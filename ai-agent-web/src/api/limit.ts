import { get, post, put, del } from '@/utils/request'

/** Token 限额 / 限流配置响应 */
export interface TokenLimitConfigResp {
  id: number
  scope: string
  targetId: number
  tokenQuota: number
  quotaPeriod: string
  rateLimitPerMinute: number
  rateLimitPerDay: number
  status: string
  remark: string | null
  createTime: string
  updateTime: string
}

/** 新增配置请求 */
export interface TokenLimitCreateReq {
  scope?: string
  targetId?: number
  tokenQuota?: number
  quotaPeriod?: string
  rateLimitPerMinute?: number
  rateLimitPerDay?: number
  status?: string
  remark?: string
}

/** 更新配置请求 */
export type TokenLimitUpdateReq = Omit<TokenLimitCreateReq, 'scope' | 'targetId'>

/** 分页结果 */
export interface TokenLimitPageResult {
  list: TokenLimitConfigResp[]
  total: number
}

export function listTokenLimits(params: {
  scope?: string
  targetId?: number
  page?: number
  size?: number
}) {
  return get<TokenLimitPageResult>('/limits', params)
}

export function createTokenLimit(req: TokenLimitCreateReq) {
  return post<TokenLimitConfigResp>('/limits', req)
}

export function updateTokenLimit(id: number, req: TokenLimitUpdateReq) {
  return put<TokenLimitConfigResp>(`/limits/${id}`, req)
}

export function deleteTokenLimit(id: number) {
  return del(`/limits/${id}`)
}
