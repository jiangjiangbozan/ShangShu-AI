package com.ai.api.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Refresh Token 请求 DTO
 *
 * @author add-user-auth
 */
@Data
public class RefreshRequestDTO {

  @NotBlank(message = "refreshToken 不能为空")
  private String refreshToken;
}
