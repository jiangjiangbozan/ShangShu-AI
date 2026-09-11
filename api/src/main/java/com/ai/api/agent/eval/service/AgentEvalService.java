package com.ai.api.agent.eval.service;

import com.ai.api.agent.entity.AgentRuntime;
import com.ai.api.agent.eval.entity.AgentEval;
import com.ai.api.agent.eval.mapper.AgentEvalMapper;
import com.ai.api.agent.mapper.AgentRuntimeMapper;
import com.ai.api.agent.trace.TraceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Agent 评测面板（A3）：运行历史 + 人工评测 + 汇总统计。 */
@Slf4j
@Service
public class AgentEvalService {

  private final AgentRuntimeMapper runtimeMapper;
  private final AgentEvalMapper evalMapper;
  private final TraceService traceService;

  public AgentEvalService(
      AgentRuntimeMapper runtimeMapper, AgentEvalMapper evalMapper, TraceService traceService) {
    this.runtimeMapper = runtimeMapper;
    this.evalMapper = evalMapper;
    this.traceService = traceService;
  }

  /** 分页查询运行历史（附聚合指标与已评测信息）。 */
  public IPage<Map<String, Object>> listRuns(int page, int size, String status, String modelId) {
    LambdaQueryWrapper<AgentRuntime> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .eq(status != null && !status.isBlank(), AgentRuntime::getStatus, status)
        .like(modelId != null && !modelId.isBlank(), AgentRuntime::getModelId, modelId)
        .orderByDesc(AgentRuntime::getCreateTime);
    IPage<AgentRuntime> result = runtimeMapper.selectPage(Page.of(page, size), wrapper);
    return result.convert(this::toRunView);
  }

  private Map<String, Object> toRunView(AgentRuntime runtime) {
    Map<String, Object> view = new HashMap<>();
    view.put("runtimeId", runtime.getId());
    view.put("sessionId", runtime.getSessionId());
    view.put("agentConfigId", runtime.getAgentConfigId());
    view.put("status", runtime.getStatus());
    view.put("modelId", runtime.getModelId());
    view.put("userId", runtime.getUserId());
    view.put("input", extractInput(runtime));
    view.put("output", runtime.getFinalAnswer());
    view.put("errorMessage", runtime.getErrorMessage());
    view.put("createTime", runtime.getCreateTime());
    view.put("metrics", traceService.metrics(runtime.getId()));
    AgentEval eval =
        evalMapper.selectOne(
            new LambdaQueryWrapper<AgentEval>()
                .eq(AgentEval::getRuntimeId, runtime.getId())
                .last("LIMIT 1"));
    view.put("eval", eval == null ? null : toEvalView(eval));
    return view;
  }

  /** 运行详情：runtime + metrics + trace 时间轴 + 评测记录。 */
  public Map<String, Object> detail(Long runtimeId) {
    AgentRuntime runtime = runtimeMapper.selectById(runtimeId);
    if (runtime == null) {
      throw new IllegalArgumentException("运行不存在: " + runtimeId);
    }
    Map<String, Object> view = toRunView(runtime);
    view.put("traces", traceService.replay(runtimeId));
    return view;
  }

  /** 提交/更新人工评测（按 runtimeId upsert）。 */
  public AgentEval submitEval(
      Long runtimeId, Integer score, String verdict, String note, Long userId) {
    AgentRuntime runtime = runtimeMapper.selectById(runtimeId);
    if (runtime == null) {
      throw new IllegalArgumentException("运行不存在: " + runtimeId);
    }
    Map<String, Object> metrics = traceService.metrics(runtimeId);

    AgentEval existing =
        evalMapper.selectOne(
            new LambdaQueryWrapper<AgentEval>()
                .eq(AgentEval::getRuntimeId, runtimeId)
                .last("LIMIT 1"));
    boolean isNew = existing == null;
    AgentEval eval = isNew ? new AgentEval() : existing;
    eval.setRuntimeId(runtimeId);
    eval.setSessionId(runtime.getSessionId());
    eval.setAgentConfigId(runtime.getAgentConfigId());
    eval.setModelId(runtime.getModelId());
    eval.setStatus(runtime.getStatus());
    eval.setInput(extractInput(runtime));
    eval.setOutput(runtime.getFinalAnswer());
    eval.setTokens(
        metrics.containsKey("total_token_in") || metrics.containsKey("total_token_out")
            ? ((Number) metrics.getOrDefault("total_token_in", 0L)).longValue()
                + ((Number) metrics.getOrDefault("total_token_out", 0L)).longValue()
            : null);
    eval.setLatencyMs(
        (Number) metrics.getOrDefault("total_latency_ms", null) != null
            ? ((Number) metrics.get("total_latency_ms")).longValue()
            : null);
    eval.setScore(score);
    eval.setVerdict(verdict == null || verdict.isBlank() ? "none" : verdict);
    eval.setNote(note);
    if (isNew) {
      eval.setUserId(userId);
      evalMapper.insert(eval);
    } else {
      evalMapper.updateById(eval);
    }
    return eval;
  }

  /** 评测汇总统计。 */
  public Map<String, Object> stats() {
    List<AgentRuntime> all = runtimeMapper.selectList(null);
    long total = all.size();
    long success = all.stream().filter(r -> "done".equals(r.getStatus())).count();
    long failed = all.stream().filter(r -> "failed".equals(r.getStatus())).count();
    double totalLatency = 0;
    long totalTokens = 0;
    int tokenCounted = 0;
    for (AgentRuntime r : all) {
      Map<String, Object> m = traceService.metrics(r.getId());
      totalLatency += ((Number) m.getOrDefault("total_latency_ms", 0L)).longValue();
      long tin = ((Number) m.getOrDefault("total_token_in", 0L)).longValue();
      long tout = ((Number) m.getOrDefault("total_token_out", 0L)).longValue();
      if (tin > 0 || tout > 0) {
        totalTokens += tin + tout;
        tokenCounted++;
      }
    }
    List<AgentEval> evals = evalMapper.selectList(null);
    long good = evals.stream().filter(e -> "good".equals(e.getVerdict())).count();
    long bad = evals.stream().filter(e -> "bad".equals(e.getVerdict())).count();

    Map<String, Object> stat = new HashMap<>();
    stat.put("total_runs", total);
    stat.put("success_runs", success);
    stat.put("failed_runs", failed);
    stat.put("success_rate", total == 0 ? 0 : Math.round(success * 1000.0 / total) / 10.0);
    stat.put("avg_latency_ms", all.isEmpty() ? 0 : Math.round(totalLatency / all.size()));
    stat.put("total_tokens", totalTokens);
    stat.put("avg_tokens", tokenCounted == 0 ? 0 : Math.round(totalTokens * 1.0 / tokenCounted));
    stat.put("eval_count", evals.size());
    stat.put("good_count", good);
    stat.put("bad_count", bad);
    return stat;
  }

  private Map<String, Object> toEvalView(AgentEval eval) {
    Map<String, Object> view = new HashMap<>();
    view.put("id", eval.getId());
    view.put("score", eval.getScore());
    view.put("verdict", eval.getVerdict());
    view.put("note", eval.getNote());
    view.put("userId", eval.getUserId());
    view.put("createTime", eval.getCreateTime());
    return view;
  }

  /** 从 runtime 快照提取用户输入（contextSnapshot 中的首条 user 消息或直接截取）。 */
  private String extractInput(AgentRuntime runtime) {
    String snapshot = runtime.getContextSnapshot();
    if (snapshot == null || snapshot.isBlank()) {
      return null;
    }
    String input = snapshot;
    // 简化：截取前 500 字符作为输入展示
    return input.length() > 500 ? input.substring(0, 500) : input;
  }
}
