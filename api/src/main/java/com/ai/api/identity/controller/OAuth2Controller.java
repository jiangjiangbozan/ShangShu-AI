package com.ai.api.identity.controller;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.result.Result;
import com.ai.api.identity.config.AuthProperties;
import com.ai.api.identity.dto.LoginResponseDTO;
import com.ai.api.identity.dto.OAuthAuthorizeResponseDTO;
import com.ai.api.identity.service.GiteeOAuth2Service;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第三方 OAuth2 登录 Controller
 *
 * <p>路径前缀 {@code /api/v1/auth/oauth}。授权跳转走 JSON 响应，回调走浏览器 302 重定向：登录成功后令牌通过 URL fragment
 * 传回前端，避免令牌落入访问日志。
 *
 * @author add-oauth-gitee
 */
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuth2Controller {

  private final GiteeOAuth2Service giteeOAuth2Service;
  private final AuthProperties authProperties;

  public OAuth2Controller(GiteeOAuth2Service giteeOAuth2Service, AuthProperties authProperties) {
    this.giteeOAuth2Service = giteeOAuth2Service;
    this.authProperties = authProperties;
  }

  /** 获取 Gitee 授权跳转地址。 */
  @GetMapping("/gitee/authorize")
  public Result<OAuthAuthorizeResponseDTO> authorize() {
    return Result.success(giteeOAuth2Service.buildAuthorizeUrl());
  }

  /** Gitee 授权回调：校验 state → 换取令牌 → 建档绑定 → 302 重定向回前端。 */
  @GetMapping("/gitee/callback")
  public void callback(
      @RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "state", required = false) String state,
      HttpServletResponse response)
      throws Exception {
    try {
      LoginResponseDTO data = giteeOAuth2Service.handleCallback(code, state);
      redirect(response, data.getAccessToken(), data.getRefreshToken(), data.getCsrfToken(), null);
    } catch (BusinessException e) {
      redirect(response, null, null, null, String.valueOf(e.getCode()));
    }
  }

  /** 302 重定向回前端。携带令牌或错误码。 */
  private void redirect(
      HttpServletResponse response,
      String accessToken,
      String refreshToken,
      String csrfToken,
      String errorCode)
      throws Exception {
    StringBuilder location =
        new StringBuilder(authProperties.getOauth().getFrontendCallbackUrl()).append("#");
    if (StringUtils.hasText(accessToken)) {
      location
          .append("accessToken=")
          .append(urlEncode(accessToken))
          .append("&refreshToken=")
          .append(urlEncode(refreshToken))
          .append("&csrfToken=")
          .append(urlEncode(csrfToken));
    } else {
      location.append("error=").append(urlEncode(errorCode == null ? "unknown" : errorCode));
    }
    response.sendRedirect(location.toString());
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
