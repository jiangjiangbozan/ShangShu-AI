package com.ai.api.workflow.engine.node;

import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import com.ai.api.workflow.engine.VariablePool;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * IfElse 条件分支节点执行器。
 *
 * <p>按条件列表逐条求值：
 *
 * <ul>
 *   <li>第一个条件为 true → 返回 edgeLabel="if"，走 IF 分支
 *   <li>所有条件都为 false → 返回 edgeLabel="else"，走 ELSE 分支
 * </ul>
 *
 * <p>支持比较运算符：contains, =, !=, &gt;, &lt;, &gt;=, &lt;=, empty, not empty, is, is not, start with,
 * end with。
 */
@Slf4j
@Component
public class IfElseNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "if-else";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    List<Map<String, Object>> conditions =
        (List<Map<String, Object>>) data.getOrDefault("conditions", List.of());

    boolean anyTrue = false;
    for (Map<String, Object> cond : conditions) {
      if (evaluateCondition(cond, vars)) {
        anyTrue = true;
        break;
      }
    }

    String edgeLabel = anyTrue ? "if" : "else";
    log.info("[IfElseNode][{}][result={}]", context.nodeId(), edgeLabel);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("branch", edgeLabel);
    return NodeResult.of(outputs, edgeLabel);
  }

  /** 求值单个条件。 */
  @SuppressWarnings("unchecked")
  private boolean evaluateCondition(Map<String, Object> cond, VariablePool vars) {
    Map<String, Object> selector = (Map<String, Object>) cond.get("variableSelector");
    if (selector == null) return false;

    String nodeId = (String) selector.getOrDefault("nodeId", "");
    String outputKey = (String) selector.getOrDefault("outputKey", "");
    String operator = (String) cond.getOrDefault("comparisonOperator", "=");
    String expectedValue = (String) cond.getOrDefault("value", "");

    Object actualValue = vars.get(nodeId, outputKey);
    if (actualValue == null) actualValue = "";

    String actualStr = String.valueOf(actualValue);

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
      default -> {
        log.warn("[IfElseNode][不支持的比较运算符: {}]", operator);
        yield false;
      }
    };
  }

  /** 数值比较（如果两个值都能转为数字则数值比较，否则字符串比较）。 */
  private int compareNumericOrString(String a, String b) {
    try {
      double da = Double.parseDouble(a);
      double db = Double.parseDouble(b);
      return Double.compare(da, db);
    } catch (NumberFormatException e) {
      return a.compareTo(b);
    }
  }
}
