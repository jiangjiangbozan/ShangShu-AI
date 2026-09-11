package com.ai.api.model.config.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 模型连接测试响应 DTO
 *
 * @author add-chat-module
 */
@Data
@Builder
public class ModelTestRespDTO {

  /** 是否连接成功 */
  private boolean success;

  /** 响应延迟（毫秒） */
  private long latencyMs;

  /** 提示信息 */
  private String message;
}
