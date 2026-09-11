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
 * 文档提取节点执行器。
 *
 * <p>从输入变量中提取文本内容，按输出格式（text / markdown）返回。 当输入为文本字符串时直接返回；当输入为文档对象/列表时做简单序列化。
 */
@Slf4j
@Component
public class DocExtractorNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "document-extractor";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    Map<String, Object> inputSelector = (Map<String, Object>) data.getOrDefault("input", Map.of());
    String outputType = (String) data.getOrDefault("outputType", "text");

    Object input = resolveSelector(inputSelector, vars);

    String content;
    if (input == null) {
      content = "";
    } else if (input instanceof String s) {
      content = s;
    } else {
      content = String.valueOf(input);
    }

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("text", content);
    outputs.put("markdown", outputType.equals("markdown") ? content : null);
    outputs.put("outputType", outputType);

    log.info(
        "[DocExtractorNode][{}][type={} length={}]",
        context.nodeId(),
        outputType,
        content.length());
    return NodeResult.of(outputs);
  }

  private Object resolveSelector(Map<String, Object> selector, VariablePool vars) {
    String nodeId = (String) selector.getOrDefault("nodeId", "");
    String outputKey = (String) selector.getOrDefault("outputKey", "");
    if (nodeId.isBlank() || outputKey.isBlank()) return null;
    return vars.get(nodeId, outputKey);
  }
}
