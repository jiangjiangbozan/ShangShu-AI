package com.ai.api.chat.mq;

import lombok.Data;

/**
 * 聊天消息日志 MQ 消息体（add-chat-module）。
 *
 * <p>定义 {@code chat.message.log} 队列消息载荷字段，与 {@link ChatMessageLogProducer} 发送、{@link
 * ChatMessageLogConsumer} 消费的 JSON 结构一一对应。
 *
 * <p>由全局 {@code JacksonJsonMessageConverter} 负责 JSON 序列化/反序列化（消息头 {@code __TypeId__} 指向本类）。此前
 * Producer 手动将 Map 序列化为 JSON 对象字节后直接 {@code send}，Consumer 却以 {@code String} 入参接收，导致反序列化时报 {@code
 * Cannot deserialize value of type java.lang.String from Object value (token START_OBJECT)}。
 *
 * @author add-chat-module
 */
@Data
public class ChatMessageLogMessage {

  /** 会话 ID */
  private Long sessionId;

  /** 用户 ID（仅用于 ES 索引透传，不落 chat_message 表） */
  private Long userId;

  /** 角色：user / assistant / system */
  private String role;

  /** 消息内容 */
  private String content;

  /** 使用的模型 ID */
  private String modelId;

  /** 会话标题（用于 ES 索引，便于搜索匹配会话标题关键词） */
  private String title;

  /** 总 Token 数 */
  private Integer totalTokens;

  /** 响应延迟（毫秒） */
  private Integer latencyMs;

  /** RabbitMQ 消息 ID（幂等键） */
  private String mqMsgId;
}
