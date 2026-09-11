package com.ai.api.agent.pipeline;

import com.ai.api.agent.pipeline.Layer.Phase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 执行管线编排器（add-agent-runner-pipeline）。
 *
 * <p>按 Phase 分组、按 order 排序，依次串联所有 Layer。Runner 只需调用 {@link #run} 驱动管线， 业务逻辑全部下沉到各
 * Layer，实现"引擎只调度、业务分层"。
 */
@Slf4j
@Component
public class AgentPipeline {

  private final Map<Phase, List<Layer>> phaseLayers = new EnumMap<>(Phase.class);

  public AgentPipeline(List<Layer> layers) {
    for (Layer layer : layers) {
      phaseLayers.computeIfAbsent(layer.phase(), k -> new ArrayList<>()).add(layer);
    }
    phaseLayers.values().forEach(list -> list.sort(Comparator.comparingInt(Layer::order)));
    log.info(
        "[AgentPipeline][init] REQUEST layers: {}, RESPONSE layers: {}",
        phaseLayers.getOrDefault(Phase.REQUEST, List.of()).size(),
        phaseLayers.getOrDefault(Phase.RESPONSE, List.of()).size());
  }

  /** 按阶段依次执行管线（同阶段按 order 升序）。 */
  public void run(Phase phase, PipelineContext ctx) {
    for (Layer layer : phaseLayers.getOrDefault(phase, List.of())) {
      layer.process(ctx);
    }
  }
}
