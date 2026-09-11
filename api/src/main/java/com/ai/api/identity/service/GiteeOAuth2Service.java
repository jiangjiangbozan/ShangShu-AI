package com.ai.api.identity.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.identity.config.AuthProperties;
import com.ai.api.identity.dto.LoginResponseDTO;
import com.ai.api.identity.dto.OAuthAuthorizeResponseDTO;
import com.ai.api.identity.entity.SysUserOauth;
import com.ai.api.identity.entity.SystemUser;
import com.ai.api.identity.mapper.SysUserOauthMapper;
import com.ai.api.identity.mapper.SystemUserMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Gitee OAuth2 登录服务
 *
 * <p>完整授权码模式（Authorization Code Flow）：
 *
 * <ol>
 *   <li>{@link #buildAuthorizeUrl()}：生成一次性 {@code state}（防 CSRF）并拼装 Gitee 授权页地址；
 *   <li>用户跳转 Gitee 授权并回调，Gitee 携带 {@code code} 与 {@code state} 回后端；
 *   <li>{@link #handleCallback(String, String)}：校验 state（单次消费）→ 用 code 换 access_token → 拉取用户信息 → 按
 *       (provider, openId) 查找或新建本地用户并绑定 → 复用 {@link AuthService} 签发 JWT。
 * </ol>
 *
 * @author add-oauth-gitee
 */
@Slf4j
@Service
public class GiteeOAuth2Service {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final String PROVIDER = "gitee";
  private static final String KEY_OAUTH_STATE = "auth:oauth:state:";
  private static final String PLACEHOLDER_EMAIL_DOMAIN = "oauth.local";

  private final AuthProperties authProperties;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final SystemUserMapper systemUserMapper;
  private final SysUserOauthMapper sysUserOauthMapper;
  private final PasswordEncoder passwordEncoder;
  private final AuthService authService;
  private final RestClient restClient;

  public GiteeOAuth2Service(
      AuthProperties authProperties,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      SystemUserMapper systemUserMapper,
      SysUserOauthMapper sysUserOauthMapper,
      PasswordEncoder passwordEncoder,
      AuthService authService) {
    this.authProperties = authProperties;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.systemUserMapper = systemUserMapper;
    this.sysUserOauthMapper = sysUserOauthMapper;
    this.passwordEncoder = passwordEncoder;
    this.authService = authService;
    this.restClient = RestClient.builder().build();
  }

  /**
   * 生成 Gitee 授权页跳转地址。
   *
   * @return 授权响应（provider + authorizeUrl）
   */
  public OAuthAuthorizeResponseDTO buildAuthorizeUrl() {
    AuthProperties.Gitee gitee = authProperties.getOauth().getGitee();
    ensureConfigured(gitee);

    String state = generateState();
    redisTemplate
        .opsForValue()
        .set(KEY_OAUTH_STATE + state, "1", authProperties.getOauth().getStateTtl());

    String url =
        gitee.getAuthorizeUrl()
            + "?client_id="
            + urlEncode(gitee.getClientId())
            + "&redirect_uri="
            + urlEncode(gitee.getRedirectUri())
            + "&response_type=code"
            + "&state="
            + urlEncode(state);
    return OAuthAuthorizeResponseDTO.builder().provider(PROVIDER).authorizeUrl(url).build();
  }

  /**
   * 处理 Gitee 授权回调：校验 state → 用 code 换 token → 拉取用户 → 查找或新建本地用户并绑定 → 签发 JWT。
   *
   * @param code 授权码
   * @param state 授权前下发的一次性 state
   * @return 登录响应（含 access/refresh token）
   */
  @Transactional(rollbackFor = Exception.class)
  public LoginResponseDTO handleCallback(String code, String state) {
    AuthProperties.Gitee gitee = authProperties.getOauth().getGitee();
    // state 单次消费，防 CSRF：删除失败或不存在即拒绝
    if (!StringUtils.hasText(state)
        || !Boolean.TRUE.equals(redisTemplate.delete(KEY_OAUTH_STATE + state))) {
      throw new BusinessException(ErrorCode.AUTH_OAUTH_STATE_INVALID);
    }
    if (!StringUtils.hasText(code)) {
      throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED, "缺少授权码 code");
    }

    String accessToken = exchangeToken(gitee, code);
    JsonNode profile = fetchUserInfo(gitee, accessToken);
    SystemUser user = findOrCreateUser(profile);
    return authService.issueTokenPairForOAuth(user);
  }

  /** 用授权码向 Gitee 换取 access token。 */
  private String exchangeToken(AuthProperties.Gitee gitee, String code) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("code", code);
    form.add("client_id", gitee.getClientId());
    form.add("client_secret", gitee.getClientSecret());
    form.add("redirect_uri", gitee.getRedirectUri());

    String body;
    try {
      body =
          restClient
              .post()
              .uri(gitee.getTokenUrl())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(String.class);
    } catch (Exception e) {
      log.warn("[GiteeOAuth][exchangeToken 失败：{}]", e.getMessage());
      throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
    }

    JsonNode node = readJson(body);
    if (node.has("error")) {
      log.warn("[GiteeOAuth][exchangeToken 返回错误：{}]", body);
      throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
    }
    String accessToken = node.path("access_token").asText(null);
    if (!StringUtils.hasText(accessToken)) {
      throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
    }
    return accessToken;
  }

  /** 用 access token 拉取 Gitee 用户信息。 */
  private JsonNode fetchUserInfo(AuthProperties.Gitee gitee, String accessToken) {
    String url = gitee.getUserInfoUrl() + "?access_token=" + urlEncode(accessToken);
    String body;
    try {
      body = restClient.get().uri(url).retrieve().body(String.class);
    } catch (Exception e) {
      log.warn("[GiteeOAuth][fetchUserInfo 失败：{}]", e.getMessage());
      throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
    }
    JsonNode node = readJson(body);
    if (node.isNull() || !node.has("id")) {
      log.warn("[GiteeOAuth][fetchUserInfo 返回异常：{}]", body);
      throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
    }
    return node;
  }

  /** 查找现有绑定；不存在则新建本地用户并写入绑定关系。 */
  private SystemUser findOrCreateUser(JsonNode profile) {
    String openId = profile.path("id").asText();
    String login = profile.path("login").asText(null);
    String name = profile.path("name").asText(null);
    String email = profile.path("email").asText(null);
    String avatarUrl = profile.path("avatar_url").asText(null);

    SysUserOauth existed = sysUserOauthMapper.selectByProviderAndOpenId(PROVIDER, openId);
    if (existed != null) {
      SystemUser user = systemUserMapper.selectById(existed.getUserId());
      if (user == null) {
        throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND);
      }
      return user;
    }

    SystemUser user = new SystemUser();
    user.setUsername(generateUniqueUsername(login, openId));
    user.setEmail(generateEmail(email, openId));
    // OAuth 用户无密码登录：写入随机不可猜密码哈希以满足 NOT NULL，登录仅走第三方通道
    user.setPasswordHash(passwordEncoder.encode(generateRandomPassword()));
    user.setStatus(1);
    user.setFailCount(0);
    systemUserMapper.insert(user);

    SysUserOauth bind = new SysUserOauth();
    bind.setUserId(user.getId());
    bind.setProvider(PROVIDER);
    bind.setOpenId(openId);
    bind.setNickname(StringUtils.hasText(name) ? name : login);
    bind.setAvatarUrl(avatarUrl);
    bind.setRawProfile(profile.toString());
    sysUserOauthMapper.insert(bind);

    log.info("[GiteeOAuth][新建并绑定用户：userId={}, openId={}]", user.getId(), openId);
    return user;
  }

  /** 生成唯一用户名：基于 Gitee login 清洗，冲突时附加序号。 */
  private String generateUniqueUsername(String login, String openId) {
    String base = StringUtils.hasText(login) ? login : "gitee_" + openId;
    base = base.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_");
    if (base.length() > 32) {
      base = base.substring(0, 32);
    }
    if (base.length() < 3) {
      base = "gitee_" + openId;
    }
    String candidate = base;
    int i = 1;
    while (systemUserMapper.selectByUsername(candidate) != null) {
      candidate = base + "_" + i;
      if (candidate.length() > 32) {
        candidate = candidate.substring(0, 32);
      }
      i++;
    }
    return candidate;
  }

  /** 生成邮箱：Gitee 邮箱可能未公开或与本地账号冲突，此时使用占位邮箱规避唯一索引冲突。 */
  private String generateEmail(String email, String openId) {
    if (StringUtils.hasText(email)
        && email.length() <= 128
        && systemUserMapper.selectByEmail(email) == null) {
      return email;
    }
    return "gitee_" + openId + "@" + PLACEHOLDER_EMAIL_DOMAIN;
  }

  private void ensureConfigured(AuthProperties.Gitee gitee) {
    if (!gitee.isEnabled()
        || !StringUtils.hasText(gitee.getClientId())
        || !StringUtils.hasText(gitee.getClientSecret())) {
      throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
    }
  }

  private String generateState() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String generateRandomPassword() {
    return generateState();
  }

  private JsonNode readJson(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (Exception e) {
      log.warn("[GiteeOAuth][JSON 解析失败：{}]", e.getMessage());
      throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
    }
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
