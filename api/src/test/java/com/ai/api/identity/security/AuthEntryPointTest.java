package com.ai.api.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link AuthEntryPoint} 单元测试
 *
 * <p>验证：
 *
 * <ul>
 *   <li>SSE 请求（Accept: text/event-stream）以 SSE error 事件返回，前端 fetchEventSource 可解析
 *   <li>普通请求以 JSON 返回
 *   <li>响应已提交（flushBuffer 调用），防止 Spring Boot ErrorPage 二次写入触发 "response already committed"
 * </ul>
 *
 * <p>不依赖 Redis/RabbitMQ/MySQL，纯单元测试。
 *
 * @author add-user-auth
 */
@DisplayName("AuthEntryPoint SSE 错误处理测试")
class AuthEntryPointTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AuthEntryPoint entryPoint = new AuthEntryPoint(objectMapper);

  @Test
  @DisplayName("SSE 请求：返回 event:error 事件并提交响应")
  void commence_withSseRequest_writesSseErrorEventAndCommitsResponse() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException ex = new BadCredentialsException("token 已过期");

    // when
    entryPoint.commence(request, response, ex);

    // then
    // 1) 响应已提交：防止后续 ErrorPage 重复写入
    assertThat(response.isCommitted()).as("flushBuffer 应使响应进入 committed 状态").isTrue();
    // 2) Content-Type 为 SSE
    assertThat(response.getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    // 3) 响应体为 SSE error 事件格式
    String body = response.getContentAsString();
    assertThat(body).contains("event:error").contains("\"code\":401").contains("未认证");
    // 4) HTTP 状态码 200（SSE 约定，前端按事件类型判断错误）
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  @DisplayName("普通请求：返回 JSON 错误并提交响应")
  void commence_withNonSseRequest_writesJsonErrorAndCommitsResponse() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException ex = new BadCredentialsException("token 已过期");

    // when
    entryPoint.commence(request, response, ex);

    // then
    assertThat(response.isCommitted()).as("flushBuffer 应使响应进入 committed 状态").isTrue();
    assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
    String body = response.getContentAsString();
    assertThat(body).contains("\"code\":401").contains("未认证");
  }

  @Test
  @DisplayName("无 Accept 头：默认按 JSON 处理")
  void commence_withoutAcceptHeader_writesJsonError() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException ex = new BadCredentialsException("无 token");

    // when
    entryPoint.commence(request, response, ex);

    // then
    assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
    assertThat(response.getContentAsString()).contains("\"code\":401");
  }
}
