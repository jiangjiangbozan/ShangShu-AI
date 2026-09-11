import { get, post } from '@/utils/request'

/** 评测记录视图 */
export interface EvalView {
  id: number
  score: number | null
  verdict: string
  note: string | null
  userId: number | null
  createTime: string | null
}

/** 运行指标 */
export interface RunMetrics {
  total_latency_ms: number
  total_token_in: number
  total_token_out: number
  step_count: number
}

/** 运行历史行 */
export interface AgentRun {
  runtimeId: number
  sessionId: number | null
  agentConfigId: number | null
  status: string
  modelId: string | null
  userId: number | null
  input: string | null
  output: string | null
  errorMessage: string | null
  createTime: string | null
  metrics: RunMetrics
  eval: EvalView | null
}

/** 运行详情 */
export interface AgentRunDetail extends AgentRun {
  traces: TraceItem[]
}

/** trace 时间轴项 */
export interface TraceItem {
  id: number
  runtimeId: number
  stepIndex: number
  type: string
  role: string | null
  content: string | null
  tokenIn: number | null
  tokenOut: number | null
  latencyMs: number | null
  createTime: string | null
}

/** 评测汇总 */
export interface AgentEvalStats {
  total_runs: number
  success_runs: number
  failed_runs: number
  success_rate: number
  avg_latency_ms: number
  total_tokens: number
  avg_tokens: number
  eval_count: number
  good_count: number
  bad_count: number
}

export interface AgentRunQuery {
  status?: string
  modelId?: string
  page?: number
  size?: number
}

export function listRuns(params: AgentRunQuery) {
  return get<{ list: AgentRun[]; total: number }>('/agent-eval/runs', params)
}

export function getRunDetail(runtimeId: number) {
  return get<AgentRunDetail>(`/agent-eval/runs/${runtimeId}`)
}

export function submitEval(runtimeId: number, body: { score?: number; verdict?: string; note?: string }) {
  return post<EvalView>(`/agent-eval/runs/${runtimeId}/eval`, body)
}

export function getEvalStats() {
  return get<AgentEvalStats>('/agent-eval/stats')
}
