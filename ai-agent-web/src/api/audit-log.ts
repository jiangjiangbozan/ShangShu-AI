import { get, del } from '@/utils/request'

/** 操作审计日志响应 */
export interface OperationLogResp {
  id: number
  userId: number | null
  username: string | null
  module: string
  action: string
  method: string | null
  path: string | null
  ip: string | null
  params: string | null
  status: string
  errorMsg: string | null
  durationMs: number | null
  createTime: string
}

/** 分页结果 */
export interface AuditLogPageResult {
  list: OperationLogResp[]
  total: number
}

/** 查询参数 */
export interface AuditLogQuery {
  username?: string
  module?: string
  action?: string
  status?: string
  start?: string
  end?: string
  page?: number
  size?: number
}

export function listAuditLogs(params: AuditLogQuery) {
  return get<AuditLogPageResult>('/audit-logs', params)
}

export function cleanAuditLogs(days: number) {
  return del<{ cleaned: number; before: string }>(`/audit-logs?days=${days}`)
}
