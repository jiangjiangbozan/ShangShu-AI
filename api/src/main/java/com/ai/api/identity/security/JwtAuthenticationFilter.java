package com.ai.api.identity.security;

import com.ai.api.common.result.Result;
import com.ai.api.identity.config.AuthProperties;
import com.ai.api.identity.rbac.service.RbacPermissionService;
import com.ai.api.identity.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * JWT 认证过滤器
 *
 * <p>依据 design.md D2：自定义过滤器继承 {@link OncePerRequestFilter}，对所有非白名单路径执行 JWT 校验。
 *
 * <p>处理流程：
 *
 * <ol>
 *   <li>从 {@code Authorization: Bearer <token>} 头提取 token
 *   <li>调用 {@link TokenService#parseAccessToken} 校验签名/过期/黑名单
 *   <li>校验成功，构造 {@link AuthUserDetails} 写入 {@link SecurityContextHolder}
 *   <li>校验失败或无 token，跳过，由后续 {@link AuthEntryPoint} 处理
 * </ol>
 *
 * @author add-user-auth
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

  private final TokenService tokenService;
  private final AuthProperties authProperties;
  private final ObjectMapper objectMapper;
  private final RbacPermissionService rbacPermissionService;

  public JwtAuthenticationFilter(
      TokenService tokenService,
      AuthProperties authProperties,
      ObjectMapper objectMapper,
      RbacPermissionService rbacPermissionService) {
    this.tokenService = tokenService;
    this.authProperties = authProperties;
    this.objectMapper = objectMapper;
    this.rbacPermissionService = rbacPermissionService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 白名单直接放行
    if (isWhitelisted(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = extractToken(request);
    if (!StringUtils.hasText(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      Claims claims = tokenService.parseAccessToken(token);
      Long userId = claims.get(TokenService.CLAIM_USER_ID, Long.class);
      String username = claims.get(TokenService.CLAIM_USERNAME, String.class);

      RbacPermissionService.AuthorityBag bag = rbacPermissionService.loadAuthorities(userId);
      AuthUserDetails principal =
          new AuthUserDetails(userId, username, bag.roles(), bag.permissions());

      // 方法级鉴权 authorities：基础身份 ROLE_USER（兼容既有逻辑）+ 角色 ROLE_<code> + 权限点 <code>
      List<SimpleGrantedAuthority> authorities = new ArrayList<>();
      authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
      if (bag.roles() != null) {
        bag.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
      }
      if (bag.permissions() != null) {
        bag.permissions().forEach(perm -> authorities.add(new SimpleGrantedAuthority(perm)));
      }
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(principal, null, authorities);
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (JwtException e) {
      logger.debug("[JwtAuthenticationFilter][token 校验失败：" + e.getMessage() + "]");
      SecurityContextHolder.clearContext();
    }

    filterChain.doFilter(request, response);
  }

  private boolean isWhitelisted(HttpServletRequest request) {
    if (!authProperties.isEnabled()) {
      return true;
    }
    String path = request.getRequestURI();
    List<String> whitelist =
        List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/error");
    return whitelist.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
  }

  private String extractToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }

  private void writeUnauthorizedResponse(HttpServletResponse response, String message)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(Result.fail(401, message)));
  }
}
