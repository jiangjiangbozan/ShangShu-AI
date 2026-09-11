/**
 * Dashboard 首页聚合统计 API
 *
 * 对应后端 DashboardController（/api/v1/dashboard）。
 */
import { get } from '@/utils/request'

/** LLM 近 N 天运行状况 */
export interface DashboardLlmStats {
  total: number
  success: number
  failed: number
  totalTokens: number
  avgLatencyMs: number
  successRate: number
}

/** Agent 运行统计 */
export interface DashboardAgentStats {
  total: number
  success: number
  failed: number
  running: number
  successRate: number
}

/** 异常告警条目 */
export interface DashboardAlertItem {
  type: string
  message: string
  time: string
}

/** 首页聚合统计响应 */
export interface DashboardSummaryResp {
  /** 各类资源计数：sessions/messages/models/knowledgeBases/documents/promptTemplates/agentRuntimes/users */
  counts: Record<string, number>
  llm: DashboardLlmStats
  /** 各模型调用分布 */
  modelDistribution: Array<{ modelId: string; cnt: number }>
  /** 近 N 天每日调用趋势 */
  dailyTrend: Array<{ day: string; cnt: number }>
  /** 近 N 天每日 Token 消耗趋势 */
  dailyTokenTrend: Array<{ day: string; tokens: number }>
  /** Agent 运行统计 */
  agent: DashboardAgentStats
  /** 最近异常告警 */
  recentAlerts: DashboardAlertItem[]
}

/** 最近 LLM 调用日志项 */
export interface RecentLlmLog {
  id: number
  traceId: string | null
  modelId: string | null
  promptTokens: number | null
  completionTokens: number | null
  totalTokens: number | null
  latencyMs: number | null
  status: string | null
  errorMsg: string | null
  createTime: string | null
}

/** 拉取首页聚合统计 */
export function getDashboardSummary(days = 7) {
  return get<DashboardSummaryResp>('/dashboard/summary', { days })
}

/** 拉取最近 LLM 调用 */
export function getRecentCalls(size = 10) {
  return get<RecentLlmLog[]>('/dashboard/recent-calls', { size })
}
