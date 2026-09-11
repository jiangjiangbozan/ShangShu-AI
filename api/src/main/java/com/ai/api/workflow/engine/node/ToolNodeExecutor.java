package com.ai.api.workflow.engine.node;

import com.ai.api.agent.skill.SkillRegistry;
import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import com.ai.api.workflow.engine.VariablePool;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具节点执行器。
 *
 * <p>调用系统内注册的 Skill（对应 Dify 的工具）。按 toolName 路由到 SkillRegistry，输入参数支持变量引用。 输出：result（工具 返回结果）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolNodeExecutor implements NodeExecutor {

  private final SkillRegistry skillRegistry;

  @Override
  public String type() {
    return "tool";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    String toolName = (String) data.getOrDefault("toolName", "");
    Map<String, Object> inputs = (Map<String, Object>) data.getOrDefault("inputs", Map.of());

    if (toolName.isBlank()) {
      throw new IllegalArgumentException("Tool node: toolName is required");
    }

    // 解析输入参数（支持变量引用 ${nodeId.key} 或 {nodeId, outputKey}）
    Map<String, Object> resolvedInputs = new HashMap<>();
    for (Map.Entry<String, Object> entry : inputs.entrySet()) {
      Object val = entry.getValue();
      if (val instanceof Map<?, ?> m) {
        resolvedInputs.put(entry.getKey(), resolveSelector((Map<String, Object>) m, vars));
      } else if (val instanceof String s) {
        resolvedInputs.put(entry.getKey(), vars.resolve(s));
      } else {
        resolvedInputs.put(entry.getKey(), val);
      }
    }

    // 调用 Skill
    Map<String, Object> result = skillRegistry.invoke(toolName, "latest", resolvedInputs);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("result", result);
    outputs.putAll(result);

    log.info(
        "[ToolNode][{}][tool={} inputs={}]", context.nodeId(), toolName, resolvedInputs.keySet());
    return NodeResult.of(outputs);
  }

  private Object resolveSelector(Map<String, Object> selector, VariablePool vars) {
    String nodeId = (String) selector.getOrDefault("nodeId", "");
    String outputKey = (String) selector.getOrDefault("outputKey", "");
    if (nodeId.isBlank() || outputKey.isBlank()) return "";
    Object value = vars.get(nodeId, outputKey);
    return value != null ? value : "";
  }
}
