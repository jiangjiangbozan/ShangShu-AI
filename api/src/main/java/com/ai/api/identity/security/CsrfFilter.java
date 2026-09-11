package com.ai.api.identity.security;

import com.ai.api.common.result.Result;
import com.ai.api.identity.config.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * CSRF 过滤器（Double-Submit Cookie 模式）
 *
 * <p>依据 design.md D5：对非 GET 请求校验 {@code X-CSRF-Token} 请求头与 {@code csrf-token} Cookie
 * 一致。登录/注册等豁免路径跳过校验。
 *
 * <p>使用 AntPathMatcher 支持通配符路径。
 *
 * <p>Bearer Token 请求跳过 CSRF 校验：CSRF 攻击依赖浏览器自动携带 Cookie 的机制， 而 Bearer Token 由客户端显式设置，天然免疫
 * CSRF。小程序/Android 等非浏览器客户端 无法可靠管理 Cookie，因此携带 Bearer Token 时免除 Cookie 校验。
 *
 * @author add-user-auth
 */
@Component
public class CsrfFilter extends OncePerRequestFilter {

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

  private final AuthProperties authProperties;
  private final ObjectMapper objectMapper;

  public CsrfFilter(AuthProperties authProperties, ObjectMapper objectMapper) {
    this.authProperties = authProperties;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (isExempt(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String method = request.getMethod();
    if ("GET".equalsIgnoreCase(method)
        || "HEAD".equalsIgnoreCase(method)
        || "OPTIONS".equalsIgnoreCase(method)) {
      filterChain.doFilter(request, response);
      return;
    }

    // Bearer Token 请求天然免疫 CSRF（非浏览器客户端无法可靠管理 Cookie），跳过校验
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String headerToken = request.getHeader(authProperties.getCsrf().getHeaderName());
    String cookieToken = extractCookieToken(request);

    if (!StringUtils.hasText(headerToken) || !headerToken.equals(cookieToken)) {
      writeForbiddenResponse(request, response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private boolean isExempt(HttpServletRequest request) {
    if (!authProperties.isEnabled()) {
      return true;
    }
    String path = request.getRequestURI();
    List<String> whitelist = authProperties.getCsrf().getPathWhitelist();
    if (whitelist == null || whitelist.isEmpty()) {
      return false;
    }
    return whitelist.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
  }

  private String extractCookieToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    String cookieName = authProperties.getCsrf().getCookieName();
    for (Cookie cookie : cookies) {
      if (cookieName.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private void writeForbiddenResponse(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String accept = request.getHeader("Accept");
    if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
      // SSE 请求：以 error 事件返回，前端 fetchEventSource 可正常接收
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("event:error\ndata:{\"code\":403,\"error\":\"CSRF 校验失败\"}\n\n");
      response.getWriter().flush();
    } else {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write(objectMapper.writeValueAsString(Result.fail(403, "CSRF 校验失败")));
    }
    // 提交响应，防止 "response already committed" 异常
    response.flushBuffer();
  }
}
