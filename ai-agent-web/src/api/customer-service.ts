/** 智能客服 API 封装
 *
 * 对应后端 com.ai.api.cs.controller.*
 *
 * - CustomerServiceController: 客服编排入口（意图识别 + KB 回退 + 槽位填充）
 * - SessionController: 客服会话工作台
 * - LabelController: 标注回流
 * - CsStatsController: KB 命中统计
 */
import { get, post } from '@/utils/request'

/** 意图识别结果 */
export interface IntentResult {
  intent: string
  confidence: number
  slots_needed: string[]
}

/** 槽位填充结果 */
export interface SlotFillResult {
  filled_slots: Record<string, string>
  missing_slots: string[]
  is_complete: boolean
}

/** 客服编排响应 */
export interface CustomerServiceResp {
  intent: IntentResult
  kb_answer: string | null
  slots: SlotFillResult
}

/** 客服会话扩展 */
export interface CsSession {
  id: number
  sessionId: number
  runtimeId: number | null
  status: string // ai|human|done|failed
  assignedTo: number | null
  takeoverTime: string | null
  createTime: string
  updateTime: string
}

/** 标注记录 */
export interface CsLabel {
  id: number
  sessionId: number
  labelType: string // positive|negative|neutral
  labelContent: string
  labelerId: number
  createTime: string
}

/** KB 命中统计 */
export interface KbHitStat {
  date: string
  total: number
  hit: number
  hit_rate: number
}

/** KB 命中汇总 */
export interface KbHitSummary {
  total: number
  hit: number
  hit_rate: number
  by_day: KbHitStat[]
}

/** ============ 客服编排 ============ */

/** 客服入口：编排意图识别 + KB 回退 + 槽位填充 */
export function chat(runtimeId: number, query: string) {
  return post<CustomerServiceResp>('/cs/chat', { query }, { params: { runtimeId } })
}

/** ============ 会话工作台 ============ */

/** 分页查询会话列表 */
export function listSessions(params: {
  status?: string
  assignedTo?: number
  page?: number
  size?: number
}) {
  return get<{ list: CsSession[]; total: number }>(`/cs/sessions`, params)
}

/** 查询会话详情 */
export function getSession(sessionId: number) {
  return get<CsSession>(`/cs/sessions/${sessionId}`)
}

/** 人工接管 */
export function takeoverSession(sessionId: number) {
  return post(`/cs/sessions/${sessionId}/takeover`)
}

/** ============ 标注回流 ============ */

/** 提交标注 */
export function submitLabel(
  sessionId: number,
  data: { labelType: string; content: string },
) {
  return post(`/cs/labels/${sessionId}`, data)
}

/** 查询会话所有标注 */
export function listLabels(sessionId: number) {
  return get<CsLabel[]>(`/cs/labels/${sessionId}`)
}

/** ============ KB 命中统计 ============ */

/** 按天统计 KB 命中率 */
export function kbHitStats(days = 7) {
  return get<KbHitStat[]>('/cs/stats/kb-hit', { days })
}

/** 汇总 KB 命中率 */
export function kbHitSummary(days = 7) {
  return get<KbHitSummary>('/cs/stats/kb-hit/summary', { days })
}
