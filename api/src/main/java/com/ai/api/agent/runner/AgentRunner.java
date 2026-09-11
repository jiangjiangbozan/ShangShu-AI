package com.ai.api.agent.runner;

/**
 * 统一 Agent 执行器顶层抽象（add-agent-runner-pipeline）。
 *
 * <p>约束不同执行策略的统一入口，向 {@code AgentRuntimeService} 屏蔽底层执行差异：
 *
 * <ul>
 *   <li>{@link #run(Long)}：完整执行（内部循环 step 直至 DONE / PAUSED / 出错）
 *   <li>{@link #step(Long)}：单步执行（可控粒度，供外部逐步驱动）
 *   <li>{@link #pause(Long)}：暂停（落轻量 PauseState + 状态机转移）
 *   <li>{@link #resume(Long)}：恢复（从 nextStepIndex 继续执行）
 * </ul>
 *
 * <p>两个 Runner（{@link CotAgentRunner} / {@link FcAgentRunner}）输出统一为 {@link RunnerOutput}， 由 {@link
 * StrategyFactory} 按 Agent 配置动态选择实现。
 */
public interface AgentRunner {

  /** 当前执行器对应的执行策略。 */
  AgentStrategy strategy();

  /**
   * 完整执行 Agent 任务（阻塞直至终态）。
   *
   * @param runtimeId runtime ID
   * @return 统一执行结果
   */
  RunnerOutput run(Long runtimeId);

  /**
   * 单步执行（CoT：一个 Thought→Action→Observation 循环；FC：一次完整工具循环回合）。
   *
   * @param runtimeId runtime ID
   * @return 统一执行结果（CONTINUE / DONE / PAUSED）
   */
  RunnerOutput step(Long runtimeId);

  /**
   * 暂停执行：写入轻量 PauseState（NORMAL），并触发状态机 running → paused。
   *
   * @param runtimeId runtime ID
   */
  void pause(Long runtimeId);

  /**
   * 恢复执行：清除 PauseState，并触发状态机 paused → running。
   *
   * @param runtimeId runtime ID
   */
  void resume(Long runtimeId);
}
