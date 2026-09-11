package com.ai.api.model.config;

import com.ai.api.data.entity.SysModelConfig;
import com.ai.api.model.config.event.ModelConfigChangedEvent;
import com.ai.api.model.config.service.ModelConfigService;
import com.ai.api.model.config.service.ModelProviderRegistry;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * ChatClient 工厂
 *
 * <p>为每个静态 provider 注册独立的 {@link ChatClient} Bean，业务层通过 {@code @Qualifier("deepSeekChatClient")}
 * 注入对应 Bean。
 *
 * <p>动态模型扩展（对齐 Dify 的 model instance 概念）：{@link #getChatClient(String)} 接收完整 <b>modelId</b> （如
 * {@code deepseek-v3}），优先从 {@link ModelProviderRegistry} 命中配置并<u>热建一个独立 ChatClient</u> （每个模型用自己的
 * baseUrl/apiKey/modelId，复用 OpenAI 兼容协议），按 modelId 缓存；未命中则按 provider 回退到静态 Bean。配置变更时（{@link
 * ModelConfigChangedEvent}）清空动态缓存，下次请求自动重建。
 *
 * @author bootstrap-project
 * @author add-chat-module
 */
@Slf4j
@Configuration
public class ChatClientFactory {

  /** provider → 静态 ChatClient Bean 名称映射。 */
  private static final Map<String, String> STATIC_CLIENT_BEAN_NAMES =
      Map.of(
          "deepseek", "deepSeekChatClient",
          "qwen", "qwenChatClient",
          "ollama", "ollamaChatClient");

  @Resource private ModelConfig modelConfig;
  @Resource private ModelProviderRegistry modelProviderRegistry;
  @Resource private ModelConfigService modelConfigService;
  @Resource private ApplicationContext applicationContext;

  /** 动态 ChatClient 缓存：modelId（小写） -> ChatClient（按配置热建，配置变更时清空）。 */
  private final ConcurrentHashMap<String, ChatClient> dynamicClients = new ConcurrentHashMap<>();

  /**
   * DeepSeek ChatClient（走 OpenAI 兼容协议）。
   *
   * <p>DeepSeek API 兼容 OpenAI 协议，可复用 {@code spring.ai.openai.*} 装配的 ChatModel。
   *
   * @param openAiChatModel Spring AI 自动装配的 OpenAiChatModel
   * @return ChatClient
   */
  @Bean("deepSeekChatClient")
  public ChatClient deepSeekChatClient(
      @Qualifier("openAiChatModel") OpenAiChatModel openAiChatModel) {
    log.info(
        "[deepSeekChatClient][创建 ChatClient，baseUrl({}) modelId({})]",
        modelConfig.getDeepSeek() == null ? null : modelConfig.getDeepSeek().getBaseUrl(),
        modelConfig.getDeepSeek() == null ? null : modelConfig.getDeepSeek().getModelId());
    return ChatClient.builder(openAiChatModel).build();
  }

  /**
   * 通义千问 ChatClient（走 OpenAI 兼容协议）。
   *
   * <p>通义千问 DashScope 提供 OpenAI 兼容接口，复用 OpenAiChatModel，但 base-url 与 api-key 不同。生产环境建议为 Qwen 单独构造
   * OpenAiApi 实例；本 change 阶段共用 DeepSeek 的 OpenAiChatModel（依赖 Spring AI 后续扩展 multi-api-key 支持）。
   *
   * @param openAiChatModel Spring AI 自动装配的 OpenAiChatModel
   * @return ChatClient
   */
  @Bean("qwenChatClient")
  public ChatClient qwenChatClient(@Qualifier("openAiChatModel") OpenAiChatModel openAiChatModel) {
    log.info(
        "[qwenChatClient][创建 ChatClient，baseUrl({}) modelId({})]",
        modelConfig.getQwen() == null ? null : modelConfig.getQwen().getBaseUrl(),
        modelConfig.getQwen() == null ? null : modelConfig.getQwen().getModelId());
    return ChatClient.builder(openAiChatModel).build();
  }

  /**
   * Ollama ChatClient（本地模型，需安装 Ollama）。
   *
   * @param ollamaChatModel Spring AI 自动装配的 OllamaChatModel
   * @return ChatClient
   */
  @Bean("ollamaChatClient")
  public ChatClient ollamaChatClient(
      @Qualifier("ollamaChatModel") OllamaChatModel ollamaChatModel) {
    log.info(
        "[ollamaChatClient][创建 ChatClient，baseUrl({}) modelId({})]",
        modelConfig.getOllama() == null ? null : modelConfig.getOllama().getBaseUrl(),
        modelConfig.getOllama() == null ? null : modelConfig.getOllama().getModelId());
    return ChatClient.builder(ollamaChatModel).build();
  }

  /**
   * 注册 Provider 名称 → ChatClient Bean 名称的映射，供 Service 层按 provider 路由。
   *
   * @return Map
   */
  @Bean("chatClientRegistry")
  public Map<String, String> chatClientRegistry() {
    return Map.copyOf(STATIC_CLIENT_BEAN_NAMES);
  }

  /**
   * 按 modelId 获取 ChatClient（动态路由，Dify 式 model instance）。
   *
   * <p>解析顺序：
   *
   * <ol>
   *   <li>动态注册表：modelId 在 {@code sys_model_config} 中存在 → 按该配置（baseUrl/apiKey/modelId）热建并缓存独立的
   *       ChatClient；
   *   <li>静态 fallback：从 modelId 推断 provider（如 {@code deepseek-v3} → {@code deepseek}），命中静态 Bean
   *       （{@code deepSeekChatClient}）。
   * </ol>
   *
   * @param modelId 模型 ID（如 deepseek-v3 / deepseek-chat / qwen-plus / qwen2.5:7b）
   * @return ChatClient，未找到返回 null
   */
  public ChatClient getChatClient(String modelId) {
    if (modelId == null || modelId.isBlank()) return null;

    // 1. 动态注册表命中且配置了 API Key：按 modelId 热建独立 ChatClient（支持带版本的模型名，如 deepseek-v3）。
    //    API Key 未配置时跳过动态构建，落到静态 provider Bean（走环境变量 Key），
    //    避免构建出无凭证的 OpenAI 兼容客户端，运行时抛出 "credential source must be specified"。
    SysModelConfig config = modelProviderRegistry.getConfig(modelId);
    if (config != null && hasUsableApiKey(config)) {
      return dynamicClients.computeIfAbsent(modelId.toLowerCase(), k -> buildChatClient(config));
    }

    // 2. 静态 provider Bean fallback（modelId 与 provider 名不一致时也能正确路由）
    String provider = resolveProvider(modelId);
    if (provider == null) {
      log.debug("[ChatClientFactory][getChatClient][modelId({}) 无法解析 provider，返回 null]", modelId);
      return null;
    }
    String beanName = STATIC_CLIENT_BEAN_NAMES.get(provider);
    if (beanName == null || !applicationContext.containsBean(beanName)) {
      log.debug(
          "[ChatClientFactory][getChatClient][modelId({}) 无对应静态 Bean({})]", modelId, beanName);
      return null;
    }
    return applicationContext.getBean(beanName, ChatClient.class);
  }

  /** 配置变更时清空动态缓存：ChatClient 依赖具体 baseUrl/apiKey，必须重建。 */
  @EventListener
  public void onModelConfigChanged(ModelConfigChangedEvent event) {
    if (!dynamicClients.isEmpty()) {
      dynamicClients.clear();
      log.info(
          "[ChatClientFactory][onModelConfigChanged][已清空动态 ChatClient 缓存 provider({}) modelId({}) action({})]",
          event.getProvider(),
          event.getModelId(),
          event.getAction());
    }
  }

  /** 动态配置是否具备可用 API Key（无 Key 则无法构建可用的 OpenAI 兼容客户端）。 */
  private boolean hasUsableApiKey(SysModelConfig config) {
    String apiKey = modelConfigService.decryptApiKey(config);
    return apiKey != null && !apiKey.isBlank();
  }

  /**
   * 按模型配置热建 ChatClient（统一走 OpenAI 兼容协议）。
   *
   * <p>Spring AI 2.0 的 {@link OpenAiChatModel.Builder} 会依据 options 中的 baseUrl/apiKey 自动构建底层 OpenAI
   * 客户端，故无需手动构造 OpenAiApi。
   *
   * @param config 模型配置
   * @return ChatClient
   */
  private ChatClient buildChatClient(SysModelConfig config) {
    String apiKey = modelConfigService.decryptApiKey(config);
    OpenAiChatOptions options =
        OpenAiChatOptions.builder()
            .model(config.getModelId())
            .baseUrl(config.getBaseUrl())
            .apiKey(apiKey)
            .build();
    ChatModel chatModel = OpenAiChatModel.builder().options(options).build();
    ChatClient client = ChatClient.builder(chatModel).build();
    log.info(
        "[ChatClientFactory][buildChatClient][modelId({}) provider({}) baseUrl({})]",
        config.getModelId(),
        config.getProvider(),
        config.getBaseUrl());
    return client;
  }

  /**
   * 从 modelId 推断 provider 名称（静态 Bean fallback 用）。
   *
   * <ul>
   *   <li>{@code deepseek-*} → deepseek
   *   <li>{@code qwen*}（无冒号）→ qwen
   *   <li>{@code qwen2.5:*} / {@code llama*} / 含冒号 / {@code ollama*} → ollama
   * </ul>
   *
   * @param modelId 模型 ID
   * @return provider 名称，无法识别返回 null
   */
  private String resolveProvider(String modelId) {
    String lower = modelId.toLowerCase();
    if (lower.startsWith("deepseek")) return "deepseek";
    if (lower.startsWith("qwen2.5:")
        || lower.startsWith("llama")
        || lower.contains(":")
        || lower.startsWith("ollama")) {
      return "ollama";
    }
    if (lower.startsWith("qwen")) return "qwen";
    return null;
  }
}
