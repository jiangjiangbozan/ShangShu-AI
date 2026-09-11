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
 * 问题分类节点执行器。
 *
 * <p>调用 LLM 对输入 query 进行意图分类，从预设类别中选择最匹配的一项。 输出：class_id（类别 ID）、class_name（类别名称）、 query。通过
 * edgeLabel 返回类别 ID，引擎据此路由到对应分支。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionClassifierNodeExecutor implements NodeExecutor {

  private final LlmNodeHelper llmNodeHelper;

  @Override
  public String type() {
    return "question-classifier";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    String model = (String) data.getOrDefault("model", "deepseek-chat");
    Map<String, Object> querySelector = (Map<String, Object>) data.getOrDefault("query", Map.of());
    List<Map<String, Object>> categories =
        (List<Map<String, Object>>) data.getOrDefault("categories", List.of());

    String query = resolveSelector(querySelector, vars);

    if (query.isBlank()) {
      throw new IllegalArgumentException("QuestionClassifier node: query is empty");
    }
    if (categories.isEmpty()) {
      throw new IllegalArgumentException("QuestionClassifier node: no categories configured");
    }

    // 构建分类提示词
    StringBuilder categoryDesc = new StringBuilder();
    for (Map<String, Object> c : categories) {
      categoryDesc
          .append("- ")
          .append(c.getOrDefault("name", ""))
          .append(" (")
          .append(c.getOrDefault("id", ""))
          .append("): ")
          .append(c.getOrDefault("description", ""))
          .append("\n");
    }
    String systemPrompt = "你是一个问题分类器，只输出类别 ID，不要输出任何其他内容。";
    String userPrompt = "请将以下问题归类到下列类别之一：\n\n" + categoryDesc + "\n问题：\n" + query + "\n\n只输出类别 ID：";

    LlmNodeHelper.Result result = llmNodeHelper.call(model, systemPrompt, userPrompt);
    String classId = result.text() == null ? "" : result.text().trim().toLowerCase();
    // 清理可能的多余字符
    classId = classId.replaceAll("[^a-zA-Z0-9_-]", "");

    String className = classId;
    boolean matched = false;
    for (Map<String, Object> c : categories) {
      String id = String.valueOf(c.getOrDefault("id", "")).toLowerCase();
      if (id.equals(classId)) {
        className = String.valueOf(c.getOrDefault("name", classId));
        matched = true;
        break;
      }
    }

    if (!matched) {
      // 未匹配到任何类别，回退到第一个类别
      Map<String, Object> first = categories.get(0);
      classId = String.valueOf(first.getOrDefault("id", ""));
      className = String.valueOf(first.getOrDefault("name", classId));
    }

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("class_id", classId);
    outputs.put("class_name", className);
    outputs.put("query", query);

    log.info(
        "[QuestionClassifierNode][{}][query={} -> class={}]",
        context.nodeId(),
        query.substring(0, Math.min(query.length(), 50)),
        classId);
    return NodeResult.of(outputs, classId);
  }

  private String resolveSelector(Map<String, Object> selector, VariablePool vars) {
    String nodeId = (String) selector.getOrDefault("nodeId", "");
    String outputKey = (String) selector.getOrDefault("outputKey", "");
    if (nodeId.isBlank() || outputKey.isBlank()) return "";
    Object value = vars.get(nodeId, outputKey);
    return value != null ? String.valueOf(value) : "";
  }
}
