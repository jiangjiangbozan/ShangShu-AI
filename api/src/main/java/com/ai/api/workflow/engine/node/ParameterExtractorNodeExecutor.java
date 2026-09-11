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
import tools.jackson.databind.ObjectMapper;

/**
 * 参数提取节点执行器。
 *
 * <p>调用 LLM 从文本中按配置的参数定义提取结构化字段，返回 JSON。 输出：parameters（提取的参数 map，key 为参数名）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParameterExtractorNodeExecutor implements NodeExecutor {

  private final LlmNodeHelper llmNodeHelper;
  private final ObjectMapper objectMapper;

  @Override
  public String type() {
    return "parameter-extractor";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    String model = (String) data.getOrDefault("model", "deepseek-chat");
    Map<String, Object> querySelector = (Map<String, Object>) data.getOrDefault("query", Map.of());
    List<Map<String, Object>> parameters =
        (List<Map<String, Object>>) data.getOrDefault("parameters", List.of());
    String instruction = (String) data.getOrDefault("instruction", "");

    String query = resolveSelector(querySelector, vars);
    if (query.isBlank()) {
      throw new IllegalArgumentException("ParameterExtractor node: query is empty");
    }

    // 构建参数定义说明
    StringBuilder paramDesc = new StringBuilder();
    for (Map<String, Object> p : parameters) {
      paramDesc
          .append("- ")
          .append(p.getOrDefault("variable", ""))
          .append(" (")
          .append(p.getOrDefault("type", "string"))
          .append("): ")
          .append(p.getOrDefault("label", p.getOrDefault("variable", "")))
          .append("\n");
    }

    String systemPrompt = "你是信息提取助手，只输出 JSON 对象，不要输出任何其他内容。";
    String userPrompt =
        "请从以下文本中提取参数，严格按给出的 JSON 字段名输出 JSON 对象：\n\n"
            + paramDesc
            + (instruction != null && !instruction.isBlank() ? "\n额外要求：" + instruction + "\n" : "")
            + "\n文本：\n"
            + query;

    LlmNodeHelper.Result result = llmNodeHelper.call(model, systemPrompt, userPrompt);

    Map<String, Object> extracted = parseJson(result.text());
    Map<String, Object> outputs = new HashMap<>();
    outputs.put("parameters", extracted);
    outputs.putAll(extracted);

    log.info(
        "[ParameterExtractorNode][{}][extracted {} fields]", context.nodeId(), extracted.size());
    return NodeResult.ofLlm(outputs, result.tokens());
  }

  private Map<String, Object> parseJson(String text) {
    if (text == null || text.isBlank()) return new HashMap<>();
    String json = text.trim();
    // 去掉可能包裹的 ```json ... ```
    if (json.startsWith("```")) {
      int first = json.indexOf('\n');
      int last = json.lastIndexOf("```");
      if (first >= 0 && last > first) {
        json = json.substring(first + 1, last).trim();
      }
    }
    try {
      return objectMapper.readValue(json, Map.class);
    } catch (Exception e) {
      log.warn("[ParameterExtractorNode][无法解析 JSON，返回空 map] {}", e.getMessage());
      return new HashMap<>();
    }
  }

  private String resolveSelector(Map<String, Object> selector, VariablePool vars) {
    String nodeId = (String) selector.getOrDefault("nodeId", "");
    String outputKey = (String) selector.getOrDefault("outputKey", "");
    if (nodeId.isBlank() || outputKey.isBlank()) return "";
    Object value = vars.get(nodeId, outputKey);
    return value != null ? String.valueOf(value) : "";
  }
}
