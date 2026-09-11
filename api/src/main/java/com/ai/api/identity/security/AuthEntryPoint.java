package com.ai.api.identity.security;

import com.ai.api.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 未认证入口点
 *
 * <p>当 {@link JwtAuthenticationFilter} 未写入 {@code SecurityContext} 但请求路径需要认证时， Spring Security
 * 会委托此入口点。返回 {@link Result}{code:401, message:"未认证"} JSON。
 *
 * <p>SSE 端点（Accept: text/event-stream）特殊处理：以 SSE error 事件返回，避免 fetchEventSource 无法解析 JSON 响应、并避免
 * "response already committed" 异常链。
 *
 * @author add-user-auth
 */
@Component
public class AuthEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public AuthEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    if (isSseRequest(request)) {
      writeSseError(response, 401, "未认证");
    } else {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write(objectMapper.writeValueAsString(Result.fail(401, "未认证")));
    }
    // 提交响应，防止过滤链 unwind 时 Spring Boot ErrorPage 机制重复写入导致
    // "response is already committed" 异常
    response.flushBuffer();
  }

  /** 判断是否为 SSE 流式请求 */
  private boolean isSseRequest(HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
  }

  /** 以 SSE error 事件格式写入错误，前端 fetchEventSource 的 onerror 可正常接收 */
  private void writeSseError(HttpServletResponse response, int code, String message)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write("event:error\ndata:{\"code\":" + code + ",\"error\":\"" + message + "\"}\n\n");
    response.getWriter().flush();
  }
}
