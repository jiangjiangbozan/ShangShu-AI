/** API 密钥管理 API
 *
 * 对应后端 ApiKeyController（/api/v1/api-keys）。
 */
import { del, get, post, put } from '@/utils/request'

/** API 密钥响应 */
export interface ApiKeyResp {
  id: number
  name: string
  keyPrefix: string
  /** 仅创建时返回明文密钥，之后为 null */
  keyPlain: string | null
  creatorId: number | null
  lastUsedAt: string | null
  enabled: number
  createTime: string
  updateTime: string
}

/** 分页查询 API 密钥 */
export function listApiKeys(page = 1, size = 20) {
  return get<{ list: ApiKeyResp[]; total: number }>('/api-keys', { page, size })
}

/** 创建 API 密钥 */
export function createApiKey(name: string) {
  return post<ApiKeyResp>('/api-keys', { name })
}

/** 启用/禁用 API 密钥 */
export function toggleApiKey(id: number, enabled: boolean) {
  return put<void>(`/api-keys/${id}/toggle`, { enabled })
}

/** 删除 API 密钥 */
export function deleteApiKey(id: number) {
  return del<void>(`/api-keys/${id}`)
}
