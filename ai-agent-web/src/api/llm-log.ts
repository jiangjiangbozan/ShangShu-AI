/** LLM 调用日志 API 封装
 *
 * 对应后端 com.ai.api.model.controller.LlmLogController
 */
import { get } from '@/utils/request'

/** LLM 日志条目 */
export interface LlmLogItem {
  id: number
  traceId: string
  modelId: string
  prompt: string
  response: string
  promptTokens: number | null
  completionTokens: number | null
  totalTokens: number | null
  latencyMs: number | null
  status: string
  errorMsg: string | null
  createTime: string
}

/** 分页结果 */
export interface LlmLogPageResult {
  list: LlmLogItem[]
  total: number
}

/** 日志聚合统计 */
export interface LlmLogStats {
  total: number
  success: number
  failed: number
  totalTokens: number
  avgLatencyMs: number
}

/** 分页查询 LLM 调用日志 */
export function listLlmLogs(params: {
  page?: number
  size?: number
  modelId?: string
  status?: string
  traceId?: string
  startDate?: string
  endDate?: string
}) {
  return get<LlmLogPageResult>('/llm-logs', params)
}

/** 按 traceId 查询调用链路日志 */
export function getLlmLogByTraceId(traceId: string) {
  return get<LlmLogItem[]>(`/llm-logs/trace/${encodeURIComponent(traceId)}`)
}

/** 查询 LLM 日志聚合统计 */
export function getLlmLogStats(params?: { startDate?: string; endDate?: string }) {
  return get<LlmLogStats>('/llm-logs/stats', params)
}
