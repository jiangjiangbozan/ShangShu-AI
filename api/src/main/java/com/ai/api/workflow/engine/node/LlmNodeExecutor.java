package com.ai.api.workflow.engine.node;

import com.ai.api.model.config.ChatClientFactory;
import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import com.ai.api.workflow.engine.VariablePool;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * LLM 节点执行器。
 *
 * <p>调用 Spring AI ChatClient 执行大模型推理，支持：
 *
 * <ul>
 *   <li>model: 模型 ID（通过 ChatClientFactory 路由）
 *   <li>promptTemplate: 提示词模板，支持 ${nodeId.key} 变量插值
 *   <li>temperature / maxTokens: 模型参数
 * </ul>
 *
 * 输出：text（模型回复文本）、tokens（消耗 Token 数）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNodeExecutor implements NodeExecutor {

  private final ChatClientFactory chatClientFactory;

  @Override
  public String type() {
    return "llm";
  }

  @Override
  public NodeResult execute(NodeContext context) throws Exception {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    String model = (String) data.getOrDefault("model", "deepseek-chat");
    String promptTemplate = (String) data.getOrDefault("promptTemplate", "");
    double temperature = ((Number) data.getOrDefault("temperature", 0.7)).doubleValue();
    int maxTokens = ((Number) data.getOrDefault("maxTokens", 2048)).intValue();

    // 变量插值
    String resolvedPrompt = resolveTemplate(promptTemplate, vars);

    // 获取 ChatClient
    ChatClient chatClient = chatClientFactory.getChatClient(model);
    if (chatClient == null) {
      throw new IllegalStateException("LLM node: no ChatClient found for model: " + model);
    }

    // 构建消息
    List<Message> messages = new java.util.ArrayList<>();
    String systemPart = "";
    String userPart = resolvedPrompt;
    int sysIdx = resolvedPrompt.indexOf("[SYSTEM]");
    int sysEndIdx = resolvedPrompt.indexOf("[/SYSTEM]");
    if (sysIdx >= 0 && sysEndIdx > sysIdx) {
      systemPart = resolvedPrompt.substring(sysIdx + "[SYSTEM]".length(), sysEndIdx).trim();
      userPart = resolvedPrompt.substring(sysEndIdx + "[/SYSTEM]".length()).trim();
    }
    if (!systemPart.isBlank()) {
      messages.add(new SystemMessage(systemPart));
    }
    messages.add(new UserMessage(userPart));

    // 构建 ChatOptions
    OpenAiChatOptions options =
        OpenAiChatOptions.builder()
            .model(model)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .build();

    // 直接用 OpenAiChatModel 构建独立的 ChatClient，避免 Spring AI ChatClient.options() 泛型问题
    ChatModel chatModel = OpenAiChatModel.builder().options(options).build();
    ChatClient chatClientWithOptions = ChatClient.builder(chatModel).build();

    // 调用 LLM
    ChatResponse chatResponse =
        chatClientWithOptions.prompt().messages(messages).call().chatResponse();

    String text = "";
    long tokens = 0L;
    if (chatResponse != null
        && chatResponse.getResult() != null
        && chatResponse.getResult().getOutput() != null) {
      text = chatResponse.getResult().getOutput().getText();
    }
    if (chatResponse != null
        && chatResponse.getMetadata() != null
        && chatResponse.getMetadata().getUsage() != null) {
      tokens = chatResponse.getMetadata().getUsage().getTotalTokens();
    }

    log.info("[LlmNode][{}][model={} tokens={}] 执行完成", context.nodeId(), model, tokens);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("text", text);
    outputs.put("tokens", tokens);
    return NodeResult.ofLlm(outputs, tokens);
  }

  /** 对模板中的 ${nodeId.key} 做变量插值。 */
  private String resolveTemplate(String template, VariablePool vars) {
    if (template == null || !template.contains("${")) {
      return template;
    }
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < template.length()) {
      int start = template.indexOf("${", i);
      if (start < 0) {
        sb.append(template, i, template.length());
        break;
      }
      int end = template.indexOf("}", start);
      if (end < 0) {
        sb.append(template, i, template.length());
        break;
      }
      sb.append(template, i, start);
      String path = template.substring(start + 2, end);
      Object value = vars.resolve("${" + path + "}");
      sb.append(value != null ? value : "");
      i = end + 1;
    }
    return sb.toString();
  }
}
