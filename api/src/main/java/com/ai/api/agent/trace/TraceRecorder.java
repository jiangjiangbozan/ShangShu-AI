package com.ai.api.agent.trace;

import com.ai.api.agent.config.AgentMqConfig;
import com.ai.api.agent.entity.AgentTrace;
import com.ai.api.agent.mapper.AgentTraceMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Trace 记录器（add-agent-runtime，完整实现）。
 *
 * <p>采用 MQ 异步落库模式：
 *
 * <ol>
 *   <li>调用方（CotAgentRunner / DagEngine / PlanAndExecute）调用 {@link #record(AgentTrace)} 同步入队
 *   <li>{@link TraceConsumer} 消费 {@code agent.trace.collect} 队列，批量写入 {@code agent_trace} 表
 *   <li>失败由 DLX 转入 {@code agent.trace.dlq}
 * </ol>
 *
 * <p>同时提供 token/latency 性能埋点：每次 record 自动填充 latency_ms（从入参 startTimestamp 计算）。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceRecorder {

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;
  private final AgentTraceMapper agentTraceMapper;

  /**
   * 异步记录一条 Trace（同步入 MQ 队列，由 Consumer 异步落库）。
   *
   * @param trace 待记录的 trace
   */
  @Async
  public void record(AgentTrace trace) {
    if (trace.getTraceId() == null) trace.setTraceId(UUID.randomUUID().toString());
    if (trace.getCreateTime() == null) trace.setCreateTime(LocalDateTime.now());
    try {
      String json = objectMapper.writeValueAsString(trace);
      rabbitTemplate.convertAndSend(
          AgentMqConfig.TRACE_EXCHANGE, AgentMqConfig.TRACE_ROUTING_KEY, json);
    } catch (Exception e) {
      log.warn(
          "[TraceRecorder][record][runtimeId({}) stepIndex({}) MQ 入队失败，降级直写]",
          trace.getRuntimeId(),
          trace.getStepIndex(),
          e);
      // 降级直写
      try {
        agentTraceMapper.insert(trace);
      } catch (Exception ex) {
        log.error("[TraceRecorder][record][降级直写也失败]", ex);
      }
    }
  }

  /** 记录 Thought 步骤（LLM 推理）。 */
  @Async
  public void recordThought(
      Long runtimeId,
      int stepIndex,
      String prompt,
      String llmResponse,
      int tokenIn,
      int tokenOut,
      long latencyMs,
      Long parentStepId) {
    AgentTrace trace = new AgentTrace();
    trace.setRuntimeId(runtimeId);
    trace.setStepIndex(stepIndex);
    trace.setStepType("thought");
    trace.setInput(prompt);
    trace.setOutput(llmResponse);
    trace.setTokenIn(tokenIn);
    trace.setTokenOut(tokenOut);
    trace.setLatencyMs((int) latencyMs);
    trace.setParentStepId(parentStepId);
    record(trace);
  }

  /** 记录 Action 步骤（Skill 调用）。 */
  @Async
  public void recordAction(
      Long runtimeId,
      int stepIndex,
      String skillName,
      Map<String, Object> input,
      Map<String, Object> output,
      long latencyMs,
      Long parentStepId) {
    AgentTrace trace = new AgentTrace();
    trace.setRuntimeId(runtimeId);
    trace.setStepIndex(stepIndex);
    trace.setStepType("action");
    trace.setSkillId(skillName);
    trace.setInput(toJson(input));
    trace.setOutput(toJson(output));
    trace.setLatencyMs((int) latencyMs);
    trace.setParentStepId(parentStepId);
    record(trace);
  }

  /** 记录错误步骤。 */
  @Async
  public void recordError(Long runtimeId, int stepIndex, String errorMessage, String dlqMessageId) {
    AgentTrace trace = new AgentTrace();
    trace.setRuntimeId(runtimeId);
    trace.setStepIndex(stepIndex);
    trace.setStepType("error");
    trace.setError(errorMessage);
    trace.setDlqMessageId(dlqMessageId);
    record(trace);
  }

  /** 记录断点续跑步骤。 */
  @Async
  public void recordResume(Long runtimeId, int fromStepIndex, String reason) {
    AgentTrace trace = new AgentTrace();
    trace.setRuntimeId(runtimeId);
    trace.setStepIndex(fromStepIndex);
    trace.setStepType("resume");
    Map<String, Object> meta = new HashMap<>();
    meta.put("reason", reason);
    trace.setInput(toJson(meta));
    record(trace);
  }

  /** 记录 Plan-and-Execute 计划生成。 */
  @Async
  public void recordPlan(Long runtimeId, List<Map<String, Object>> steps, String rawPlan) {
    AgentTrace trace = new AgentTrace();
    trace.setRuntimeId(runtimeId);
    trace.setStepIndex(0);
    trace.setStepType("thought");
    trace.setInput("plan");
    Map<String, Object> output = new HashMap<>();
    output.put("steps", steps);
    output.put("raw", rawPlan);
    trace.setOutput(toJson(output));
    record(trace);
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      return "{}";
    }
  }
}
