package com.ai.api.workflow.engine.node;

import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import com.ai.api.workflow.engine.VariablePool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 列表过滤节点执行器。
 *
 * <p>对输入列表按指定字段和比较运算符过滤。 输出：result（过滤后的列表）、length（结果数量）。
 */
@Slf4j
@Component
public class ListFilterNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "list-operator";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    Map<String, Object> inputSelector = (Map<String, Object>) data.getOrDefault("input", Map.of());
    Map<String, Object> filterCondition =
        (Map<String, Object>) data.getOrDefault("filterCondition", Map.of());

    Object input = resolveSelector(inputSelector, vars);
    List<Object> inputList;
    if (input instanceof List<?> l) {
      inputList = new ArrayList<>(l);
    } else if (input == null) {
      inputList = new ArrayList<>();
    } else {
      inputList = List.of(input);
    }

    String key = (String) filterCondition.getOrDefault("key", "");
    String operator = (String) filterCondition.getOrDefault("comparisonOperator", "=");
    String expectedValue = (String) filterCondition.getOrDefault("value", "");

    List<Object> filtered = new ArrayList<>();
    for (Object item : inputList) {
      Object fieldValue = extractField(item, key);
      if (matches(fieldValue, operator, expectedValue)) {
        filtered.add(item);
      }
    }

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("result", filtered);
    outputs.put("length", filtered.size());

    log.info(
        "[ListFilterNode][{}][filtered {} -> {}]",
        context.nodeId(),
        inputList.size(),
        filtered.size());
    return NodeResult.of(outputs);
  }

  private Object extractField(Object item, String key) {
    if (item instanceof Map<?, ?> m) {
      return m.get(key);
    }
    return null;
  }

  private boolean matches(Object fieldValue, String operator, String expectedValue) {
    String actualStr = fieldValue == null ? "" : String.valueOf(fieldValue);
    return switch (operator) {
      case "contains" -> actualStr.contains(expectedValue);
      case "not contains" -> !actualStr.contains(expectedValue);
      case "start with" -> actualStr.startsWith(expectedValue);
      case "end with" -> actualStr.endsWith(expectedValue);
      case "is" -> actualStr.equals(expectedValue);
      case "is not" -> !actualStr.equals(expectedValue);
      case "empty" -> actualStr.isBlank();
      case "not empty" -> !actualStr.isBlank();
      case "=" -> compareNumericOrString(actualStr, expectedValue) == 0;
      case "!=" -> compareNumericOrString(actualStr, expectedValue) != 0;
      case ">" -> compareNumericOrString(actualStr, expectedValue) > 0;
      case "<" -> compareNumericOrString(actualStr, expectedValue) < 0;
      case ">=" -> compareNumericOrString(actualStr, expectedValue) >= 0;
      case "<=" -> compareNumericOrString(actualStr, expectedValue) <= 0;
      default -> actualStr.equals(expectedValue);
    };
  }

  private int compareNumericOrString(String a, String b) {
    try {
      double da = Double.parseDouble(a);
      double db = Double.parseDouble(b);
      return Double.compare(da, db);
    } catch (NumberFormatException e) {
      return a.compareTo(b);
    }
  }

  private Object resolveSelector(Map<String, Object> selector, VariablePool vars) {
    String nodeId = (String) selector.getOrDefault("nodeId", "");
    String outputKey = (String) selector.getOrDefault("outputKey", "");
    if (nodeId.isBlank() || outputKey.isBlank()) return null;
    return vars.get(nodeId, outputKey);
  }
}
