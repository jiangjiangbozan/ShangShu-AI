/**
 * 智能客服 API 封装
 *
 * 对标 Web 端 src/api/customer-service.ts，接口路径完全一致。
 * 对应后端 com.ai.api.cs.controller.*。
 */
import { get, post } from '@/utils/request'
import type {
  CsLabel,
  CsSession,
  CustomerServiceResp,
  KbHitSummary,
} from '@/types/customer-service'

/** ============ 客服编排 ============ */

/** 客服入口：编排意图识别 + KB 回退 + 槽位填充 */
export function csChat(runtimeId: number, query: string) {
  return post<CustomerServiceResp>(`/cs/chat?runtimeId=${runtimeId}`, { query })
}

/** ============ 会话工作台 ============ */

/** 分页查询会话列表 */
export function listCsSessions(params: {
  status?: string
  assignedTo?: number
  page?: number
  size?: number
}) {
  return get<{ list: CsSession[]; total: number }>('/cs/sessions', params)
}

/** 查询会话详情 */
export function getCsSession(sessionId: number) {
  return get<CsSession>(`/cs/sessions/${sessionId}`)
}

/** 人工接管 */
export function takeoverSession(sessionId: number) {
  return post<void>(`/cs/sessions/${sessionId}/takeover`)
}

/** ============ 标注回流 ============ */

/** 提交标注 */
export function submitLabel(sessionId: number, data: { labelType: string; content: string }) {
  return post<void>(`/cs/labels/${sessionId}`, data)
}

/** 查询会话所有标注 */
export function listLabels(sessionId: number) {
  return get<CsLabel[]>(`/cs/labels/${sessionId}`)
}

/** ============ KB 命中统计 ============ */

/** 汇总 KB 命中率 */
export function kbHitSummary(days = 7) {
  return get<KbHitSummary>('/cs/stats/kb-hit/summary', { days })
}
