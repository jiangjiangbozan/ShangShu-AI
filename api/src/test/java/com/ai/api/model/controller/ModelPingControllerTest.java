package com.ai.api.model.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.api.model.service.ModelPingResult;
import com.ai.api.model.service.ModelPingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link ModelPingController} 接口测试
 *
 * <p>依据 design.md D4 与 tasks.md 4.7：使用 MockMvc + Mockito 验证 200 响应与错误码。
 *
 * <p>Spring Boot 4 适配说明：
 *
 * <ul>
 *   <li>{@code @AutoConfigureMockMvc} 迁移到 {@code spring-boot-starter-webmvc-test}，包路径变为 {@code
 *       org.springframework.boot.webmvc.test.autoconfigure}
 *   <li>{@code @MockBean} 已废弃并移除，使用 Spring Framework 的 {@link MockitoBean} 替代， 包路径 {@code
 *       org.springframework.test.context.bean.override.mockito}
 * </ul>
 *
 * @author bootstrap-project
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".*")
class ModelPingControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ModelPingService modelPingService;

  /**
   * 正常请求：200 + code=0
   *
   * @throws Exception 异常
   */
  @Test
  void ping_success() throws Exception {
    // given
    ModelPingResult mockResult =
        ModelPingResult.builder()
            .content("你好！我是 DeepSeek...")
            .modelId("deepseek-chat")
            .latencyMs(1234L)
            .promptTokens(10)
            .completionTokens(50)
            .totalTokens(60)
            .build();
    when(modelPingService.ping(anyString(), anyString())).thenReturn(mockResult);

    // when & then
    String jsonBody = "{\"modelId\":\"deepseek-chat\",\"prompt\":\"你好\"}";
    mockMvc
        .perform(
            post("/api/v1/model/ping").contentType(MediaType.APPLICATION_JSON).content(jsonBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.modelId").value("deepseek-chat"))
        .andExpect(jsonPath("$.data.content").value("你好！我是 DeepSeek..."));
  }

  /**
   * 参数校验失败：modelId 为空时返回 400 + code=400
   *
   * @throws Exception 异常
   */
  @Test
  void ping_missingModelId_returns400() throws Exception {
    // given
    String jsonBody = "{\"modelId\":\"\",\"prompt\":\"你好\"}";

    // when & then
    mockMvc
        .perform(
            post("/api/v1/model/ping").contentType(MediaType.APPLICATION_JSON).content(jsonBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(400));
  }
}
