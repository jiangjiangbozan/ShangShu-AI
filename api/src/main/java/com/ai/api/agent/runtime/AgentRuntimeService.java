package com.ai.api.agent.runtime;

import com.ai.api.agent.config.AgentMqConfig;
import com.ai.api.agent.config.AgentProperties;
import com.ai.api.agent.entity.AgentRuntime;
import com.ai.api.agent.mapper.AgentRuntimeMapper;
import com.ai.api.agent.pause.PauseState;
import com.ai.api.agent.pause.PauseStateService;
import com.ai.api.agent.pipeline.ContextLayer;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Agent Runtime 核心服务（add-agent-runtime，add-agent-runner-pipeline 适配）。
 *
 * <p>实现状态机 + 异步任务派发 + 断点续跑。所有状态转移 MUST 通过 {@link #transition(Long, AgentStatus, AgentStatus)}
 * 方法校验合法性。
 *
 * <p>暂停机制（add-agent-runner-pipeline）：优先使用轻量 {@link PauseState} 作为暂停点存储（NORMAL /
 * ASK_HUMAN），AgentContextManager 全量快照降级为异常崩溃的完整恢复兜底。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeService {

  private final AgentRuntimeMapper agentRuntimeMapper;
  private final RabbitTemplate rabbitTemplate;
  private final AgentProperties agentProperties;
  private final PauseStateService pauseStateService;
  private final AgentContextManager agentContextManager;

  /**
   * 创建 runtime。
   *
   * @param sessionId 关联会话 ID
   * @param agentConfigId Agent 配置 ID（可空）
   * @param modelId LLM modelId（可空，回退默认）
   * @param userId 用户 ID
   * @param strategy 执行策略（COT / FC，可空默认 COT）
   * @return runtimeId
   */
  public Long createRuntime(
      Long sessionId, Long agentConfigId, String modelId, Long userId, String strategy) {
    AgentRuntime runtime = new AgentRuntime();
    runtime.setSessionId(sessionId);
    runtime.setAgentConfigId(agentConfigId);
    runtime.setModelId(modelId);
    runtime.setUserId(userId);
    runtime.setStatus(AgentStatus.PENDING.name().toLowerCase());
    runtime.setStrategy(
        strategy == null || strategy.isBlank() ? "COT" : strategy.trim().toUpperCase());
    runtime.setNextStepIndex(0);
    runtime.setLastHeartbeat(LocalDateTime.now());
    runtime.setTtlSeconds(agentProperties.getHeartbeatTtl());
    runtime.setCreateTime(LocalDateTime.now());
    runtime.setUpdateTime(LocalDateTime.now());
    agentRuntimeMapper.insert(runtime);
    log.info(
        "[AgentRuntimeService][createRuntime][runtimeId({}) sessionId({}) modelId({}) strategy({})]",
        runtime.getId(),
        sessionId,
        modelId,
        runtime.getStrategy());
    return runtime.getId();
  }

  /** 启动 runtime：状态 pending → running，向 agent.task.dispatch 派发消息。 */
  public void start(Long runtimeId) {
    AgentRuntime runtime = loadRuntime(runtimeId);
    AgentStatus current = AgentStatus.valueOf(runtime.getStatus().toUpperCase());
    transition(runtimeId, current, AgentStatus.RUNNING);

    Map<String, Object> msg = new HashMap<>();
    msg.put("runtimeId", runtimeId);
    msg.put("sessionId", runtime.getSessionId());
    sendTask(msg);
    log.info("[AgentRuntimeService][start][runtimeId({}) 派发异步任务]", runtimeId);
  }

  /**
   * 暂停 runtime：状态 running → paused，并落轻量 PauseState（NORMAL）作为暂停点。
   *
   * <p>暂停点优先使用 PauseState 存储（nextStepIndex），全量快照仅作为异常崩溃恢复兜底。
   */
  public void pause(Long runtimeId) {
    AgentRuntime runtime = loadRuntime(runtimeId);
    AgentStatus current = AgentStatus.valueOf(runtime.getStatus().toUpperCase());
    transition(runtimeId, current, AgentStatus.PAUSED);
    if (!pauseStateService.isPaused(runtimeId)) {
      pauseStateService.create(
          runtimeId, runtime.getNextStepIndex(), PauseState.TYPE_NORMAL, null, null);
    }
    log.info("[AgentRuntimeService][pause][runtimeId({}) 落 PauseState 暂停点]", runtimeId);
  }

  /** 恢复 runtime：状态 paused → running，清除暂停点，重新派发异步任务。 */
  public void resume(Long runtimeId) {
    AgentRuntime runtime = loadRuntime(runtimeId);
    AgentStatus current = AgentStatus.valueOf(runtime.getStatus().toUpperCase());
    transition(runtimeId, current, AgentStatus.RUNNING);
    pauseStateService.cancel(runtimeId);

    Map<String, Object> msg = new HashMap<>();
    msg.put("runtimeId", runtimeId);
    msg.put("sessionId", runtime.getSessionId());
    msg.put("resume", true);
    sendTask(msg);
    log.info("[AgentRuntimeService][resume][runtimeId({}) 重新派发任务]", runtimeId);
  }

  /**
   * 人工介入（ask-human）：生成 ASK_HUMAN 类型 PauseState 挂起执行。
   *
   * @param runtimeId runtime ID
   * @param question 向人类确认的问题
   * @param variables 待确认变量
   * @param nextStepIndex 下一步执行序号（resume 后从该步继续）
   * @return 新建的 PauseState
   */
  public PauseState askHuman(
      Long runtimeId, String question, Map<String, Object> variables, Integer nextStepIndex) {
    AgentRuntime runtime = loadRuntime(runtimeId);
    AgentStatus current = AgentStatus.valueOf(runtime.getStatus().toUpperCase());
    transition(runtimeId, current, AgentStatus.PAUSED);
    PauseState pause =
        pauseStateService.create(
            runtimeId, nextStepIndex, PauseState.TYPE_ASK_HUMAN, question, variables);
    log.info(
        "[AgentRuntimeService][askHuman][runtimeId({}) question({}) nextStepIndex({})]",
        runtimeId,
        question,
        nextStepIndex);
    return pause;
  }

  /**
   * 人工回复：解析 PauseState → 用户输入回填上下文 → 从 nextStepIndex 继续执行。
   *
   * @param runtimeId runtime ID
   * @param userInput 人工回复内容
   */
  public void respond(Long runtimeId, String userInput) {
    PauseState pause = pauseStateService.findActive(runtimeId);
    if (pause == null) {
      throw new BusinessException(ErrorCode.AGENT_PAUSE_NOT_FOUND, "no active pause: " + runtimeId);
    }
    pauseStateService.resolve(runtimeId, userInput);

    // 回填上下文：人工回复注入 observations，runner 从 nextStepIndex 继续
    AgentContext context = agentContextManager.load(runtimeId);
    Map<String, Object> humanResponse = new HashMap<>();
    humanResponse.put("question", pause.getQuestion());
    humanResponse.put("reply", userInput);
    context.getObservations().put(ContextLayer.HUMAN_RESPONSE_KEY, humanResponse);
    context.setNextStepIndex(pause.getNextStepIndex());
    agentContextManager.snapshot(runtimeId, context);
    log.info(
        "[AgentRuntimeService][respond][runtimeId({}) nextStepIndex({}) 回填人工回复]",
        runtimeId,
        pause.getNextStepIndex());

    // 状态机 paused → running，重新派发
    AgentRuntime runtime = loadRuntime(runtimeId);
    AgentStatus current = AgentStatus.valueOf(runtime.getStatus().toUpperCase());
    transition(runtimeId, current, AgentStatus.RUNNING);
    Map<String, Object> msg = new HashMap<>();
    msg.put("runtimeId", runtimeId);
    msg.put("sessionId", runtime.getSessionId());
    msg.put("resume", true);
    sendTask(msg);
  }

  /** 更新心跳时间。暂停状态不刷新心跳（识别暂停，避免暂停期间刷新心跳掩盖真实状态）。 */
  public void heartbeat(Long runtimeId) {
    AgentRuntime runtime = agentRuntimeMapper.selectById(runtimeId);
    if (runtime == null) {
      return;
    }
    if (AgentStatus.valueOf(runtime.getStatus().toUpperCase()) == AgentStatus.PAUSED) {
      return;
    }
    agentRuntimeMapper.touchHeartbeat(runtimeId);
  }

  /** 取消 runtime：状态 running|paused → cancelled。 */
  public void cancel(Long runtimeId) {
    AgentRuntime runtime = loadRuntime(runtimeId);
    AgentStatus current = AgentStatus.valueOf(runtime.getStatus().toUpperCase());
    transition(runtimeId, current, AgentStatus.CANCELLED);
    log.info("[AgentRuntimeService][cancel][runtimeId({})]", runtimeId);
  }

  /** 标记完成 + 最终答案。 */
  public void done(Long runtimeId, String finalAnswer) {
    AgentRuntime runtime = loadRuntime(runtimeId);
    AgentStatus current = AgentStatus.valueOf(runtime.getStatus().toUpperCase());
    transition(runtimeId, current, AgentStatus.DONE);
    agentRuntimeMapper.markDone(runtimeId, finalAnswer);
    log.info(
        "[AgentRuntimeService][done][runtimeId({}) finalAnswer({})]",
        runtimeId,
        finalAnswer == null ? null : finalAnswer.substring(0, Math.min(50, finalAnswer.length())));
  }

  /** 标记失败 + 错误原因 + 关联死信消息 ID。 */
  public void fail(Long runtimeId, String errorMessage, String dlqMessageId) {
    AgentRuntime runtime = loadRuntime(runtimeId);
    AgentStatus current = AgentStatus.valueOf(runtime.getStatus().toUpperCase());
    transition(runtimeId, current, AgentStatus.FAILED);
    String fullError =
        dlqMessageId == null ? errorMessage : errorMessage + " [dlq=" + dlqMessageId + "]";
    agentRuntimeMapper.markFailed(runtimeId, fullError);
    log.warn("[AgentRuntimeService][fail][runtimeId({}) error({})]", runtimeId, fullError);
  }

  /** 扫描超时 running 任务转 failed（供 HeartbeatScanner 调用）。 */
  public List<AgentRuntime> scanOverdueRuntimes() {
    LocalDateTime cutoff = LocalDateTime.now().minusSeconds(agentProperties.getHeartbeatTtl());
    List<AgentRuntime> overdue = agentRuntimeMapper.selectOverdueRunning(cutoff);
    for (AgentRuntime runtime : overdue) {
      agentRuntimeMapper.markFailed(runtime.getId(), "heartbeat timeout");
      log.warn(
          "[AgentRuntimeService][scanOverdue][runtimeId({}) lastHeartbeat({}) 转 failed]",
          runtime.getId(),
          runtime.getLastHeartbeat());
    }
    return overdue;
  }

  /** 加载 runtime，不存在抛 AGENT_NOT_FOUND。 */
  public AgentRuntime loadRuntime(Long runtimeId) {
    return Optional.ofNullable(agentRuntimeMapper.selectById(runtimeId))
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.AGENT_NOT_FOUND, "runtime not found: " + runtimeId));
  }

  /**
   * 判断 runtime 是否已处于终态（DONE/FAILED/CANCELLED）。
   *
   * <p>用于消费幂等跳过（add-mq-at-least-once）：at-least-once 语义下消息可能重投，终态任务无需重复执行； runtime
   * 不存在视为终态（陈旧消息直接跳过，不触发重试）。
   */
  public boolean isTerminal(Long runtimeId) {
    AgentRuntime runtime = agentRuntimeMapper.selectById(runtimeId);
    if (runtime == null) {
      return true;
    }
    AgentStatus status = AgentStatus.valueOf(runtime.getStatus().toUpperCase());
    return status == AgentStatus.DONE
        || status == AgentStatus.FAILED
        || status == AgentStatus.CANCELLED;
  }

  /** 任务消息入队（由全局 JacksonJsonMessageConverter 负责 JSON 序列化）。 */
  private void sendTask(Map<String, Object> msg) {
    rabbitTemplate.convertAndSend(AgentMqConfig.TASK_EXCHANGE, AgentMqConfig.TASK_ROUTING_KEY, msg);
  }

  /**
   * 校验状态转移合法性，非法抛 AGENT_INVALID_STATE_TRANSITION。
   *
   * @param runtimeId runtime ID
   * @param from 当前状态
   * @param to 目标状态
   */
  private void transition(Long runtimeId, AgentStatus from, AgentStatus to) {
    if (!from.canTransitionTo(to)) {
      throw new BusinessException(
          ErrorCode.AGENT_INVALID_STATE_TRANSITION, "非法状态转移: " + from + " → " + to);
    }
    agentRuntimeMapper.updateStatus(runtimeId, to.name().toLowerCase());
  }
}
