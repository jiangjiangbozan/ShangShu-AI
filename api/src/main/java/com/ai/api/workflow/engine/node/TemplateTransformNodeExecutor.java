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
 * 模板转换节点执行器。
 *
 * <p>将模板字符串中的 ${nodeId.key} 替换为实际值，输出转换后的文本。
 */
@Slf4j
@Component
public class TemplateTransformNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "template-transform";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) throws Exception {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    String template = (String) data.getOrDefault("template", "");

    // 变量插值
    Object resolved = vars.resolve(template);
    String output = resolved != null ? String.valueOf(resolved) : "";

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("output", output);

    log.info("[TemplateTransformNode][{}][output length={}]", context.nodeId(), output.length());
    return NodeResult.of(outputs);
  }
}
