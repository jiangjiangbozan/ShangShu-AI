package com.ai.api.identity.config;

import com.ai.api.identity.security.AccessDeniedHandlerImpl;
import com.ai.api.identity.security.AuthEntryPoint;
import com.ai.api.identity.security.CsrfFilter;
import com.ai.api.identity.security.JwtAuthenticationFilter;
import com.ai.api.identity.security.XssFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 7 配置
 *
 * <p>依据 design.md D7 与 spec.md "JWT 认证过滤器"、"CSRF 防护" 要求。
 *
 * <p>过滤链顺序：CsrfFilter → XssFilter → JwtAuthenticationFilter → Spring Security 默认链。
 *
 * <p>白名单路径（匿名可访问）：
 *
 * <ul>
 *   <li>{@code /api/v1/auth/register|login|refresh|password/**}
 *   <li>{@code /actuator/**}
 *   <li>{@code /swagger-ui/**}、{@code /v3/api-docs/**}
 *   <li>{@code /error}
 * </ul>
 *
 * <p>其余路径 {@code authenticated()}。
 *
 * <p>无 HTTP Session（无状态 JWT）。
 *
 * @author add-user-auth
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final XssFilter xssFilter;
  private final CsrfFilter csrfFilter;
  private final AuthEntryPoint authEntryPoint;
  private final AccessDeniedHandlerImpl accessDeniedHandler;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      XssFilter xssFilter,
      CsrfFilter csrfFilter,
      AuthEntryPoint authEntryPoint,
      AccessDeniedHandlerImpl accessDeniedHandler) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.xssFilter = xssFilter;
    this.csrfFilter = csrfFilter;
    this.authEntryPoint = authEntryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    // BCrypt cost=10（默认），单次哈希约 100ms
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // 无状态会话
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // 关闭 Spring Security 默认 CSRF（使用自定义 Double-Submit Cookie）
        .csrf(AbstractHttpConfigurer::disable)
        // 关闭 form 登录与默认 HTTP Basic
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        // 异常处理
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        // 路径授权
        // 关键：dispatcherTypeMatchers(ASYNC/ERROR).permitAll() 让 ASYNC、ERROR 派发跳过鉴权，
        // 仅在初始 REQUEST 派发上做鉴权。
        //
        // 背景：SSE 控制器返回 Flux<ServerSentEvent<String>> 时，Spring MVC 会触发异步派发：
        //   1) 初始 REQUEST 派发：JWT 过滤器写入 SecurityContext → 鉴权通过 → 控制器返回 Flux
        //   2) Flux 开始 emit → 响应提交（headers + 首块已写出）
        //   3) Flux 完成后触发 ASYNC 派发，过滤链再次执行
        //   4) JwtAuthenticationFilter 默认跳过 ASYNC 派发（OncePerRequestFilter），SecurityContext 为空
        //   5) 默认 AuthorizationFilter 在 ASYNC 派发上仍会运行 → 鉴权失败抛
        //      AuthorizationDeniedException → 试图写入错误响应 → "response already committed"
        // permitAll() ASYNC/ERROR 派发后，仅在初始 REQUEST 派发上做鉴权，符合无状态 JWT 的语义，
        // 不会影响普通接口的鉴权流程（普通接口不触发 ASYNC 派发）。
        // 注：Spring Security 7 已移除 shouldFilterAllDispatcherTypes，改用 dispatcherTypeMatchers。
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ASYNC)
                    .permitAll()
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/password/**",
                        "/api/v1/auth/oauth/**",
                        "/actuator/**", // 为了检查服务健康
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // 注册自定义过滤器（顺序：XSS → CSRF → JWT → Spring Security 默认）
        .addFilterBefore(xssFilter, UsernamePasswordAuthenticationFilter.class) // 在 JWT 过滤器之前加入
        .addFilterAfter(csrfFilter, XssFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
