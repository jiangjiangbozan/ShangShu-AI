package com.ai.api.agent.pipeline;

/**
 * 可插拔管线 Layer 抽象（add-agent-runner-pipeline）。
 *
 * <p>执行流程按 {@link Phase} 分组、按 {@link #order()} 升序串联，各 Layer 独立可替换、可自定义实现。
 *
 * <ul>
 *   <li>{@link Phase#REQUEST}：模型调用前组装（上下文注入 → 工具注册 → 提示词组装 → 暂停检测）
 *   <li>{@link Phase#RESPONSE}：模型调用后处理（输出解析 / 格式校验）
 * </ul>
 */
public interface Layer {

  /** 管线阶段。 */
  Phase phase();

  /** 组内执行顺序（升序，同阶段内越小越先执行）。 */
  int order();

  /** 处理统一上下文。 */
  void process(PipelineContext ctx);

  /** 管线阶段：REQUEST（模型调用前）/ RESPONSE（模型调用后）。 */
  enum Phase {
    REQUEST,
    RESPONSE
  }
}
