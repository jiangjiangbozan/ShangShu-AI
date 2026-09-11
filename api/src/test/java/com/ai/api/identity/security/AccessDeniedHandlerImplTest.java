package com.ai.api.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link AccessDeniedHandlerImpl} 单元测试
 *
 * <p>验证：已登录但权限不足时，SSE 端点能正确返回 SSE error 事件（而非触发 "response already committed"）。对应需求：
 * "在已登录但权限不足的情况下，SSE 接口能正确返回鉴权错误而不出现 response already committed 异常"。
 *
 * <p>不依赖 Redis/RabbitMQ/MySQL，纯单元测试。
 *
 * @author add-user-auth
 */
@DisplayName("AccessDeniedHandlerImpl SSE 错误处理测试")
class AccessDeniedHandlerImplTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AccessDeniedHandlerImpl handler = new AccessDeniedHandlerImpl(objectMapper);

  @Test
  @DisplayName("SSE 请求：返回 event:error 事件并提交响应")
  void handle_withSseRequest_writesSseErrorEventAndCommitsResponse() throws Exception {
    // given：已登录用户访问无权限的资源（如其他人的会话）
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AccessDeniedException ex =
        new AccessDeniedException("Access Denied"); // 模拟 Spring Security 抛出的 403

    // when
    handler.handle(request, response, ex);

    // then
    // 1) 响应已提交：防止后续 ErrorPage 重复写入导致 "response already committed"
    assertThat(response.isCommitted()).as("flushBuffer 应使响应进入 committed 状态").isTrue();
    // 2) Content-Type 为 SSE
    assertThat(response.getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    // 3) 响应体为 SSE error 事件，code=403
    String body = response.getContentAsString();
    assertThat(body).contains("event:error").contains("\"code\":403").contains("权限不足");
    // 4) HTTP 状态码 200（SSE 约定）
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  @DisplayName("普通请求：返回 JSON 错误并提交响应")
  void handle_withNonSseRequest_writesJsonErrorAndCommitsResponse() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AccessDeniedException ex = new AccessDeniedException("Access Denied");

    // when
    handler.handle(request, response, ex);

    // then
    assertThat(response.isCommitted()).as("flushBuffer 应使响应进入 committed 状态").isTrue();
    assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
    assertThat(response.getContentAsString()).contains("\"code\":403").contains("权限不足");
  }
}
