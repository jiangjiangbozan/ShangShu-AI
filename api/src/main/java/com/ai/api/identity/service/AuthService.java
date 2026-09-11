package com.ai.api.identity.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.identity.config.AuthProperties;
import com.ai.api.identity.dto.LoginRequestDTO;
import com.ai.api.identity.dto.LoginResponseDTO;
import com.ai.api.identity.dto.RegisterRequestDTO;
import com.ai.api.identity.entity.SystemUser;
import io.jsonwebtoken.Claims;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务
 *
 * <p>依据 spec.md 全部 9 个 Requirement，编排 {@link UserService}、{@link TokenService}、 {@link
 * LoginFailLimiter}、{@link MailService} 完成业务闭环。
 *
 * @author add-user-auth
 */
@Slf4j
@Service
public class AuthService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final String KEY_RESET_TOKEN = "auth:reset:";

  private final UserService userService;
  private final TokenService tokenService;
  private final LoginFailLimiter loginFailLimiter;
  private final MailService mailService;
  private final AuthProperties authProperties;
  private final StringRedisTemplate redisTemplate;

  public AuthService(
      UserService userService,
      TokenService tokenService,
      LoginFailLimiter loginFailLimiter,
      MailService mailService,
      AuthProperties authProperties,
      StringRedisTemplate redisTemplate) {
    this.userService = userService;
    this.tokenService = tokenService;
    this.loginFailLimiter = loginFailLimiter;
    this.mailService = mailService;
    this.authProperties = authProperties;
    this.redisTemplate = redisTemplate;
  }

  /**
   * 注册：校验唯一性 → 哈希密码 → 签发 Token 对。
   *
   * @param request 注册请求
   * @return 登录响应
   */
  @Transactional(rollbackFor = Exception.class)
  public LoginResponseDTO register(RegisterRequestDTO request) {
    SystemUser user =
        userService.register(request.getUsername(), request.getEmail(), request.getPassword());
    return issueTokenPair(user, generateCsrfToken());
  }

  /**
   * 登录：查询用户 → 校验密码 → 失败计数/锁 定 → 签发 Token 对。
   *
   * @param req 登录请求
   * @return 登录响应
   */
  public LoginResponseDTO login(LoginRequestDTO req) {
    // 先检查 Redis 层失败计数是否已达阈值（兜底加速）
    if (loginFailLimiter.isExceeded(req.getAccount())) {
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
    }

    SystemUser user = userService.getByAccount(req.getAccount());
    // 用户不存在：仍记录一次失败（防止账号枚举）
    if (user == null) {
      loginFailLimiter.recordFailure(req.getAccount());
      throw new BusinessException(ErrorCode.AUTH_BAD_CREDENTIALS);
    }

    // 账户禁用
    if (userService.isDisabled(user)) {
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
    }

    // 账户锁定（基于 DB 字段）
    if (userService.isLocked(user)) {
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
    }

    // 密码校验
    if (!userService.checkPassword(req.getPassword(), user)) {
      loginFailLimiter.recordFailure(req.getAccount());
      int failCount =
          userService.onLoginFail(
              user.getId(),
              authProperties.getLoginFail().getMaxAttempts(),
              authProperties.getLoginFail().getLockDuration());
      if (failCount >= authProperties.getLoginFail().getMaxAttempts()) {
        log.warn("[login][account={} 触发账户锁定]", req.getAccount());
      }
      throw new BusinessException(ErrorCode.AUTH_BAD_CREDENTIALS);
    }

    // 登录成功
    userService.onLoginSuccess(user.getId());
    loginFailLimiter.clearFailures(req.getAccount());
    return issueTokenPair(user, generateCsrfToken());
  }

  /**
   * 刷新 Token：校验 refresh token → 旋转签发新对。
   *
   * @param refreshToken 旧 refresh token
   * @return 新 Token 对
   */
  public LoginResponseDTO refresh(String refreshToken) {
    Claims claims;
    try {
      claims = tokenService.parseRefreshToken(refreshToken);
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
    }
    Long userId = claims.get(TokenService.CLAIM_USER_ID, Long.class);
    String username = claims.get(TokenService.CLAIM_USERNAME, String.class);
    // 旋转：旧 token 立即黑名单
    tokenService.revokeRefreshToken(refreshToken);
    SystemUser user = userService.getById(userId);
    return issueTokenPair(user, generateCsrfToken(), userId, username);
  }

  /**
   * 登出：拉黑当前 access + refresh token。
   *
   * @param accessToken 当前 access token
   * @param refreshToken 当前 refresh token
   */
  public void logout(String accessToken, String refreshToken) {
    if (accessToken != null) {
      tokenService.revokeAccessToken(accessToken);
    }
    if (refreshToken != null) {
      tokenService.revokeRefreshToken(refreshToken);
    }
  }

  /**
   * 忘记密码：无论邮箱是否存在都返回相同响应（防枚举）。已注册时发送重置邮件。
   *
   * @param email 邮箱
   */
  public void forgotPassword(String email) {
    SystemUser user = userService.getByAccount(email);
    if (user == null) {
      log.info("[forgotPassword][email={} 未注册，跳过邮件发送]", email);
      return;
    }
    String token = generateResetToken();
    redisTemplate
        .opsForValue()
        .set(
            KEY_RESET_TOKEN + token,
            String.valueOf(user.getId()),
            authProperties.getPasswordReset().getTokenTtl());
    String resetUrl = authProperties.getPasswordReset().getResetUrl() + token;
    mailService.sendPasswordResetMail(email, resetUrl);
  }

  /**
   * 重置密码：校验 token → 更新密码 → 失效 token → 拉黑所有 refresh token。
   *
   * @param token 重置 token
   * @param newRawPassword 新密码
   */
  @Transactional(rollbackFor = Exception.class)
  public void resetPassword(String token, String newRawPassword) {
    String userIdStr = redisTemplate.opsForValue().get(KEY_RESET_TOKEN + token);
    if (userIdStr == null) {
      throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID);
    }
    Long userId = Long.parseLong(userIdStr);
    userService.updatePassword(userId, newRawPassword);
    tokenService.revokeAllRefreshTokensOf(userId);
    redisTemplate.delete(KEY_RESET_TOKEN + token);
  }

  /**
   * 供第三方 OAuth 登录复用：为已完成身份确认的用户签发 Token 对（含 CSRF）。
   *
   * @param user 已验证用户实体
   * @return 登录响应
   */
  public LoginResponseDTO issueTokenPairForOAuth(SystemUser user) {
    return issueTokenPair(user, generateCsrfToken());
  }

  private LoginResponseDTO issueTokenPair(SystemUser user, String csrfToken) {
    return issueTokenPair(user, csrfToken, user.getId(), user.getUsername());
  }

  private LoginResponseDTO issueTokenPair(
      SystemUser user, String csrfToken, Long userId, String username) {
    int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    String accessToken = tokenService.issueAccessToken(userId, username, tokenVersion);
    String refreshToken = tokenService.issueRefreshToken(userId, username, tokenVersion);
    long expiresIn = authProperties.getJwt().getAccessTokenTtl().getSeconds();
    return LoginResponseDTO.builder()
        .userId(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(expiresIn)
        .csrfToken(csrfToken)
        .build();
  }

  private String generateCsrfToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes); // 生成 32 字节随机数
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); // 64 位 URL 安全 Base64
  }

  private String generateResetToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
