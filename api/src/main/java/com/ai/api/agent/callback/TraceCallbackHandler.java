package com.ai.api.agent.callback;

import com.ai.api.agent.trace.TraceRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Trace 回调处理器（add-agent-runner-pipeline）。
 *
 * <p>回调内部对接 {@link TraceRecorder}，自动写入 {@code agent_trace}：
 *
 * <ul>
 *   <li>SKILL_AFTER_INVOKE → action trace（skill 入参 / 出参 / 耗时）
 *   <li>SKILL_ERROR → error trace（异常信息）
 *   <li>SKILL_BEFORE_INVOKE → 不落库（避免与 AFTER 重复，供监控 / 审计等自定义处理器消费）
 * </ul>
 *
 * <p>业务代码不再需要到处调用 TraceRecorder，统一由回调管线自动埋点。 {@code stepIndex} 为空（非 Agent 编排场景）时跳过落库，避免破坏
 * (runtime_id, step_index) 唯一约束。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceCallbackHandler implements CallbackHandler {

  private final TraceRecorder traceRecorder;

  @Override
  public void onEvent(SkillCallbackContext ctx) {
    if (ctx.getStepIndex() == null) {
      return;
    }
    switch (ctx.getEvent()) {
      case SKILL_AFTER_INVOKE ->
          traceRecorder.recordAction(
              ctx.getRuntimeId(),
              ctx.getStepIndex(),
              ctx.getSkillName(),
              ctx.getInput(),
              ctx.getOutput(),
              ctx.getLatencyMs(),
              null);
      case SKILL_ERROR ->
          traceRecorder.recordError(
              ctx.getRuntimeId(),
              ctx.getStepIndex(),
              "skill invoke failed: " + ctx.getSkillName() + " - " + ctx.getError(),
              null);
      case SKILL_BEFORE_INVOKE -> {
        // 不落库，留给监控 / 审计类自定义处理器
      }
    }
  }
}
