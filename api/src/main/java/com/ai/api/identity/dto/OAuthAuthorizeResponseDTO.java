package com.ai.api.identity.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 第三方 OAuth 授权跳转响应 DTO
 *
 * <p>前端拿到 {@code authorizeUrl} 后直接 {@code window.location.href} 跳转第三方授权页。
 *
 * @author add-oauth-gitee
 */
@Data
@Builder
public class OAuthAuthorizeResponseDTO {

  /** 平台标识，如 gitee */
  private String provider;

  /** 第三方授权页完整地址（含 client_id / redirect_uri / response_type / state） */
  private String authorizeUrl;
}
