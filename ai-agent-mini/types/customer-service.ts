/** 智能客服类型定义 */

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

/** 客服会话 */
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
