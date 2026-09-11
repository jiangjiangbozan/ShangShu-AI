package com.ai.api.workflow.engine;

import java.util.Map;

/**
 * 节点执行上下文。
 *
 * @param nodeId 节点 ID
 * @param nodeType 节点类型
 * @param title 节点标题
 * @param data 节点配置数据（来自 DSL）
 * @param variables 变量池（可读写上游节点输出）
 * @param inputs 本次运行的输入参数
 */
public record NodeContext(
    String nodeId,
    String nodeType,
    String title,
    Map<String, Object> data,
    VariablePool variables,
    Map<String, Object> inputs) {}
