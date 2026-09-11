package com.ai.api.dashboard.controller;

import com.ai.api.agent.entity.AgentRuntime;
import com.ai.api.agent.mapper.AgentRuntimeMapper;
import com.ai.api.chat.mapper.ChatMessageMapper;
import com.ai.api.chat.mapper.ChatSessionMapper;
import com.ai.api.common.result.Result;
import com.ai.api.dashboard.dto.DashboardSummaryRespDTO;
import com.ai.api.dashboard.dto.DashboardSummaryRespDTO.AgentStats;
import com.ai.api.dashboard.dto.DashboardSummaryRespDTO.AlertItem;
import com.ai.api.data.entity.SysLlmLog;
import com.ai.api.data.mapper.SysLlmLogMapper;
import com.ai.api.data.mapper.SysModelConfigMapper;
import com.ai.api.identity.mapper.SystemUserMapper;
import com.ai.api.knowledge.mapper.KnowledgeBaseMapper;
import com.ai.api.knowledge.mapper.KnowledgeDocumentMapper;
import com.ai.api.prompt.mapper.PromptTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard 首页聚合统计 Controller。
 *
 * <p>聚合平台各模块资源计数（对话/模型/知识库/Prompt/Agent/用户）与近 N 天 LLM 调用运行状况， 供 Web 端首页仪表盘（DashboardView）一次性拉取渲染。
 *
 * @author add-dashboard
 */
@Slf4j
@Tag(name = "Dashboard 首页")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final ChatSessionMapper chatSessionMapper;
  private final ChatMessageMapper chatMessageMapper;
  private final SysModelConfigMapper modelConfigMapper;
  private final KnowledgeBaseMapper knowledgeBaseMapper;
  private final KnowledgeDocumentMapper documentMapper;
  private final PromptTemplateMapper promptTemplateMapper;
  private final AgentRuntimeMapper agentRuntimeMapper;
  private final SystemUserMapper systemUserMapper;
  private final SysLlmLogMapper llmLogMapper;

  /** 首页聚合统计。 */
  @Operation(summary = "首页聚合统计")
  @GetMapping("/summary")
  public Result<DashboardSummaryRespDTO> summary(@RequestParam(defaultValue = "7") int days) {
    int n = Math.clamp(days, 1, 90);

    Map<String, Long> counts = new LinkedHashMap<>();
    counts.put("sessions", chatSessionMapper.selectCount(null));
    counts.put("messages", chatMessageMapper.selectCount(null));
    counts.put("models", modelConfigMapper.selectCount(null));
    counts.put("knowledgeBases", knowledgeBaseMapper.selectCount(null));
    counts.put("documents", documentMapper.selectCount(null));
    counts.put("promptTemplates", promptTemplateMapper.selectCount(null));
    counts.put("agentRuntimes", agentRuntimeMapper.selectCount(null));
    counts.put("users", systemUserMapper.selectCount(null));

    // 近 N 天 LLM 调用统计
    LocalDateTime start = LocalDate.now().minusDays(n).atStartOfDay();
    LocalDateTime end = LocalDateTime.now();

    QueryWrapper<SysLlmLog> base = new QueryWrapper<>();
    base.ge("create_time", start).le("create_time", end);

    Long total = llmLogMapper.selectCount(base);
    QueryWrapper<SysLlmLog> successW = base.clone();
    successW.eq("status", "success");
    Long success = llmLogMapper.selectCount(successW);
    QueryWrapper<SysLlmLog> failedW = base.clone();
    failedW.eq("status", "failed");
    Long failed = llmLogMapper.selectCount(failedW);

    QueryWrapper<SysLlmLog> tokenW = base.clone();
    tokenW.select("total_tokens", "latency_ms");
    List<SysLlmLog> logs = llmLogMapper.selectList(tokenW);
    long totalTokens =
        logs.stream().mapToLong(l -> l.getTotalTokens() != null ? l.getTotalTokens() : 0).sum();
    long avgLatency =
        logs.isEmpty()
            ? 0
            : (long)
                logs.stream()
                    .mapToLong(l -> l.getLatencyMs() != null ? l.getLatencyMs() : 0)
                    .average()
                    .orElse(0);
    double successRate = total == 0 ? 0 : (double) success / total;

    // 各模型调用分布
    List<Map<String, Object>> modelDistribution =
        llmLogMapper.selectMaps(
            new QueryWrapper<SysLlmLog>()
                .select("model_id AS modelId, COUNT(*) AS cnt")
                .ge("create_time", start)
                .le("create_time", end)
                .groupBy("model_id")
                .orderByDesc("cnt"));

    // 每日调用趋势
    List<Map<String, Object>> dailyTrend =
        llmLogMapper.selectMaps(
            new QueryWrapper<SysLlmLog>()
                .select("DATE(create_time) AS day, COUNT(*) AS cnt")
                .ge("create_time", start)
                .le("create_time", end)
                .groupBy("day")
                .orderByAsc("day"));

    // 每日 Token 消耗趋势
    List<Map<String, Object>> dailyTokenTrend =
        llmLogMapper.selectMaps(
            new QueryWrapper<SysLlmLog>()
                .select("DATE(create_time) AS day, IFNULL(SUM(total_tokens),0) AS tokens")
                .ge("create_time", start)
                .le("create_time", end)
                .groupBy("day")
                .orderByAsc("day"));

    // Agent 运行统计
    long agentTotal =
        agentRuntimeMapper.selectCount(
            new QueryWrapper<AgentRuntime>().ge("create_time", start).le("create_time", end));
    long agentSuccess =
        agentRuntimeMapper.selectCount(
            new QueryWrapper<AgentRuntime>()
                .ge("create_time", start)
                .le("create_time", end)
                .eq("status", "done"));
    long agentFailed =
        agentRuntimeMapper.selectCount(
            new QueryWrapper<AgentRuntime>()
                .ge("create_time", start)
                .le("create_time", end)
                .eq("status", "failed"));
    long agentRunning =
        agentRuntimeMapper.selectCount(new QueryWrapper<AgentRuntime>().eq("status", "running"));
    double agentSuccessRate = agentTotal == 0 ? 0 : (double) agentSuccess / agentTotal;
    AgentStats agentStats =
        AgentStats.builder()
            .total(agentTotal)
            .success(agentSuccess)
            .failed(agentFailed)
            .running(agentRunning)
            .successRate(Math.round(agentSuccessRate * 10000) / 10000.0)
            .build();

    // 最近异常告警（最近 24h 失败的 LLM 调用 + 失败的 Agent 运行）
    List<AlertItem> recentAlerts = new ArrayList<>();
    LocalDateTime alertStart = LocalDateTime.now().minusHours(24);
    List<SysLlmLog> failedLogs =
        llmLogMapper.selectList(
            new QueryWrapper<SysLlmLog>()
                .eq("status", "failed")
                .ge("create_time", alertStart)
                .orderByDesc("create_time")
                .last("LIMIT 5"));
    for (SysLlmLog fl : failedLogs) {
      String msg = fl.getModelId() + " 调用失败";
      if (fl.getErrorMsg() != null && !fl.getErrorMsg().isBlank()) {
        msg +=
            "："
                + (fl.getErrorMsg().length() > 60
                    ? fl.getErrorMsg().substring(0, 60) + "…"
                    : fl.getErrorMsg());
      }
      recentAlerts.add(
          AlertItem.builder()
              .type("llm_error")
              .message(msg)
              .time(fl.getCreateTime() != null ? fl.getCreateTime().toString() : "")
              .build());
    }

    DashboardSummaryRespDTO resp =
        DashboardSummaryRespDTO.builder()
            .counts(counts)
            .llm(
                DashboardSummaryRespDTO.LlmStats.builder()
                    .total(total)
                    .success(success)
                    .failed(failed)
                    .totalTokens(totalTokens)
                    .avgLatencyMs(avgLatency)
                    .successRate(Math.round(successRate * 10000) / 10000.0)
                    .build())
            .modelDistribution(modelDistribution)
            .dailyTrend(dailyTrend)
            .dailyTokenTrend(dailyTokenTrend)
            .agent(agentStats)
            .recentAlerts(recentAlerts)
            .build();
    return Result.success(resp);
  }

  /** LLM 最近调用日志（Dashboard 简版，避免跳页）。 */
  @Operation(summary = "最近 LLM 调用")
  @GetMapping("/recent-calls")
  public Result<List<SysLlmLog>> recentCalls(@RequestParam(defaultValue = "10") int size) {
    int n = Math.clamp(size, 1, 50);
    QueryWrapper<SysLlmLog> wrapper = new QueryWrapper<>();
    wrapper.orderByDesc("create_time").last("LIMIT " + n);
    return Result.success(llmLogMapper.selectList(wrapper));
  }
}
