package com.ai.api.app.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * AI 应用 Controller 的请求与响应模型。
 *
 * <p>公开入口只暴露非敏感字段，绝不把 apiKey 或 apiKeyHash 返回给客户端。
 */
public final class AiApplicationControllerDTO {

  private AiApplicationControllerDTO() {}

  /** publish 接口响应：apiKey 只返回一次，publicCode 可长期用于扫码入口。 */
  @Builder
  @Data
  public static class PublishResponse {

    private String apiKey;
    private String message;
    private String publicCode;
  }

  /** publicApp 接口响应：公开应用配置摘要。 */
  @Builder
  @Data
  public static class PublicAppResponse {

    private String accessPolicy;
    private String appType;
    private String description;
    private String name;
    private String publicCode;
  }

  /** publicChat 接口请求。 */
  @Data
  public static class PublicChatRequest {

    @NotBlank(message = "用户消息不能为空")
    private String message;

    private List<Map<String, String>> history;
  }

  /** publicChat 接口响应。 */
  @Builder
  @Data
  public static class PublicChatResponse {

    private String reply;
  }

  /** publicStream 接口请求。 */
  @Data
  public static class PublicStreamRequest {

    @NotBlank(message = "用户消息不能为空")
    private String message;

    private List<Map<String, String>> history;
  }

  /** listPublic 接口响应。 */
  @Builder
  @Data
  public static class PublicListResponse {

    private List<PublicAppResponse> apps;
  }
}
