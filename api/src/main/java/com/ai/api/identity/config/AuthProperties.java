package com.ai.api.identity.config;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户认证配置属性
 *
 * <p>对应配置项前缀 {@code ai.auth}，绑定 application.yml 中的 auth 段。
 *
 * <p>各字段含义参见 design.md D7 与 application-dev.yml 注释。
 *
 * @author add-user-auth
 */
@Setter
@Getter
@ConfigurationProperties("ai.auth")
public class AuthProperties {

  /** 是否启用认证过滤链（false 时所有路径匿名可访问，用于灰度回滚） */
  private boolean enabled = true;

  /** JWT 配置 */
  private Jwt jwt = new Jwt();

  /** 登录失败限制配置 */
  private LoginFail loginFail = new LoginFail();

  /** CSRF 防护配置 */
  private Csrf csrf = new Csrf();

  /** 密码重置配置 */
  private PasswordReset passwordReset = new PasswordReset();

  /** 邮件服务配置 */
  private Mail mail = new Mail();

  /** 第三方 OAuth2 登录配置 */
  private Oauth oauth = new Oauth();

  /** JWT 配置 */
  @Setter
  @Getter
  public static class Jwt {
    /** HS256 签名密钥（≥32 字符），生产环境通过环境变量注入 */
    private String secret = "";

    /** access token TTL */
    private Duration accessTokenTtl = Duration.ofHours(2);

    /** refresh token TTL */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** 签发者 */
    private String issuer = "ai-agent-platform";
  }

  /** 登录失败限制配置 */
  @Setter
  @Getter
  public static class LoginFail {
    /** 最大连续失败次数，超过后锁定账户 */
    private int maxAttempts = 5;

    /** 锁定时长 */
    private Duration lockDuration = Duration.ofMinutes(15);
  }

  /** CSRF 防护配置 */
  @Setter
  @Getter
  public static class Csrf {
    /** Cookie 名 */
    private String cookieName = "csrf-token";

    /** 请求头名 */
    private String headerName = "X-CSRF-Token";

    /** Cookie domain */
    private String cookieDomain = "";

    /** Cookie max-age */
    private Duration cookieMaxAge = Duration.ofHours(2);

    /** 路径白名单（不校验 CSRF 的路径） */
    private List<String> pathWhitelist = List.of();
  }

  /** 密码重置配置 */
  @Setter
  @Getter
  public static class PasswordReset {
    /** 重置 token TTL */
    private Duration tokenTtl = Duration.ofMinutes(30);

    /** 重置链接 URL 模板（{token} 占位符会被替换） */
    private String resetUrl = "";
  }

  /** 邮件服务配置 */
  @Setter
  @Getter
  public static class Mail {
    private String host = "";
    private int port = 587;
    private String username = "";
    private String password = "";
    private String from = "";
    private String fromName = "";
  }

  /** 第三方 OAuth2 登录配置 */
  @Setter
  @Getter
  public static class Oauth {
    /** 前端回调地址（后端登录成功后 302 跳转此地址，令牌经 URL fragment 传递） */
    private String frontendCallbackUrl = "http://localhost:5173/auth/oauth/callback";

    /** state 参数 TTL（OAuth 授权流程防 CSRF 的一次性凭证） */
    private Duration stateTtl = Duration.ofMinutes(10);

    /** Gitee 平台配置 */
    private Gitee gitee = new Gitee();
  }

  /** Gitee OAuth2 配置 */
  @Setter
  @Getter
  public static class Gitee {
    /** 是否启用（未配置 clientId/secret 时应保持 false） */
    private boolean enabled = false;

    /** 应用 Client ID */
    private String clientId = "";

    /** 应用 Client Secret */
    private String clientSecret = "";

    /** 授权地址 */
    private String authorizeUrl = "https://gitee.com/oauth/authorize";

    /** 换取令牌地址 */
    private String tokenUrl = "https://gitee.com/oauth/token";

    /** 用户信息地址 */
    private String userInfoUrl = "https://gitee.com/api/v5/user";

    /** 后端回调地址（需与 Gitee 应用登记的 redirect_uri 完全一致） */
    private String redirectUri = "http://localhost:8080/api/v1/auth/oauth/gitee/callback";
  }
}
