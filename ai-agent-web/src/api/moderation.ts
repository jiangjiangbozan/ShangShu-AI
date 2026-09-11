import { get, post, put, del } from '@/utils/request'

/** 内容审查配置 */
export interface ModerationConfig {
  id?: number
  name: string
  provider: string
  apiKey?: string
  apiUrl?: string
  threshold?: number
  keywords?: string
  enabled?: boolean
  remark?: string
  createTime?: string
}

export interface ModerationConfigPage {
  list: ModerationConfig[]
  total: number
}

export interface ModerationTestResult {
  flagged: boolean
  reason: string | null
}

export function listModerationConfigs(params: { name?: string; page?: number; size?: number }) {
  return get<ModerationConfigPage>('/moderation-configs', params)
}

export function createModerationConfig(config: ModerationConfig) {
  return post<ModerationConfig>('/moderation-configs', config)
}

export function updateModerationConfig(id: number, config: ModerationConfig) {
  return put<ModerationConfig>(`/moderation-configs/${id}`, config)
}

export function deleteModerationConfig(id: number) {
  return del<void>(`/moderation-configs/${id}`)
}

export function testModeration(body: {
  provider: string
  apiKey?: string
  apiUrl?: string
  threshold?: string
  keywords?: string
  text: string
}) {
  return post<ModerationTestResult>('/moderation-configs/test', body)
}
