package com.ai.api.workflow.engine.node;

import com.ai.api.model.config.ChatClientFactory;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * LLM 调用辅助类。
 *
 * <p>为 QuestionClassifier / ParameterExtractor / Agent 等需要大模型推理的节点提供统一的调用入口， 复用 LlmNodeExecutor 的
 * ChatClient 构建与消息组装逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNodeHelper {

  private final ChatClientFactory chatClientFactory;

  /** 调用 LLM 并返回回复文本与 Token 消耗。 */
  public Result call(String model, String systemPrompt, String userPrompt) {
    if (model == null || model.isBlank()) model = "deepseek-chat";
    if (userPrompt == null) userPrompt = "";

    ChatClient chatClient = chatClientFactory.getChatClient(model);
    if (chatClient == null) {
      throw new IllegalStateException("LLM node: no ChatClient found for model: " + model);
    }

    List<Message> messages = new ArrayList<>();
    if (systemPrompt != null && !systemPrompt.isBlank()) {
      messages.add(new SystemMessage(systemPrompt));
    }
    messages.add(new UserMessage(userPrompt));

    OpenAiChatOptions options =
        OpenAiChatOptions.builder().model(model).temperature(0.1).maxTokens(2048).build();
    ChatModel chatModel = OpenAiChatModel.builder().options(options).build();
    ChatClient clientWithOptions = ChatClient.builder(chatModel).build();

    ChatResponse response = clientWithOptions.prompt().messages(messages).call().chatResponse();

    String text = "";
    long tokens = 0L;
    if (response != null
        && response.getResult() != null
        && response.getResult().getOutput() != null) {
      text = response.getResult().getOutput().getText();
    }
    if (response != null
        && response.getMetadata() != null
        && response.getMetadata().getUsage() != null) {
      tokens = response.getMetadata().getUsage().getTotalTokens();
    }

    log.info("[LlmNodeHelper][model={} tokens={}] 执行完成", model, tokens);
    return new Result(text, tokens);
  }

  /** LLM 调用结果。 */
  public record Result(String text, long tokens) {}
}
