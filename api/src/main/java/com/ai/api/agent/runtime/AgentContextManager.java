package com.ai.api.agent.runtime;

import com.ai.api.agent.entity.AgentRuntime;
import com.ai.api.agent.mapper.AgentRuntimeMapper;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 上下文管理器（add-agent-runtime）。
 *
 * <p>负责 AgentContext 的序列化、加载、快照持久化。采用 Redis + MySQL 双写：
 *
 * <ul>
 *   <li>Redis Key {@code agent:ctx:{runtimeId}}（TTL 24h）：热缓存，高频读
 *   <li>MySQL {@code agent_runtime.context_snapshot}：冷存储，断点续跑恢复
 * </ul>
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentContextManager {

  private static final String REDIS_KEY_PREFIX = "agent:ctx:";
  private static final Duration REDIS_TTL = Duration.ofHours(24);

  private final AgentRuntimeMapper agentRuntimeMapper;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  /**
   * 加载上下文。同时读取 Redis 与 MySQL 快照，按 generation 取大者（自愈回填落后一侧）。
   *
   * @param runtimeId runtime ID
   * @return AgentContext，runtime 不存在抛 AGENT_NOT_FOUND
   */
  public AgentContext load(Long runtimeId) {
    AgentRuntime runtime =
        Optional.ofNullable(agentRuntimeMapper.selectById(runtimeId))
            .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));

    // 1. 同时读取 Redis（热缓存）与 MySQL（冷存储）快照
    String redisJson = readRedis(runtimeId);
    String mysqlJson = runtime.getContextSnapshot();

    // 2. 按 generation 取大者：写序无关，哪边新用哪边，天然覆盖"崩溃窗口"的任意单边写
    String winner = pickNewest(redisJson, mysqlJson);
    if (winner == null || winner.isBlank()) {
      // 新 runtime，返回空 context
      AgentContext ctx = new AgentContext();
      ctx.setRuntimeId(runtimeId);
      ctx.setNextStepIndex(runtime.getNextStepIndex());
      return ctx;
    }

    AgentContext ctx;
    try {
      ctx = objectMapper.readValue(winner, AgentContext.class);
      ctx.setRuntimeId(runtimeId);
    } catch (JacksonException e) {
      log.error("[AgentContextManager][load][反序列化失败 runtimeId({})]", runtimeId, e);
      throw new BusinessException(ErrorCode.AGENT_CONTEXT_CORRUPTED);
    }

    // 3. 自愈回填：winner 为真源，落后一侧用 winner 覆盖（失败仅告警，不影响主流程）
    backfillStaleSide(redisJson, mysqlJson, winner, runtimeId, ctx);
    return ctx;
  }

  /**
   * 持久化上下文快照（Redis + MySQL 双写，失败对称降级）。
   *
   * @param runtimeId runtime ID
   * @param context 当前上下文
   */
  public void snapshot(Long runtimeId, AgentContext context) {
    context.setRuntimeId(runtimeId);
    // generation 单调递增：单执行器由 Redisson 锁保护，内存自增即可保证单调；
    // 双写两侧携带同一代次，load 时取大者自愈（无需依赖 Redis INCR 之类的全局计数器）
    context.setGeneration(context.getGeneration() + 1);
    String json;
    try {
      json = objectMapper.writeValueAsString(context);
    } catch (JacksonException e) {
      log.error("[AgentContextManager][snapshot][序列化失败 runtimeId({})]", runtimeId, e);
      throw new BusinessException(ErrorCode.AGENT_CONTEXT_CORRUPTED);
    }

    // 1. 写 Redis（同步，热缓存）；失败降级仅 MySQL
    try {
      redisTemplate.opsForValue().set(redisKey(runtimeId), json, REDIS_TTL);
    } catch (Exception e) {
      log.warn("[AgentContextManager][snapshot][Redis 写失败 runtimeId({}) 降级仅 MySQL]", runtimeId, e);
    }

    // 2. 写 MySQL（context_snapshot + next_step_index + last_heartbeat）；失败降级仅 Redis
    try {
      agentRuntimeMapper.updateContextSnapshot(runtimeId, json, context.getNextStepIndex());
      agentRuntimeMapper.touchHeartbeat(runtimeId);
    } catch (Exception e) {
      log.warn("[AgentContextManager][snapshot][MySQL 写失败 runtimeId({}) 降级仅 Redis]", runtimeId, e);
    }
  }

  /** 读 Redis 快照，失败降级直读 MySQL（返回 null）。 */
  private String readRedis(Long runtimeId) {
    try {
      return redisTemplate.opsForValue().get(redisKey(runtimeId));
    } catch (Exception e) {
      log.warn("[AgentContextManager][load][读 Redis 失败 runtimeId({}) 直读 MySQL]", runtimeId, e);
      return null;
    }
  }

  /** 按 generation 取大者；无法解析视为损坏（-1）优先级最低；相等时优先 Redis。 */
  private String pickNewest(String redisJson, String mysqlJson) {
    if (redisJson == null || redisJson.isBlank()) {
      return mysqlJson;
    }
    if (mysqlJson == null || mysqlJson.isBlank()) {
      return redisJson;
    }
    long redisGen = parseGeneration(redisJson);
    long mysqlGen = parseGeneration(mysqlJson);
    return redisGen >= mysqlGen ? redisJson : mysqlJson;
  }

  /** 解析快照代次；JSON 损坏时返回 -1（视为最旧，让另一侧胜出并自愈覆盖它）。 */
  private long parseGeneration(String json) {
    try {
      return objectMapper.readValue(json, AgentContext.class).getGeneration();
    } catch (JacksonException e) {
      return -1;
    }
  }

  /** 自愈回填：落后一侧用 winner 覆盖，让双写收敛到同一代次。 */
  private void backfillStaleSide(
      String redisJson, String mysqlJson, String winner, Long runtimeId, AgentContext ctx) {
    if (winner.equals(redisJson)) {
      // Redis 赢且 MySQL 落后 → 回填 MySQL
      if (!winner.equals(mysqlJson)) {
        try {
          agentRuntimeMapper.updateContextSnapshot(runtimeId, winner, ctx.getNextStepIndex());
        } catch (Exception e) {
          log.warn("[AgentContextManager][load][回填 MySQL 失败 runtimeId({})]", runtimeId, e);
        }
      }
    } else {
      // MySQL 赢且 Redis 落后/缺失 → 回填 Redis
      try {
        redisTemplate.opsForValue().set(redisKey(runtimeId), winner, REDIS_TTL);
      } catch (Exception e) {
        log.warn("[AgentContextManager][load][回填 Redis 失败 runtimeId({})]", runtimeId, e);
      }
    }
  }

  private String redisKey(Long runtimeId) {
    return REDIS_KEY_PREFIX + runtimeId;
  }
}
