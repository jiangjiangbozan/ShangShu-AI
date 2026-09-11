package com.ai.api.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * {@link AuthProperties} 单元测试
 *
 * <p>覆盖：默认值、绑定值、嵌套属性。
 *
 * @author add-user-auth
 */
@DisplayName("AuthProperties 配置绑定测试")
class AuthPropertiesTest {

  @Test
  @DisplayName("默认值应正确")
  void defaults_shouldBeCorrect() {
    // when
    AuthProperties props = new AuthProperties();

    // then
    assertThat(props.isEnabled()).isTrue();
    assertThat(props.getJwt().getAccessTokenTtl()).isEqualTo(Duration.ofHours(2));
    assertThat(props.getJwt().getRefreshTokenTtl()).isEqualTo(Duration.ofDays(7));
    assertThat(props.getJwt().getIssuer()).isEqualTo("ai-agent-platform");
    assertThat(props.getLoginFail().getMaxAttempts()).isEqualTo(5);
    assertThat(props.getLoginFail().getLockDuration()).isEqualTo(Duration.ofMinutes(15));
    assertThat(props.getCsrf().getCookieName()).isEqualTo("csrf-token");
    assertThat(props.getCsrf().getHeaderName()).isEqualTo("X-CSRF-Token");
    assertThat(props.getPasswordReset().getTokenTtl()).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  @DisplayName("Binder 应正确绑定 ai.auth.* 属性")
  void binder_shouldBindFromEnvironment() {
    // given
    StandardEnvironment env = new StandardEnvironment();
    MutablePropertySources sources = env.getPropertySources();
    sources.addFirst(
        new MapPropertySource(
            "test",
            java.util.Map.of(
                "ai.auth.enabled", "false",
                "ai.auth.jwt.secret", "my-secret",
                "ai.auth.jwt.access-token-ttl", "PT1H",
                "ai.auth.jwt.refresh-token-ttl", "P3D",
                "ai.auth.jwt.issuer", "test-issuer",
                "ai.auth.login-fail.max-attempts", "3",
                "ai.auth.login-fail.lock-duration", "PT10M")));

    // when
    AuthProperties props = Binder.get(env).bind("ai.auth", AuthProperties.class).get();

    // then
    assertThat(props.isEnabled()).isFalse();
    assertThat(props.getJwt().getSecret()).isEqualTo("my-secret");
    assertThat(props.getJwt().getAccessTokenTtl()).isEqualTo(Duration.ofHours(1));
    assertThat(props.getJwt().getRefreshTokenTtl()).isEqualTo(Duration.ofDays(3));
    assertThat(props.getJwt().getIssuer()).isEqualTo("test-issuer");
    assertThat(props.getLoginFail().getMaxAttempts()).isEqualTo(3);
    assertThat(props.getLoginFail().getLockDuration()).isEqualTo(Duration.ofMinutes(10));
  }
}
