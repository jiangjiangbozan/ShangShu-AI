package com.ai.api.agent.runner;

/**
 * AgentRunner 单步执行结果（add-agent-runner-refactor）。
 *
 * <ul>
 *   <li>{@link #CONTINUE}：继续下一步
 *   <li>{@link #DONE}：执行完成（final answer / 失败终止）
 *   <li>{@link #PAUSED}：暂停（等待人工介入或外部暂停）
 * </ul>
 */
public enum StepResult {
  CONTINUE,
  DONE,
  PAUSED
}
