package com.ai.api.workflow.engine.node;

import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * End 节点执行器。
 *
 * <p>End 节点不做处理，从 VariablePool 收集所有上游输出作为最终结果。
 */
@Component
public class EndNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "end";
  }

  @Override
  public NodeResult execute(NodeContext context) {
    // End 节点的输出 = 所有上游输出的汇聚
    // 在 WorkflowEngine 中会自动处理
    return NodeResult.of(Map.of());
  }
}
