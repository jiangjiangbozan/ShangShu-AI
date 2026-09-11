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
 * 直接回复节点执行器。
 *
 * <p>将上游节点输出直接作为工作流最终回复输出。 输出：answer（回复文本）。
 */
@Slf4j
@Component
public class AnswerNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "answer";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) throws Exception {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    Map<String, Object> answerSelector =
        (Map<String, Object>) data.getOrDefault("answer", Map.of());
    String srcNodeId = (String) answerSelector.getOrDefault("nodeId", "");
    String srcKey = (String) answerSelector.getOrDefault("outputKey", "");

    Object answerValue = "";
    if (!srcNodeId.isBlank() && !srcKey.isBlank()) {
      answerValue = vars.get(srcNodeId, srcKey);
    }

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("answer", answerValue != null ? String.valueOf(answerValue) : "");

    log.info(
        "[AnswerNode][{}][answer length={}]",
        context.nodeId(),
        answerValue != null ? String.valueOf(answerValue).length() : 0);
    return NodeResult.of(outputs);
  }
}
