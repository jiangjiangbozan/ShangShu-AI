package com.ai.api.workflow.engine;

import java.util.Map;

/**
 * 节点执行结果。
 *
 * @param outputs 节点输出（写入 VariablePool）
 * @param tokens 消耗的 Token 数（LLM 节点填写）
 * @param edgeLabel 条件分支节点的输出边标签（如 "if"/"else"），决定走哪条边；null 表示走默认边
 */
public record NodeResult(Map<String, Object> outputs, long tokens, String edgeLabel) {

  /** 简单成功结果（无 Token 消耗，无分支）。 */
  public static NodeResult of(Map<String, Object> outputs) {
    return new NodeResult(outputs, 0, null);
  }

  /** 带分支的结果。 */
  public static NodeResult of(Map<String, Object> outputs, String edgeLabel) {
    return new NodeResult(outputs, 0, edgeLabel);
  }

  /** LLM 结果（带 Token）。 */
  public static NodeResult ofLlm(Map<String, Object> outputs, long tokens) {
    return new NodeResult(outputs, tokens, null);
  }
}
