package com.ai.api.agent.skill;

import com.ai.api.agent.callback.CallbackDispatcher;
import com.ai.api.agent.callback.CallbackEvent;
import com.ai.api.agent.callback.SkillCallbackContext;
import com.ai.api.agent.config.AgentProperties;
import com.ai.api.agent.entity.SkillDefinition;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.knowledge.service.RagRetrievalService;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Skill 执行器（add-agent-runtime，add-agent-runner-pipeline 集成统一回调）。
 *
 * <p>按 {@link SkillDefinition#getType()} 路由到不同的执行策略：
 *
 * <ul>
 *   <li>{@link SkillType#LOCAL}：反射调用 Bean#method
 *   <li>{@link SkillType#API}：HTTP POST 到 endpoint
 *   <li>{@link SkillType#KB}：调用 {@link RagRetrievalService}
 *   <li>{@link SkillType#SUBAGENT} / {@link SkillType#MCP}：抛 SKILL_NOT_IMPLEMENTED
 * </ul>
 *
 * <p>超时护栏（add-skill-timeout-guard）：所有 Skill 调用统一包一层 {@link CompletableFuture#orTimeout}（时长取 {@code
 * agent.react.skill-timeout}，默认 30s）。 超时不再抛异常向引擎传播，而是返回 {@code {"error":"skill timeout"}} 观察结果， 由
 * ReAct 引擎基于已有信息收尾回答。
 *
 * <p>统一回调埋点（add-agent-runner-pipeline）：调用前后触发 skill_before_invoke / skill_after_invoke /
 * skill_error 事件，由 {@link CallbackDispatcher} 广播，内部对接 TraceRecorder 自动写入 agent_trace。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillInvoker {

  /** 超时后写入 observation 的错误键。 */
  public static final String ERROR_KEY = "error";

  /** 超时错误消息（引擎据此识别超时，停止继续调工具并收尾）。 */
  public static final String TIMEOUT_ERROR = "skill timeout";

  private final ApplicationContext applicationContext;
  private final RagRetrievalService ragRetrievalService;
  private final ObjectMapper objectMapper;
  private final AgentProperties agentProperties;
  private final CallbackDispatcher callbackDispatcher;

  /**
   * 执行 Skill 调用（无 Agent 编排上下文，回调不落 trace）。
   *
   * @param def Skill 元数据
   * @param input 输入参数
   * @return 输出参数；超时返回 {@code {"error":"skill timeout"}}
   */
  public Map<String, Object> invoke(SkillDefinition def, Map<String, Object> input) {
    return invoke(def, input, null, null);
  }

  /**
   * 执行 Skill 调用（带超时护栏 + 统一回调埋点）。
   *
   * @param def Skill 元数据
   * @param input 输入参数
   * @param runtimeId 关联 runtimeId（可空）
   * @param stepIndex 当前 step 序号（可空）
   * @return 输出参数；超时返回 {@code {"error":"skill timeout"}}
   */
  public Map<String, Object> invoke(
      SkillDefinition def, Map<String, Object> input, Long runtimeId, Integer stepIndex) {
    long startTs = System.currentTimeMillis();
    dispatch(CallbackEvent.SKILL_BEFORE_INVOKE, def, input, null, 0L, null, runtimeId, stepIndex);
    try {
      Map<String, Object> result = invokeWithTimeout(def, input);
      dispatch(
          CallbackEvent.SKILL_AFTER_INVOKE,
          def,
          input,
          result,
          System.currentTimeMillis() - startTs,
          null,
          runtimeId,
          stepIndex);
      return result;
    } catch (BusinessException e) {
      dispatch(
          CallbackEvent.SKILL_ERROR,
          def,
          input,
          null,
          System.currentTimeMillis() - startTs,
          e.getMessage(),
          runtimeId,
          stepIndex);
      throw e;
    }
  }

  /** 执行 Skill 调用（带超时护栏，不含回调埋点）。 */
  private Map<String, Object> invokeWithTimeout(SkillDefinition def, Map<String, Object> input) {
    long timeoutSeconds = Math.max(1, agentProperties.getReact().getSkillTimeout());
    try {
      return CompletableFuture.supplyAsync(
              () -> {
                try {
                  return doInvoke(def, input);
                } catch (Exception ex) {
                  // 受检异常包成 CompletionException，join() 后按根因区分超时/业务异常/其他
                  throw new CompletionException(ex);
                }
              })
          .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
          .join();
    } catch (CompletionException e) {
      // supplyAsync 会把 doInvoke 抛出的任何异常包成 CompletionException，
      // 需解开根因区分：超时(TimeoutException) / 业务异常(BusinessException) / 其他
      Throwable cause = e.getCause() == null ? e : e.getCause();
      if (cause instanceof TimeoutException) {
        log.warn(
            "[SkillInvoker][invoke][skill({}#{}) type({}) 调用超时 {}s，返回 {} observation]",
            def.getName(),
            def.getVersion(),
            def.getType(),
            timeoutSeconds,
            TIMEOUT_ERROR);
        Map<String, Object> timeoutResult = new HashMap<>();
        timeoutResult.put(ERROR_KEY, TIMEOUT_ERROR);
        return timeoutResult;
      }
      if (cause instanceof BusinessException be) {
        throw be;
      }
      log.error(
          "[SkillInvoker][invoke][skill({}#{}) type({}) 调用失败]",
          def.getName(),
          def.getVersion(),
          def.getType(),
          cause);
      throw new BusinessException(
          ErrorCode.SKILL_INVOKE_FAILED,
          String.format(
              "skill(%s#%s) invoke failed: %s",
              def.getName(), def.getVersion(), cause.getMessage()));
    }
  }

  /** 派发回调事件（null runtimeId/stepIndex 由处理器自行决定是否落库）。 */
  private void dispatch(
      CallbackEvent event,
      SkillDefinition def,
      Map<String, Object> input,
      Map<String, Object> output,
      long latencyMs,
      String error,
      Long runtimeId,
      Integer stepIndex) {
    try {
      callbackDispatcher.dispatch(
          SkillCallbackContext.builder()
              .event(event)
              .runtimeId(runtimeId)
              .stepIndex(stepIndex)
              .skillName(def.getName())
              .skillVersion(def.getVersion())
              .input(input)
              .output(output)
              .latencyMs(latencyMs)
              .error(error)
              .build());
    } catch (Exception e) {
      // 回调异常不影响 Skill 主流程
      log.warn("[SkillInvoker][dispatch][event({}) 回调异常]", event, e);
    }
  }

  /** 按类型路由执行（不包超时，超时由 {@link #invoke} 统一护栏控制）。 */
  private Map<String, Object> doInvoke(SkillDefinition def, Map<String, Object> input)
      throws Exception {
    SkillType type = SkillType.valueOf(def.getType().toUpperCase());
    return switch (type) {
      case LOCAL -> invokeLocal(def, input);
      case API -> invokeApi(def, input);
      case KB -> invokeKb(def, input);
      case SUBAGENT ->
          throw new BusinessException(
              ErrorCode.SKILL_NOT_IMPLEMENTED, "subagent skill not supported in M4");
      case MCP ->
          throw new BusinessException(
              ErrorCode.SKILL_NOT_IMPLEMENTED, "mcp skill not supported in M4");
    };
  }

  /** LOCAL 类型：反射调用 Bean#method。 */
  private Map<String, Object> invokeLocal(SkillDefinition def, Map<String, Object> input)
      throws Exception {
    Object bean = applicationContext.getBean(def.getBeanName());
    Method method = bean.getClass().getDeclaredMethod(def.getMethodName(), Map.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) method.invoke(bean, input);
    return result == null ? new HashMap<>() : result;
  }

  /** API 类型：HTTP POST 到 endpoint。 */
  private Map<String, Object> invokeApi(SkillDefinition def, Map<String, Object> input) {
    if (def.getEndpoint() == null
        || def.getEndpoint().isBlank()
        || "mock".equals(def.getEndpoint())) {
      // mock 实现（如 web_search Skill），由 Bean 自身处理
      try {
        return invokeLocal(def, input);
      } catch (Exception e) {
        throw new BusinessException(
            ErrorCode.SKILL_INVOKE_FAILED, "mock api skill invoke failed: " + e.getMessage());
      }
    }
    try {
      ResponseEntity<Map> resp =
          RestClient.create()
              .post()
              .uri(def.getEndpoint())
              .body(input)
              .retrieve()
              .toEntity(Map.class);
      @SuppressWarnings("unchecked")
      Map<String, Object> body = resp.getBody();
      return body == null ? new HashMap<>() : body;
    } catch (Exception e) {
      throw new BusinessException(
          ErrorCode.SKILL_INVOKE_FAILED, "api skill 5xx: " + e.getMessage());
    }
  }

  /** KB 类型：调用 RagRetrievalService。 */
  private Map<String, Object> invokeKb(SkillDefinition def, Map<String, Object> input) {
    String query = (String) input.get("query");
    Object kbIdObj = input.get("kbId");
    Long kbId = kbIdObj instanceof Number n ? n.longValue() : Long.valueOf(kbIdObj.toString());

    int topK = input.get("topK") instanceof Number n ? n.intValue() : 5;
    Map<String, Object> result = new HashMap<>();
    result.put("chunks", ragRetrievalService.retrieveWithRrf(kbId, query, topK));
    return result;
  }
}
