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
 * 迭代节点执行器。
 *
 * <p>对输入列表执行迭代，输出：items（迭代结果，每项为源元素）、length（长度）、items 元数据。 简化实现：直接透传列表元素，供下游 节点逐个引用（如通过下标）。
 */
@Slf4j
@Component
public class IterationNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "iteration";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    Map<String, Object> iterator = (Map<String, Object>) data.getOrDefault("iterator", Map.of());
    String nodeId = (String) iterator.getOrDefault("nodeId", "");
    String outputKey = (String) iterator.getOrDefault("outputKey", "");

    Object value = vars.get(nodeId, outputKey);
    List<Object> items;
    if (value instanceof List<?> l) {
      items = new ArrayList<>(l);
    } else if (value == null) {
      items = new ArrayList<>();
    } else {
      items = List.of(value);
    }

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("items", items);
    outputs.put("length", items.size());

    log.info("[IterationNode][{}][iterating {} items]", context.nodeId(), items.size());
    return NodeResult.of(outputs);
  }
}
