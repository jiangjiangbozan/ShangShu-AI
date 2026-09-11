package com.ai.api.agent.trace;

import com.ai.api.agent.entity.AgentTrace;
import com.ai.api.agent.mapper.AgentTraceMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Trace 回放、思维链重建与性能聚合服务（add-agent-runtime，add-agent-runner-pipeline 扩展）。
 *
 * <p>对标 Dify MessageAgentThought + MessageChain，基于 {@code agent_trace} 表提供：
 *
 * <ul>
 *   <li>{@link #replay(Long)}：按 stepIndex 升序返回完整 trace 时间轴
 *   <li>{@link #rebuildChain(Long)}：按 trace 完整重建 Agent 思维链（按 step 聚合 thought-action-observation）
 *   <li>{@link #rebuildChain(Long, int, int)}：思维链分页
 *   <li>{@link #metrics(Long)}：聚合 latency / token 成本
 * </ul>
 *
 * @author add-agent-runtime
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceService {

  private final AgentTraceMapper agentTraceMapper;
  private final ObjectMapper objectMapper;

  /** 按 step_index 升序返回 runtime 的所有 trace（用于前端时间轴回放）。 */
  public List<AgentTrace> replay(Long runtimeId) {
    return agentTraceMapper.selectByRuntimeId(runtimeId);
  }

  /** 查询子任务 trace。 */
  public List<AgentTrace> listByParentStep(Long parentStepId) {
    return agentTraceMapper.selectByParentStepId(parentStepId);
  }

  /**
   * 按 trace 完整重建 Agent 思维链。
   *
   * <p>以 step_index 为聚合键，将同一推理步的 thought / action / observation / error 合并为一条结构化记录：
   *
   * <pre>{@code
   * [
   *   {
   *     "step": 0,
   *     "thought": {"input": <prompt>, "output": <llm响应>, "tokenIn": N, "tokenOut": N, "latencyMs": N},
   *     "action": {"skillId": "...", "input": {...}, "output": {...}, "latencyMs": N},
   *     "observation": {...},
   *     "error": null
   *   }
   * ]
   * }</pre>
   *
   * @param runtimeId runtime ID
   * @return 完整结构化思维链数组（按 step 升序）
   */
  public List<Map<String, Object>> rebuildChain(Long runtimeId) {
    return groupByStep(agentTraceMapper.selectByRuntimeId(runtimeId));
  }

  /**
   * 思维链分页获取。
   *
   * @param runtimeId runtime ID
   * @param page 页码（从 1 开始）
   * @param size 每页条数
   * @return 当前页的思维链步骤数组
   */
  public List<Map<String, Object>> rebuildChain(Long runtimeId, int page, int size) {
    List<Map<String, Object>> chain = rebuildChain(runtimeId);
    int safePage = Math.max(1, page);
    int safeSize = Math.max(1, size);
    int from = (safePage - 1) * safeSize;
    if (from >= chain.size()) {
      return List.of();
    }
    return new ArrayList<>(chain.subList(from, Math.min(chain.size(), from + safeSize)));
  }

  /** 按 step_index 聚合 trace 记录为思维链数组。 */
  private List<Map<String, Object>> groupByStep(List<AgentTrace> traces) {
    Map<Integer, Map<String, Object>> steps = new LinkedHashMap<>();
    for (AgentTrace t : traces) {
      Map<String, Object> step =
          steps.computeIfAbsent(t.getStepIndex(), k -> new LinkedHashMap<>());
      step.putIfAbsent("step", t.getStepIndex());
      switch (t.getStepType() == null ? "" : t.getStepType()) {
        case "thought" -> step.put("thought", thoughtDetail(t));
        case "action" -> step.put("action", actionDetail(t));
        case "observation" -> step.put("observation", parseJson(t.getOutput()));
        case "error" -> {
          step.put("error", t.getError());
          step.put("errorLatencyMs", t.getLatencyMs());
        }
        case "resume" -> {
          Map<String, Object> resume = new HashMap<>();
          resume.put("input", parseJson(t.getInput()));
          step.put("resume", resume);
        }
        default -> {
          // 未知 step_type 原样透出
          Map<String, Object> raw = new HashMap<>();
          raw.put("stepType", t.getStepType());
          raw.put("input", parseJson(t.getInput()));
          raw.put("output", parseJson(t.getOutput()));
          step.put("raw", raw);
        }
      }
    }
    return new ArrayList<>(steps.values());
  }

  private Map<String, Object> thoughtDetail(AgentTrace t) {
    Map<String, Object> detail = new HashMap<>();
    detail.put("input", parseJson(t.getInput()));
    detail.put("output", parseJson(t.getOutput()));
    detail.put("tokenIn", t.getTokenIn());
    detail.put("tokenOut", t.getTokenOut());
    detail.put("latencyMs", t.getLatencyMs());
    return detail;
  }

  private Map<String, Object> actionDetail(AgentTrace t) {
    Map<String, Object> detail = new HashMap<>();
    detail.put("skillId", t.getSkillId());
    detail.put("input", parseJson(t.getInput()));
    detail.put("output", parseJson(t.getOutput()));
    detail.put("latencyMs", t.getLatencyMs());
    return detail;
  }

  /**
   * 聚合 runtime 的成本指标。
   *
   * @return Map 包含 total_latency_ms / total_token_in / total_token_out / step_count
   */
  public Map<String, Object> metrics(Long runtimeId) {
    Map<String, Object> raw = agentTraceMapper.selectMetricsByRuntimeId(runtimeId);
    if (raw == null) raw = new HashMap<>();

    Map<String, Object> metrics = new HashMap<>();
    metrics.put("total_latency_ms", asLong(raw.get("total_latency")));
    metrics.put("total_token_in", asLong(raw.get("total_token_in")));
    metrics.put("total_token_out", asLong(raw.get("total_token_out")));
    metrics.put("step_count", replay(runtimeId).size());
    return metrics;
  }

  private Object parseJson(String json) {
    if (json == null || json.isBlank()) return null;
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (Exception e) {
      // 非 JSON 文本原样返回
      return json;
    }
  }

  private long asLong(Object v) {
    if (v == null) return 0;
    if (v instanceof Number n) return n.longValue();
    try {
      return Long.parseLong(v.toString());
    } catch (Exception e) {
      return 0;
    }
  }
}
