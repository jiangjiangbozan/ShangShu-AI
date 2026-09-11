package com.ai.api.dashboard.dto;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Dashboard 首页聚合统计响应 DTO。
 *
 * <p>聚合平台核心资源计数与近 N 天 LLM 调用运行状况，供前端首页仪表盘渲染。
 *
 * @author add-dashboard
 */
@Data
@Builder
public class DashboardSummaryRespDTO {

  /** 各类资源计数（key 见前端约定：sessions/messages/models/knowledgeBases/documents/...） */
  private Map<String, Long> counts;

  /** 近 N 天 LLM 调用统计 */
  private LlmStats llm;

  /** 各模型调用分布（modelId -> cnt） */
  private List<Map<String, Object>> modelDistribution;

  /** 近 N 天每日调用趋势（day -> cnt） */
  private List<Map<String, Object>> dailyTrend;

  /** 近 N 天每日 Token 消耗趋势（day -> tokens） */
  private List<Map<String, Object>> dailyTokenTrend;

  /** Agent 运行统计 */
  private AgentStats agent;

  /** 最近异常告警 */
  private List<AlertItem> recentAlerts;

  /** LLM 调用统计 */
  @Data
  @Builder
  public static class LlmStats {
    private long total;
    private long success;
    private long failed;
    private long totalTokens;
    private long avgLatencyMs;
    private double successRate;
  }

  /** Agent 运行统计 */
  @Data
  @Builder
  public static class AgentStats {
    private long total;
    private long success;
    private long failed;
    private long running;
    private double successRate;
  }

  /** 异常告警条目 */
  @Data
  @Builder
  public static class AlertItem {
    private String type;
    private String message;
    private String time;
  }
}
