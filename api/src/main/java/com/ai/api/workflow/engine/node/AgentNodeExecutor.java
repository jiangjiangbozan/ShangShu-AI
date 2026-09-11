package com.ai.api.workflow.engine.node;

import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import com.ai.api.workflow.engine.VariablePool;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 节点执行器。
 *
 * <p>基于提示词模板调用 LLM，按配置的工具列表约束输出（简化实现：不进行真正的工具循环调用，由 LLM 直接生成回复）。 输出：text（模型回复文本）、tokens（消耗 Token
 * 数）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentNodeExecutor implements NodeExecutor {

  private final LlmNodeHelper llmNodeHelper;

  @Override
  public String type() {
    return "agent";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    String model = (String) data.getOrDefault("model", "deepseek-chat");
    String promptTemplate = (String) data.getOrDefault("promptTemplate", "");
    List<String> tools = (List<String>) data.getOrDefault("tools", List.of());
    int maxIterations = ((Number) data.getOrDefault("maxIterations", 10)).intValue();

    // 变量插值
    String resolvedPrompt = resolveTemplate(promptTemplate, vars);

    // 附加工具列表说明
    if (!tools.isEmpty()) {
      resolvedPrompt += "\n\n可使用的工具：" + String.join(", ", tools);
    }

    LlmNodeHelper.Result result = llmNodeHelper.call(model, "", resolvedPrompt);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("text", result.text());
    outputs.put("tokens", result.tokens());
    outputs.put("maxIterations", maxIterations);

    log.info("[AgentNode][{}][tokens={}] 执行完成", context.nodeId(), result.tokens());
    return NodeResult.ofLlm(outputs, result.tokens());
  }

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
