package com.ai.api.agent.controller;

import com.ai.api.agent.entity.AgentTrace;
import com.ai.api.agent.trace.TraceService;
import com.ai.api.common.result.Result;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trace 回放、思维链重建与性能聚合 REST API（add-agent-runtime，add-agent-runner-pipeline 扩展）。
 *
 * <ul>
 *   <li>GET /api/v1/agent/trace/{runtimeId}/replay：按 stepIndex 升序返回完整 trace 时间轴
 *   <li>GET /api/v1/agent/trace/{runtimeId}/chain：按 trace 完整重建 Agent 思维链（分页可选）
 *   <li>GET /api/v1/agent/trace/{runtimeId}/metrics：聚合 latency / token 成本
 * </ul>
 *
 * @author add-agent-runtime
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/trace")
@RequiredArgsConstructor
public class TraceController {

  private final TraceService traceService;

  @GetMapping("/{runtimeId}/replay")
  public Result<List<AgentTrace>> replay(@PathVariable Long runtimeId) {
    return Result.success(traceService.replay(runtimeId));
  }

  @GetMapping("/{runtimeId}/chain")
  public Result<List<Map<String, Object>>> chain(
      @PathVariable Long runtimeId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    if (page != null && size != null) {
      return Result.success(traceService.rebuildChain(runtimeId, page, size));
    }
    return Result.success(traceService.rebuildChain(runtimeId));
  }

  @GetMapping("/{runtimeId}/metrics")
  public Result<Map<String, Object>> metrics(@PathVariable Long runtimeId) {
    return Result.success(traceService.metrics(runtimeId));
  }

  @GetMapping("/parent/{parentStepId}")
  public Result<List<AgentTrace>> listByParent(@PathVariable Long parentStepId) {
    return Result.success(traceService.listByParentStep(parentStepId));
  }
}
