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
 * 变量赋值节点执行器。
 *
 * <p>将上游节点的输出值赋值到指定变量名，供下游节点引用。 输出：所有赋值后的变量。
 */
@Slf4j
@Component
public class VariableAssignerNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "variable-assigner";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) throws Exception {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    List<Map<String, Object>> assignments =
        (List<Map<String, Object>>) data.getOrDefault("assignments", List.of());

    Map<String, Object> outputs = new HashMap<>();
    for (Map<String, Object> assignment : assignments) {
      String variable = (String) assignment.getOrDefault("variable", "");
      Map<String, Object> valueSelector =
          (Map<String, Object>) assignment.getOrDefault("valueSelector", Map.of());
      String srcNodeId = (String) valueSelector.getOrDefault("nodeId", "");
      String srcKey = (String) valueSelector.getOrDefault("outputKey", "");

      Object value = "";
      if (!srcNodeId.isBlank() && !srcKey.isBlank()) {
        value = vars.get(srcNodeId, srcKey);
      }
      outputs.put(variable, value);
      // 同时写入变量池供后续节点引用
      vars.put(context.nodeId(), outputs);
    }

    log.info("[VariableAssignerNode][{}][assigned {} vars]", context.nodeId(), outputs.size());
    return NodeResult.of(outputs);
  }
}
