package com.ai.api.workflow.engine.node;

import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import com.ai.api.workflow.engine.VariablePool;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 循环节点执行器。
 *
 * <p>根据循环条件判断是否继续循环。条件成立时 edgeLabel="loop"（走循环分支），否则 edgeLabel="exit"（退出循环）。
 * 输出：continueLoop（是否继续）、iteration（当前迭代次数）。
 */
@Slf4j
@Component
public class LoopNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "loop";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    Map<String, Object> loopCondition =
        (Map<String, Object>) data.getOrDefault("loopCondition", Map.of());
    int maxIterations = ((Number) data.getOrDefault("maxIterations", 100)).intValue();

    Map<String, Object> variableSelector =
        (Map<String, Object>) loopCondition.getOrDefault("variableSelector", Map.of());
    String operator = (String) loopCondition.getOrDefault("comparisonOperator", "=");
    String value = (String) loopCondition.getOrDefault("value", "");

    String nodeId = (String) variableSelector.getOrDefault("nodeId", "");
    String outputKey = (String) variableSelector.getOrDefault("outputKey", "");
    Object current = vars.get(nodeId, outputKey);
    String actualStr = current == null ? "" : String.valueOf(current);

    // 迭代计数（从变量池读取自身上一次输出）
    int iteration = 0;
    Object lastIter = vars.get(context.nodeId(), "iteration");
    if (lastIter instanceof Number n) {
      iteration = n.intValue();
    }

    boolean continueLoop = iteration < maxIterations && evaluate(actualStr, operator, value);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("continueLoop", continueLoop);
    outputs.put("iteration", iteration + 1);

    String edgeLabel = continueLoop ? "loop" : "exit";
    log.info(
        "[LoopNode][{}][iteration={} continueLoop={}]", context.nodeId(), iteration, continueLoop);
    return NodeResult.of(outputs, edgeLabel);
  }

  private boolean evaluate(String actual, String operator, String expected) {
    return switch (operator) {
      case "contains" -> actual.contains(expected);
      case "not contains" -> !actual.contains(expected);
      case "start with" -> actual.startsWith(expected);
      case "end with" -> actual.endsWith(expected);
      case "is" -> actual.equals(expected);
      case "is not" -> !actual.equals(expected);
      case "empty" -> actual.isBlank();
      case "not empty" -> !actual.isBlank();
      case "=" -> compareNumericOrString(actual, expected) == 0;
      case "!=" -> compareNumericOrString(actual, expected) != 0;
      case ">" -> compareNumericOrString(actual, expected) > 0;
      case "<" -> compareNumericOrString(actual, expected) < 0;
      case ">=" -> compareNumericOrString(actual, expected) >= 0;
      case "<=" -> compareNumericOrString(actual, expected) <= 0;
      default -> actual.equals(expected);
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
}
