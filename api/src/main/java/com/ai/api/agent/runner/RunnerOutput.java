package com.ai.api.agent.runner;

import com.ai.api.agent.pause.PauseState;
import lombok.Builder;
import lombok.Data;

/**
 * Runner 统一输出模型（add-agent-runner-pipeline）。
 *
 * <p>两个 Runner（CoT / FC）执行结果统一封装，向上层 {@code AgentRuntimeService} 屏蔽底层执行差异。
 */
@Data
@Builder
public class RunnerOutput {

  /** 单步 / 整体执行结果。 */
  private StepResult result;

  /** 最终答案（DONE 时写入）。 */
  private String finalAnswer;

  /** 错误信息（DONE + 失败终止时写入）。 */
  private String error;

  /** 当前执行到 stepIndex（PAUSED 时为暂停点）。 */
  private Integer stepIndex;

  /** 关联 runtimeId。 */
  private Long runtimeId;

  /** 暂停详情（PAUSED 且为人工介入时非空）。 */
  private PauseState pauseState;

  public static RunnerOutput continuing(Long runtimeId, int stepIndex) {
    return RunnerOutput.builder()
        .result(StepResult.CONTINUE)
        .runtimeId(runtimeId)
        .stepIndex(stepIndex)
        .build();
  }

  public static RunnerOutput done(Long runtimeId, String finalAnswer, int stepIndex) {
    return RunnerOutput.builder()
        .result(StepResult.DONE)
        .runtimeId(runtimeId)
        .finalAnswer(finalAnswer)
        .stepIndex(stepIndex)
        .build();
  }

  public static RunnerOutput failed(Long runtimeId, String error, int stepIndex) {
    return RunnerOutput.builder()
        .result(StepResult.DONE)
        .runtimeId(runtimeId)
        .error(error)
        .stepIndex(stepIndex)
        .build();
  }

  public static RunnerOutput paused(Long runtimeId, int stepIndex, PauseState pauseState) {
    return RunnerOutput.builder()
        .result(StepResult.PAUSED)
        .runtimeId(runtimeId)
        .stepIndex(stepIndex)
        .pauseState(pauseState)
        .build();
  }
}
