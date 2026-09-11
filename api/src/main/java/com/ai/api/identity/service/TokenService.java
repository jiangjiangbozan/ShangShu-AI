package com.ai.api.identity.service;

import com.ai.api.identity.config.AuthProperties;
import com.ai.api.identity.entity.SystemUser;
import com.ai.api.identity.mapper.SystemUserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Token 服务
 *
 * <p>依据 design.md D1：JWT HS256 对称加密；D4：Refresh Token 旋转。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>签发 access token（默认 2h）与 refresh token（默认 7d）
 *   <li>解析与校验 token 签名、过期时间
 *   <li>维护 JTI 黑名单（access + refresh），登出/刷新/重置密码时写入
 * </ul>
 *
 * <p>Redis Key 命名空间：
 *
 * <ul>
 *   <li>{@code auth:refresh:{jti}}：refresh token 白名单（旋转机制）
 *   <li>{@code auth:access:blacklist:{jti}}：access token 黑名单
 *   <li>{@code auth:refresh:blacklist:{jti}}：refresh token 黑名单
 *   <li>{@code auth:user:version:{userId}}：用户令牌版本号缓存（毫秒级全量作废）
 *   <li>{@code auth:user:refresh:jtis:{userId}}：用户 → refresh token jti 集合（批量失效）
 * </ul>
 *
 * @author add-user-auth
 */
@Slf4j
@Service
public class TokenService {

  public static final String CLAIM_USER_ID = "uid";
  public static final String CLAIM_USERNAME = "username";
  public static final String CLAIM_TOKEN_TYPE = "type";
  public static final String CLAIM_TOKEN_VERSION = "ver";
  public static final String TYPE_ACCESS = "access";
  public static final String TYPE_REFRESH = "refresh";

  private static final String KEY_REFRESH = "auth:refresh:";
  private static final String KEY_ACCESS_BLACKLIST = "auth:access:blacklist:";
  private static final String KEY_REFRESH_BLACKLIST = "auth:refresh:blacklist:";
  private static final String KEY_USER_VERSION = "auth:user:version:";
  private static final String KEY_USER_REFRESH_JTIS = "auth:user:refresh:jtis:";

  private final AuthProperties authProperties;
  private final StringRedisTemplate redisTemplate;
  private final SystemUserMapper systemUserMapper;
  private final SecretKey secretKey;

  public TokenService(
      AuthProperties authProperties,
      StringRedisTemplate redisTemplate,
      SystemUserMapper systemUserMapper) {
    this.authProperties = authProperties;
    this.redisTemplate = redisTemplate;
    this.systemUserMapper = systemUserMapper;
    byte[] keyBytes = authProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
    this.secretKey = Keys.hmacShaKeyFor(keyBytes);
  }

  /**
   * 签发 access token。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param tokenVersion 令牌版本号（写入 payload ver，用于改密码后毫秒级全量作废）
   * @return token 字符串
   */
  public String issueAccessToken(Long userId, String username, int tokenVersion) {
    return issueToken(
        userId, username, tokenVersion, TYPE_ACCESS, authProperties.getJwt().getAccessTokenTtl());
  }

  /**
   * 签发 refresh token，并将 JTI 写入 Redis 白名单与用户 jti 集合。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param tokenVersion 令牌版本号
   * @return token 字符串
   */
  public String issueRefreshToken(Long userId, String username, int tokenVersion) {
    String token =
        issueToken(
            userId,
            username,
            tokenVersion,
            TYPE_REFRESH,
            authProperties.getJwt().getRefreshTokenTtl());
    String jti = parseClaims(token).getId();
    // 写入 refresh token 白名单（JTI → userId）
    // opsForValue() 获取字符串类型操作对象，对应 Redis 的 SET 命令。
    redisTemplate
        .opsForValue()
        .set(
            KEY_REFRESH + jti,
            String.valueOf(userId),
            authProperties.getJwt().getRefreshTokenTtl());
    // 维护 user → jti 集合，支持一次性批量失效该用户全部 refresh token
    // 保存用户的全部 refresh 编号，方便批量全部作废
    redisTemplate.opsForSet().add(KEY_USER_REFRESH_JTIS + userId, jti);
    // 为 refresh token 白名单设置过期时间（与 refresh token 有效期一致）
    redisTemplate.expire(
        KEY_USER_REFRESH_JTIS + userId, authProperties.getJwt().getRefreshTokenTtl());
    return token;
  }

  /**
   * 解析 access token 并校验签名、过期、JTI 黑名单与令牌版本号。
   *
   * @param token access token
   * @return Claims，校验失败抛出 {@link JwtException}
   */
  public Claims parseAccessToken(String token) {
    Claims claims = parseClaims(token);
    if (!TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE))) {
      throw new JwtException("Token 类型不匹配，预期 access");
    }
    String jti = claims.getId();
    if (isInBlacklist(KEY_ACCESS_BLACKLIST + jti)) {
      throw new JwtException("Access token 已被撤销");
    }
    verifyTokenVersion(claims, TYPE_ACCESS);
    return claims;
  }

  /**
   * 解析 refresh token 并校验签名、过期、JTI 白名单、黑名单与令牌版本号。
   *
   * @param token refresh token
   * @return Claims，校验失败抛出 {@link JwtException}
   */
  public Claims parseRefreshToken(String token) {
    Claims claims = parseClaims(token);
    if (!TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE))) {
      throw new JwtException("Token 类型不匹配，预期 refresh");
    }
    String jti = claims.getId();
    if (isInBlacklist(KEY_REFRESH_BLACKLIST + jti)) {
      throw new JwtException("Refresh token 已失效");
    }
    if (Boolean.FALSE.equals(redisTemplate.hasKey(KEY_REFRESH + jti))) {
      throw new JwtException("Refresh token 不在白名单");
    }
    verifyTokenVersion(claims, TYPE_REFRESH);
    return claims;
  }

  /**
   * 撤销 access token（加入黑名单）。
   *
   * @param token access token
   */
  public void revokeAccessToken(String token) {
    try {
      Claims claims = parseClaims(token);
      String jti = claims.getId();
      long remaining = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
      if (remaining > 0) {
        redisTemplate
            .opsForValue()
            .set(KEY_ACCESS_BLACKLIST + jti, "1", Duration.ofSeconds(remaining));
        log.info("[revokeAccessToken][jti={}]", jti);
      }
    } catch (ExpiredJwtException e) {
      log.debug("[revokeAccessToken][token 已过期，跳过]");
    } catch (JwtException e) {
      log.warn("[revokeAccessToken][解析失败：{}]", e.getMessage());
    }
  }

  /**
   * 撤销 refresh token（加入黑名单、移除白名单并从用户 jti 集合移除）。
   *
   * @param token refresh token
   */
  public void revokeRefreshToken(String token) {
    try {
      Claims claims = parseClaims(token);
      String jti = claims.getId();
      Long userId = claims.get(CLAIM_USER_ID, Long.class);
      long remaining = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
      if (remaining > 0) {
        redisTemplate
            .opsForValue()
            .set(KEY_REFRESH_BLACKLIST + jti, "1", Duration.ofSeconds(remaining));
      }
      redisTemplate.delete(KEY_REFRESH + jti);
      if (userId != null) {
        redisTemplate.opsForSet().remove(KEY_USER_REFRESH_JTIS + userId, jti);
      }
      log.info("[revokeRefreshToken][jti={}]", jti);
    } catch (ExpiredJwtException e) {
      log.debug("[revokeRefreshToken][token 已过期，跳过]");
    } catch (JwtException e) {
      log.warn("[revokeRefreshToken][解析失败：{}]", e.getMessage());
    }
  }

  /**
   * 将该用户所有已签发令牌全部失效。具体步骤：
   *
   * <ol>
   *   <li>递增令牌版本号：使该用户所有 access/refresh token 因 {@code ver} 不匹配立即失效（毫秒级全量作废，无需等待黑名单 TTL）；
   *   <li>遍历 user → jti 集合，批量拉黑并移除所有 refresh token 白名单。
   * </ol>
   *
   * @param userId 用户 ID
   */
  public void revokeAllRefreshTokensOf(Long userId) {
    // 递增版本号：旧 token 立即失效
    int newVersion = incrementTokenVersion(userId);

    // 批量失效该用户所有 refresh token 白名单并清空 jti 集合
    String jtiSetKey = KEY_USER_REFRESH_JTIS + userId;
    Set<String> jtis = redisTemplate.opsForSet().members(jtiSetKey);
    int revoked = 0;
    if (jtis != null && !jtis.isEmpty()) {
      for (String jti : jtis) {
        String whitelistKey = KEY_REFRESH + jti;
        Long remaining = redisTemplate.getExpire(whitelistKey, TimeUnit.SECONDS);
        if (remaining != null && remaining > 0) {
          redisTemplate
              .opsForValue()
              .set(KEY_REFRESH_BLACKLIST + jti, "1", Duration.ofSeconds(remaining));
        }
        redisTemplate.delete(whitelistKey);
        revoked++;
      }
    }
    redisTemplate.delete(jtiSetKey);
    log.info(
        "[revokeAllRefreshTokensOf][userId={}, newVersion={}, revokedRefreshTokens={}]",
        userId,
        newVersion,
        revoked);
  }

  /**
   * 递增用户令牌版本号，使该用户所有既有 JWT 立即失效。
   *
   * <p>数据库 {@code token_version} 原子自增，并将新版本号写入 Redis 缓存（write-through），后续请求经 {@link
   * #verifyTokenVersion} 比对发现 {@code ver} 不匹配即拒绝。
   *
   * @param userId 用户 ID
   * @return 递增后的新版本号
   */
  public int incrementTokenVersion(Long userId) {
    systemUserMapper.incrementTokenVersion(userId);
    SystemUser user = systemUserMapper.selectById(userId);
    int newVersion = user == null || user.getTokenVersion() == null ? 1 : user.getTokenVersion();
    redisTemplate.opsForValue().set(KEY_USER_VERSION + userId, String.valueOf(newVersion));
    log.info("[incrementTokenVersion][userId={}, newVersion={}]", userId, newVersion);
    return newVersion;
  }

  /**
   * 校验 token 内 {@code ver} 与用户当前令牌版本号是否一致，不一致则抛出异常。
   *
   * @param claims token 负载
   * @param type token 类型（用于异常信息）
   */
  private void verifyTokenVersion(Claims claims, String type) {
    Long userId = claims.get(CLAIM_USER_ID, Long.class);
    Integer tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
    int currentVersion = getCurrentTokenVersion(userId);
    if (tokenVersion == null || tokenVersion != currentVersion) {
      throw new JwtException(type + " token 版本已失效，请重新登录");
    }
  }

  /**
   * 获取用户当前令牌版本号（Redis 缓存优先，未命中回源 DB 并回填缓存）。
   *
   * @param userId 用户 ID
   * @return 当前令牌版本号
   */
  private int getCurrentTokenVersion(Long userId) {
    String key = KEY_USER_VERSION + userId;
    try {
      String cached = redisTemplate.opsForValue().get(key);
      if (cached != null) {
        return Integer.parseInt(cached);
      }
    } catch (Exception e) {
      log.warn("[getCurrentTokenVersion][Redis 读取异常，回退 DB：{}]", e.getMessage());
    }
    SystemUser user = systemUserMapper.selectById(userId);
    int version = user == null || user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    try {
      redisTemplate.opsForValue().set(key, String.valueOf(version));
    } catch (Exception e) {
      log.warn("[getCurrentTokenVersion][Redis 缓存写入异常：{}]", e.getMessage());
    }
    return version;
  }

  private String issueToken(
      Long userId, String username, int tokenVersion, String type, Duration ttl) {
    Instant now = Instant.now();
    return Jwts.builder()
        .id(UUID.randomUUID().toString()) // jti，令牌 ID。每个 JWT 独一无二。
        .issuer(authProperties.getJwt().getIssuer()) // 签发者，通常是应用或服务的名称。
        .subject(String.valueOf(userId)) // sub，令牌的主题，通常是用户 ID。
        .claim(CLAIM_USER_ID, userId) // 自定义声明，存储用户 ID。
        .claim(CLAIM_USERNAME, username) // 自定义声明，存储用户名。
        .claim(CLAIM_TOKEN_TYPE, type) // 自定义声明，存储令牌类型。
        .claim(CLAIM_TOKEN_VERSION, tokenVersion) // 令牌版本号，改密码/强制下线后递增使旧 token 立即失效。
        .issuedAt(Date.from(now)) // iat，令牌签发时间。
        .expiration(Date.from(now.plus(ttl))) // exp，令牌过期时间。
        .signWith(secretKey) // 签名算法和密钥，用于验证令牌的真实性。
        .compact(); // 生成紧凑的 JWT 字符串。
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload(); // 解析 JWT 字符串并返回负载（Claims）。
  }

  private boolean isInBlacklist(String key) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(key));
  }
}
