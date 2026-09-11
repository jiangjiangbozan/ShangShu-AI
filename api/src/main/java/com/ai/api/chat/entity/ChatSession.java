package com.ai.api.chat.entity;

import com.ai.api.data.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话会话实体
 *
 * <p>对应 {@code chat_session} 表，继承 {@link BaseDO} 复用审计字段与逻辑删除。
 *
 * @author add-chat-module
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_session")
public class ChatSession extends BaseDO {

  /** 用户 ID */
  private Long userId;

  /** 会话标题 */
  private String title;

  /** 使用的模型 ID */
  private String modelId;

  /** 上下文窗口大小（轮次） */
  private Integer contextSize;

  /** 消息总数 */
  private Integer messageCount;

  /** 累计 Token 用量 */
  private Long totalTokens;
}
