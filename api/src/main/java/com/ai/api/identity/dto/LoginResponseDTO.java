package com.ai.api.identity.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录/注册/刷新响应 DTO
 *
 * <p>携带 access token、refresh token、过期时间（秒）与基本用户信息。
 *
 * @author add-user-auth
 */
@Data
@Builder
public class LoginResponseDTO {

  private Long userId;
  private String username;
  private String email;
  private String accessToken;
  private String refreshToken;
  private Long expiresIn;
  private String csrfToken;
}
