package com.ai.api.identity.controller;

import com.ai.api.common.result.Result;
import com.ai.api.identity.config.AuthProperties;
import com.ai.api.identity.dto.ForgotPasswordRequestDTO;
import com.ai.api.identity.dto.LoginRequestDTO;
import com.ai.api.identity.dto.LoginResponseDTO;
import com.ai.api.identity.dto.RefreshRequestDTO;
import com.ai.api.identity.dto.RegisterRequestDTO;
import com.ai.api.identity.dto.ResetPasswordRequestDTO;
import com.ai.api.identity.security.AuthUserDetails;
import com.ai.api.identity.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 Controller
 *
 * <p>依据 design.md "对外暴露接口" 与 spec.md 全部 9 个 Requirement。路径前缀 {@code /api/v1/auth}。
 *
 * <p>所有接口返回 {@link Result} 统一响应格式。登录/注册成功后写 CSRF Cookie。
 *
 * @author jiangbozan
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthService authService;
  private final AuthProperties authProperties;

  public AuthController(AuthService authService, AuthProperties authProperties) {
    this.authService = authService;
    this.authProperties = authProperties;
  }

  @PostMapping("/register")
  public Result<LoginResponseDTO> register(
      @Valid @RequestBody RegisterRequestDTO request, HttpServletResponse response) {
    LoginResponseDTO data = authService.register(request);
    writeCsrfCookie(response, data.getCsrfToken());
    return Result.success(data);
  }

  @PostMapping("/login")
  public Result<LoginResponseDTO> login(
      @Valid @RequestBody LoginRequestDTO request, HttpServletResponse response) {
    LoginResponseDTO data = authService.login(request);
    writeCsrfCookie(response, data.getCsrfToken());
    return Result.success(data);
  }

  /** accessToken 过期时，用 refreshToken 换新一套令牌。 */
  @PostMapping("/refresh")
  public Result<LoginResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO request) {
    LoginResponseDTO data = authService.refresh(request.getRefreshToken());
    // refresh 接口本身已通过 CSRF 过滤器校验，响应中再次返回新 csrf-token
    return Result.success(data);
  }

  /** 退出登录，清除 accessToken、refreshToken、CSRF Cookie。 */
  @PostMapping("/logout")
  public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    String accessToken = extractBearerToken(request.getHeader("Authorization"));
    // refresh token 由请求体传递
    String refreshToken = request.getParameter("refreshToken");
    authService.logout(accessToken, refreshToken);
    // 清除CSRF Cookie
    clearCsrfCookie(response);
    return Result.success();
  }

  @PostMapping("/password/forgot")
  public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO req) {
    authService.forgotPassword(req.getEmail());
    // 防邮箱枚举：无论邮箱是否注册都返回相同成功信息
    return Result.success();
  }

  @PostMapping("/password/reset")
  public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO req) {
    authService.resetPassword(req.getToken(), req.getNewPassword());
    return Result.success();
  }

  private void clearCsrfCookie(HttpServletResponse response) {
    String cookieName = authProperties.getCsrf().getCookieName();
    String domain = authProperties.getCsrf().getCookieDomain();
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(cookieName, "")
            .path("/")
            .httpOnly(false)
            .sameSite("Lax")
            .secure(false)
            .maxAge(Duration.ZERO); // maxAge=0 浏览器立刻删除

    if (StringUtils.hasText(domain)) {
      builder.domain(domain);
    }
    response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
  }

  private void writeCsrfCookie(HttpServletResponse response, String csrfToken) {
    String cookieName = authProperties.getCsrf().getCookieName();
    Duration maxAge = authProperties.getCsrf().getCookieMaxAge();
    String domain = authProperties.getCsrf().getCookieDomain();

    // Spring ResponseCookie 封装，自动拼接全部属性，支持SameSite
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(cookieName, csrfToken)
            .path("/")
            .httpOnly(false) // CSRF Cookie JS要读，必须false！
            .secure(false)
            .sameSite("Lax")
            .maxAge(maxAge);

    if (StringUtils.hasText(domain)) {
      builder.domain(domain);
    }
    String setCookieValue = builder.build().toString();
    // 注意addHeader，不是setHeader，避免覆盖别的Cookie
    response.addHeader(HttpHeaders.SET_COOKIE, setCookieValue);
  }

  private String extractBearerToken(String authHeader) {
    if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
      return authHeader.substring(BEARER_PREFIX.length());
    }
    return null;
  }

  /** 用于获取当前登录用户信息（同 UserController.users/me，但放在 AuthController 便于管理）。 */
  public AuthUserDetails getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthUserDetails details)) {
      return null;
    }
    return details;
  }
}
