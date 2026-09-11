package com.ai.api.workflow.engine.node;

import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 人工介入节点执行器。
 *
 * <p>按配置的表单字段从运行输入（inputs）中读取人工填写值；缺失时使用空值/默认值。 输出：表单字段名 → 值，以及 outputs（汇总 map）。
 */
@Slf4j
@Component
public class HumanInputNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "human-input";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) {
    Map<String, Object> data = context.data();
    Map<String, Object> inputs = context.inputs();

    List<Map<String, Object>> inputForm =
        (List<Map<String, Object>>) data.getOrDefault("inputForm", List.of());

    Map<String, Object> outputs = new HashMap<>();
    Map<String, Object> fieldValues = new HashMap<>();
    for (Map<String, Object> field : inputForm) {
      String variable = (String) field.getOrDefault("variable", "");
      if (variable.isBlank()) continue;
      Object value = inputs.get(variable);
      fieldValues.put(variable, value != null ? value : "");
    }
    outputs.put("outputs", fieldValues);
    outputs.putAll(fieldValues);

    log.info("[HumanInputNode][{}][fields={}]", context.nodeId(), fieldValues.keySet());
    return NodeResult.of(outputs);
  }
}
