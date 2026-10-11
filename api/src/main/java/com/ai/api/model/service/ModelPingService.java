package com.ai.api.model.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import jakarta.annotation.Resource;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 模型连通性测试 Service
 *
 * <p>依据 design.md D7：按 {@code modelId} 选择对应 {@link ChatClient}， 调用 {@code
 * .prompt().user(prompt).call().content()}，返回 {@link ModelPingResult}。
 *
 * <p>本 change 仅实现"按 modelId 找到对应 ChatClient 并调用"，完整路由策略（fallback、 限流、重试）在后续 {@code
 * add-model-capability} 中实现。
 *
 * @author bootstrap-project
 */
@Slf4j
@Service
public class ModelPingService {

  /** DeepSeek ChatClient */
  @Resource
  @Qualifier("deepSeekChatClient")
  private ChatClient deepSeekChatClient;

  /** 通义千问 ChatClient */
  @Resource
  @Qualifier("qwenChatClient")
  private ChatClient qwenChatClient;

  /** Ollama ChatClient */
  @Resource
  @Qualifier("ollamaChatClient")
  private ChatClient ollamaChatClient;

  /** Provider 名称 → ChatClient Bean 名称的映射 */
  @Resource(name = "chatClientRegistry")
  private Map<String, String> chatClientRegistry;

  /**
   * 测试模型连通性。
   *
   * @param modelId 模型 ID，如 deepseek-chat / qwen-plus / qwen2.5:7b
   * @param prompt 测试提示词
   * @return ModelPingResult
   * @throws BusinessException 模型未配置、未启用或调用失败时抛出
   */
  public ModelPingResult ping(String modelId, String prompt) {
    // 1. 参数校验
    if (!StringUtils.hasText(modelId)) {
      throw new BusinessException(ErrorCode.MODEL_PING_BAD_PARAM);
    }
    if (!StringUtils.hasText(prompt)) {
      throw new BusinessException(ErrorCode.MODEL_PING_BAD_PARAM);
    }

    // 2. 解析 provider 与 ChatClient
    String provider = resolveProvider(modelId);
    ChatClient chatClient = resolveChatClient(provider);

    // 3. 调用并计时
    long start = System.currentTimeMillis();
    try {
      ChatResponse chatResponse = chatClient.prompt().user(prompt).call().chatResponse();
      long latency = System.currentTimeMillis() - start;

      // 4. 提取响应与 Usage
      String content = extractContent(chatResponse);
      Usage usage = chatResponse.getMetadata().getUsage();
      Integer promptTokens =
          usage.getPromptTokens() == null ? 0 : usage.getPromptTokens().intValue();
      Integer completionTokens =
          usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens().intValue();
      Integer totalTokens =
          usage.getTotalTokens() == null
              ? promptTokens + completionTokens
              : usage.getTotalTokens().intValue();

      log.info(
          "[ping][modelId({}) latency({}ms) promptTokens({}) completionTokens({}) totalTokens({})]",
          modelId,
          latency,
          promptTokens,
          completionTokens,
          totalTokens);

      return ModelPingResult.builder()
          .content(content)
          .modelId(modelId)
          .latencyMs(latency)
          .promptTokens(promptTokens)
          .completionTokens(completionTokens)
          .totalTokens(totalTokens)
          .build();
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      long latency = System.currentTimeMillis() - start;
      log.error(
          "[ping][modelId({}) latency({}ms) error({})]", modelId, latency, ex.getMessage(), ex);
      throw new BusinessException(ErrorCode.MODEL_PING_FAILED, "模型调用失败：" + ex.getMessage());
    }
  }

  /**
   * 根据 modelId 解析 provider 名称。
   *
   * <p>本 change 简化策略：
   *
   * <ul>
   *   <li>{@code deepseek-*} → deepseek
   *   <li>{@code qwen*} → qwen
   *   <li>{@code qwen2.5:*} 等带冒号 → ollama
   * </ul>
   *
   * @param modelId 模型 ID
   * @return provider 名称
   * @throws BusinessException 无法识别
   */
  private String resolveProvider(String modelId) {
    if (modelId.toLowerCase().startsWith("deepseek")) {
      return "deepseek";
    }
    if (modelId.toLowerCase().startsWith("qwen2.5:")
        || modelId.toLowerCase().startsWith("llama")
        || modelId.contains(":")) {
      return "ollama";
    }
    if (modelId.toLowerCase().startsWith("qwen")) {
      return "qwen";
    }
    throw new BusinessException(
        ErrorCode.MODEL_NOT_ENABLED, "无法识别 modelId 对应的 provider：" + modelId);
  }

  /**
   * 按 provider 名称选择 ChatClient。
   *
   * @param provider provider 名称
   * @return ChatClient
   * @throws BusinessException 不支持
   */
  private ChatClient resolveChatClient(String provider) {
    switch (provider.toLowerCase()) {
      case "deepseek":
        return deepSeekChatClient;
      case "qwen":
        return qwenChatClient;
      case "ollama":
        return ollamaChatClient;
      default:
        throw new BusinessException(ErrorCode.MODEL_NOT_ENABLED, "不支持的 provider：" + provider);
    }
  }

  /**
   * 提取 ChatResponse 中的内容。
   *
   * @param chatResponse ChatResponse
   * @return String
   */
  private String extractContent(ChatResponse chatResponse) {
    if (chatResponse == null
        || chatResponse.getResult() == null
        || chatResponse.getResult().getOutput() == null) {
      return "";
    }
    return chatResponse.getResult().getOutput().getText();
  }
}
