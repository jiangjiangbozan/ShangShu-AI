package com.ai.api.chat.mq;

import com.ai.api.data.config.RabbitMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 聊天消息日志生产者
 *
 * <p>异步发送消息日志到 {@code chat.message.log} 队列，不阻塞 SSE 流式响应。
 *
 * <p>幂等设计：调用方传入 {@code mqMsgId}（UUID）作为 RabbitMQ messageId， Consumer 端通过 {@code
 * chat_message.mq_msg_id} UNIQUE 索引去重。
 *
 * <p>序列化：用 Jackson {@link ObjectMapper} 替代早期手写 {@code String.format} JSON 拼接， 避免对消息 content
 * 中的引号、换行等字符做手写转义遗漏（曾导致 Consumer 端解析失败）。
 *
 * @author add-chat-module
 */
@Slf4j
@Component
public class ChatMessageLogProducer {

  private final RabbitTemplate rabbitTemplate;

  public ChatMessageLogProducer(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * 发送消息日志（无幂等键，向后兼容）。
   *
   * <p>内部自动生成 UUID 作为 mqMsgId。
   */
  public void sendLog(
      Long sessionId,
      Long userId,
      String role,
      String content,
      String modelId,
      int totalTokens,
      int latencyMs) {
    sendLog(sessionId, userId, role, content, modelId, totalTokens, latencyMs, null, null);
  }

  /**
   * 发送消息日志（带幂等键）。
   *
   * @param sessionId 会话 ID
   * @param userId 用户 ID
   * @param role 角色（user/assistant/system）
   * @param content 消息内容
   * @param modelId 模型 ID
   * @param totalTokens Token 数
   * @param latencyMs 延迟毫秒
   * @param mqMsgId 幂等键（UUID）；为 null 时由本方法生成
   */
  public void sendLog(
      Long sessionId,
      Long userId,
      String role,
      String content,
      String modelId,
      int totalTokens,
      int latencyMs,
      String mqMsgId) {
    sendLog(sessionId, userId, role, content, modelId, totalTokens, latencyMs, mqMsgId, null);
  }

  /**
   * 发送消息日志（带幂等键 + 会话标题）。
   *
   * @param sessionId 会话 ID
   * @param userId 用户 ID
   * @param role 角色（user/assistant/system）
   * @param content 消息内容
   * @param modelId 模型 ID
   * @param totalTokens Token 数
   * @param latencyMs 延迟毫秒
   * @param mqMsgId 幂等键（UUID）；为 null 时由本方法生成
   * @param title 会话标题（可选，用于 ES 索引）
   */
  public void sendLog(
      Long sessionId,
      Long userId,
      String role,
      String content,
      String modelId,
      int totalTokens,
      int latencyMs,
      String mqMsgId,
      String title) {
    try {
      if (mqMsgId == null || mqMsgId.isBlank()) {
        mqMsgId = java.util.UUID.randomUUID().toString();
      }
      ChatMessageLogMessage payload = new ChatMessageLogMessage();
      payload.setSessionId(sessionId);
      payload.setUserId(userId);
      payload.setRole(role);
      payload.setContent(content);
      payload.setModelId(modelId);
      payload.setTotalTokens(totalTokens);
      payload.setLatencyMs(latencyMs);
      payload.setMqMsgId(mqMsgId);
      payload.setTitle(title);

      // 全局 JacksonJsonMessageConverter 自动序列化为 JSON 对象并写入 __TypeId__；
      // 幂等键通过 MessagePostProcessor 写入 messageId 与 x-mq-msg-id 头
      String finalMqMsgId = mqMsgId;
      rabbitTemplate.convertAndSend(
          RabbitMqConfig.CHAT_LOG_EXCHANGE,
          RabbitMqConfig.CHAT_LOG_ROUTING_KEY,
          payload,
          (MessagePostProcessor)
              msg -> {
                msg.getMessageProperties().setMessageId(finalMqMsgId);
                msg.getMessageProperties().setHeader("x-mq-msg-id", finalMqMsgId);
                return msg;
              });
    } catch (Exception e) {
      log.warn("[ChatMessageLogProducer][发送消息日志失败，不影响主流程: {}]", e.getMessage());
    }
  }
}
