package com.ai.api.document.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文档关联问答记录响应 DTO（add-p3-rag-feedback-ops）。
 *
 * <p>展示该文档被哪些会话命中引用，辅助内容运营与调优。
 *
 * @author add-p3-rag-feedback-ops
 */
@Data
public class RelatedChatRespDTO {

  /** 会话 ID */
  private Long sessionId;

  /** 会话标题 */
  private String sessionTitle;

  /** 命中时间（消息创建时间） */
  private LocalDateTime hitTime;

  /** 用户提问片段（同会话命中前的最近一条 user 消息） */
  private String querySnippet;

  /** 命中的 chunk 内容片段 */
  private String chunkContent;

  /** 命中消息 ID（ES 中以 keyword 字符串存储） */
  private String messageId;

  /** 命中消息角色（user/assistant） */
  private String role;
}
