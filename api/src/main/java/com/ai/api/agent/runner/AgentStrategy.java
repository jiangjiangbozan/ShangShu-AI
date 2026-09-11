package com.ai.api.agent.runner;

/**
 * Agent 执行策略枚举（add-agent-runner-refactor）。
 *
 * <p>用于 {@link StrategyFactory} 根据 Agent 配置动态选择执行器：
 *
 * <ul>
 *   <li>{@link #COT}：基于思考链文本解析（Thought→Action→Observation，ReAct 式），由 {@link CotAgentRunner} 实现
 *   <li>{@link #FC}：基于模型原生 Function Calling 工具调用，由 {@link FcAgentRunner} 实现
 * </ul>
 */
public enum AgentStrategy {
  COT,
  FC
}
