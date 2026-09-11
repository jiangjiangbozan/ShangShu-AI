package com.ai.api.chat.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 会话响应 DTO
 *
 * @author add-chat-module
 */
@Data
@Builder
public class SessionRespDTO {

  private Long sessionId;
  private String title;
  private String modelId;
  private Integer messageCount;
  private Long totalTokens;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
