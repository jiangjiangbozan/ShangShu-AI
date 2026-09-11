package com.ai.api.chat.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 消息响应 DTO
 *
 * @author add-chat-module
 */
@Data
@Builder
public class MessageRespDTO {

  private Long messageId;
  private Long sessionId;
  private String role;
  private String content;
  private Integer promptTokens;
  private Integer completionTokens;
  private Integer totalTokens;
  private Integer latencyMs;
  private String modelId;
  private LocalDateTime createTime;
}
