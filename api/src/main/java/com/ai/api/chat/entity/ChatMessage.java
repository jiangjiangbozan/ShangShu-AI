package com.ai.api.chat.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话消息实体
 *
 * <p>对应 {@code chat_message} 表，继承 {@link BaseDO} 复用审计字段与逻辑删除。
 *
 * @author add-chat-module
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_message")
public class ChatMessage extends BaseDO {

  /** 会话 ID */
  private Long sessionId;

  /** 角色：user / assistant / system */
  private String role;

  /** 消息内容 */
  private String content;

  /** 提示词 Token 数 */
  private Integer promptTokens;

  /** 生成 Token 数 */
  private Integer completionTokens;

  /** 总 Token 数 */
  private Integer totalTokens;

  /** 响应延迟（毫秒） */
  private Integer latencyMs;

  /** 使用的模型 ID */
  private String modelId;

  /**
   * RabbitMQ 消息 ID（幂等键）。
   *
   * <p>由 Producer 在发送时生成（UUID），Consumer 写入前用此字段配合 UNIQUE 索引实现去重： 同一 mq_msg_id 重复消费时 INSERT
   * 会因索引冲突失败，应用层捕获 DuplicateKeyException 即可短路返回 ack。 非 MQ 来源（如本地直存）的消息该字段为 NULL，UNIQUE 索引允许多个 NULL
   * 不冲突。
   */
  private String mqMsgId;
}
