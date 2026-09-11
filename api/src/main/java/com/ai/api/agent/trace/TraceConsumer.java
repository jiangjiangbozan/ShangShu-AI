package com.ai.api.agent.trace;

import com.ai.api.agent.config.AgentMqConfig;
import com.ai.api.agent.entity.AgentTrace;
import com.ai.api.agent.mapper.AgentTraceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Trace 异步落库消费者（add-agent-runtime）。
 *
 * <p>消费 {@code agent.trace.collect} 队列，反序列化 {@link AgentTrace} 写入 {@code agent_trace} 表。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceConsumer {

  private final AgentTraceMapper agentTraceMapper;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = AgentMqConfig.TRACE_QUEUE)
  public void onTrace(String json) {
    try {
      AgentTrace trace = objectMapper.readValue(json, AgentTrace.class);
      agentTraceMapper.insert(trace);
      log.debug(
          "[TraceConsumer][onTrace][runtimeId({}) stepIndex({}) type({}) 落库成功]",
          trace.getRuntimeId(),
          trace.getStepIndex(),
          trace.getStepType());
    } catch (Exception e) {
      log.error("[TraceConsumer][onTrace][Trace 落库失败，进入 DLQ，原始 JSON: {}]", json, e);
      throw new RuntimeException(e);
    }
  }
}
