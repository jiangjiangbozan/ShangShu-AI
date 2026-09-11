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
 * 变量聚合节点执行器。
 *
 * <p>将多个上游节点输出聚合为一个列表，供下游节点迭代使用。 输出：items（聚合后的列表）、length（长度）。
 */
@Slf4j
@Component
public class VariableAggregatorNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "variable-aggregator";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    List<Map<String, Object>> selectors =
        (List<Map<String, Object>>) data.getOrDefault("variables", List.of());

    List<Object> items = new ArrayList<>();
    for (Map<String, Object> selector : selectors) {
      String nodeId = (String) selector.getOrDefault("nodeId", "");
      String outputKey = (String) selector.getOrDefault("outputKey", "");
      if (nodeId.isBlank() || outputKey.isBlank()) continue;
      Object value = vars.get(nodeId, outputKey);
      if (value != null) {
        items.add(value);
      }
    }

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("items", items);
    outputs.put("length", items.size());

    log.info("[VariableAggregatorNode][{}][aggregated {} items]", context.nodeId(), items.size());
    return NodeResult.of(outputs);
  }
}
