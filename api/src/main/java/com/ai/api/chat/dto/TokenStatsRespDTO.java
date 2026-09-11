package com.ai.api.chat.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Token 用量统计响应 DTO
 *
 * @author add-chat-module
 */
@Data
@Builder
public class TokenStatsRespDTO {

  private String period;
  private long promptTokens;
  private long completionTokens;
  private long totalTokens;
}
