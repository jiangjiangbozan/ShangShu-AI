package com.ai.api.agent.runtime;

import com.ai.api.agent.config.AgentMqConfig;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Agent 状态变更通知器（add-agent-runtime）。
 *
 * <p>向 {@code agent.state.notify}（fanout exchange）发送状态变更事件。前端 SSE 网关订阅此 exchange 后推送前端。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentStateNotifier {

  private final RabbitTemplate rabbitTemplate;

  /**
   * 异步发送状态变更通知。
   *
   * @param runtimeId runtime ID
   * @param oldStatus 原状态
   * @param newStatus 新状态
   */
  @Async
  public void notify(Long runtimeId, String oldStatus, String newStatus) {
    try {
      Map<String, Object> event = new HashMap<>();
      event.put("runtimeId", runtimeId);
      event.put("oldStatus", oldStatus);
      event.put("newStatus", newStatus);
      event.put("timestamp", System.currentTimeMillis());
      // 由全局 JacksonJsonMessageConverter 负责 JSON 序列化
      rabbitTemplate.convertAndSend(AgentMqConfig.STATE_EXCHANGE, "", event);
      log.debug(
          "[AgentStateNotifier][notify][runtimeId({}) {} → {}]", runtimeId, oldStatus, newStatus);
    } catch (Exception e) {
      log.warn("[AgentStateNotifier][notify][runtimeId({}) 发送失败]", runtimeId, e);
    }
  }
}
