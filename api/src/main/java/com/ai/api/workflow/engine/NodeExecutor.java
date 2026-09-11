package com.ai.api.workflow.engine;

/**
 * 节点执行器接口。
 *
 * <p>每种节点类型实现此接口，执行具体逻辑并返回结果。
 */
public interface NodeExecutor {

  /** 支持的节点类型（对应前端 DSL NodeType 枚举值）。 */
  String type();

  /** 执行节点。 */
  NodeResult execute(NodeContext context) throws Exception;
}
