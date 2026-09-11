package com.ai.api.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 大模型配置
 *
 * <p>依据 design.md D7：从 {@code ai.model.*} 读取 DeepSeek/Qwen/Ollama 配置。 用于业务层获取当前可用 provider 与默认
 * modelId。
 *
 * <p>注意：实际与 Spring AI starter 通信所需的 api-key/base-url 通过 {@code spring.ai.openai.*} 与 {@code
 * spring.ai.ollama.*} 注入到 starter 自动装配的 ChatModel Bean，本配置仅用于业务层路由与展示。
 *
 * @author bootstrap-project
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.model")
public class ModelConfig {

  /** DeepSeek 配置 */
  private ProviderConfig deepSeek;

  /** 通义千问配置 */
  private ProviderConfig qwen;

  /** Ollama 配置 */
  private ProviderConfig ollama;

  /** 单个 Provider 配置。 */
  @Data
  public static class ProviderConfig {
    /** API Key（从环境变量读取） */
    private String apiKey;

    /** 基础 URL */
    private String baseUrl;

    /** 默认模型 ID */
    private String modelId;

    /** 是否启用 */
    private Boolean enabled = true;
  }

  /**
   * 按 provider 名称获取配置。
   *
   * @param provider deepseek/qwen/ollama
   * @return ProviderConfig，不存在返回 null
   */
  public ProviderConfig getProvider(String provider) {
    if (provider == null) {
      return null;
    }
    switch (provider.toLowerCase()) {
      case "deepseek":
        return deepSeek;
      case "qwen":
        return qwen;
      case "ollama":
        return ollama;
      default:
        return null;
    }
  }
}
