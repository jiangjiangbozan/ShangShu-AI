package com.ai.api.agent.pipeline;

import com.ai.api.agent.pause.PauseStateService;
import com.ai.api.agent.pipeline.Layer.Phase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 人工介入 Layer（add-agent-runner-pipeline）。
 *
 * <p>处理人工介入 / 暂停场景：每步执行前检测活跃 {@link PauseState}（WAITING）。 命中时置位 {@code ctx.paused} 并注入 {@code
 * ctx.pauseState}，Runner 据此停止循环并返回 PAUSED。
 *
 * <p>恢复时由 {@code AgentRuntimeService.respond} 将人工回复回填上下文并解析 PauseState， 本层检测到无活跃暂停点后自动放行，从
 * nextStepIndex 继续执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AskHumanLayer implements Layer {

  private final PauseStateService pauseStateService;

  @Override
  public Phase phase() {
    return Phase.REQUEST;
  }

  @Override
  public int order() {
    return 5;
  }

  @Override
  public void process(PipelineContext ctx) {
    if (pauseStateService.isPaused(ctx.getRuntimeId())) {
      ctx.setPaused(true);
      ctx.setPauseState(pauseStateService.findActive(ctx.getRuntimeId()));
      log.info(
          "[AskHumanLayer][process][runtimeId({}) 检测到暂停状态 pauseType({})]",
          ctx.getRuntimeId(),
          ctx.getPauseState().getPauseType());
    }
  }
}
