package com.ai.api.workflow.engine.node;

import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Start 节点执行器。
 *
 * <p>将运行时输入参数透传到 VariablePool，作为后续节点的上游数据。
 */
@Component
public class StartNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "start";
  }

  @Override
  public NodeResult execute(NodeContext context) {
    // Start 节点的输出 = 运行时输入
    Map<String, Object> outputs = new LinkedHashMap<>(context.inputs());
    // 将 variables 配置的默认值填充（如果运行时未传入）
    @SuppressWarnings("unchecked")
    java.util.List<Map<String, Object>> variables =
        (java.util.List<Map<String, Object>>)
            context.data().getOrDefault("variables", java.util.List.of());
    for (Map<String, Object> var : variables) {
      String name = (String) var.getOrDefault("variable", "");
      if (!name.isBlank() && !outputs.containsKey(name)) {
        outputs.put(name, var.getOrDefault("default", ""));
      }
    }
    return NodeResult.of(outputs);
  }
}
