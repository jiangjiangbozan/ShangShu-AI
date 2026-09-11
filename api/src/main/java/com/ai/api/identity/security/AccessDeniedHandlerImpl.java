package com.ai.api.identity.security;

import com.ai.api.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 权限不足处理器
 *
 * <p>当已认证用户访问无权限的资源时，Spring Security 会委托此处理器。
 *
 * <p>SSE 端点（Accept: text/event-stream）特殊处理：以 SSE error 事件返回， 避免 "response already committed" 异常链。
 *
 * @author add-user-auth
 */
@Component
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public AccessDeniedHandlerImpl(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    if (isSseRequest(request)) {
      writeSseError(response, 403, "权限不足");
    } else {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write(objectMapper.writeValueAsString(Result.fail(403, "权限不足")));
    }
    // 提交响应，防止过滤链 unwind 时 Spring Boot ErrorPage 机制重复写入
    response.flushBuffer();
  }

  /** 判断是否为 SSE 流式请求 */
  private boolean isSseRequest(HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
  }

  /** 以 SSE error 事件格式写入错误 */
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
