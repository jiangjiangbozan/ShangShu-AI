package com.ai.api.model.config.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 模型配置响应 DTO
 *
 * <p>API Key 脱敏返回（仅显示前 4 位 + **** + 后 4 位）。
 *
 * @author add-chat-module
 */
@Data
@Builder
public class ModelConfigRespDTO {

  private Long id;
  private String provider;
  private String modelId;
  private String displayName;

  /** 脱敏后的 API Key */
  private String apiKeyMasked;

  private String baseUrl;
  private Integer enabled;
  private String parameters;
  private Integer sortOrder;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
