/**
 * 模型配置相关 API 封装
 *
 * 对应后端 com.ai.api.model.config.controller.ModelConfigController
 */
import { get, post, put, del } from '@/utils/request'

/** 模型配置响应 */
export interface ModelConfigResp {
  id: number
  provider: string
  modelId: string
  displayName: string
  apiKeyMasked: string
  baseUrl: string
  enabled: number
  parameters: string
  sortOrder: number
  createTime: string
  updateTime: string
}

/** 模型配置请求 */
export interface ModelConfigReq {
  provider: string
  modelId: string
  displayName?: string
  apiKey?: string
  baseUrl?: string
  enabled?: number
  parameters?: string
  sortOrder?: number
}

/** 连接测试响应 */
export interface ModelTestResp {
  success: boolean
  latencyMs: number
  message: string
}

/** 查询模型配置列表 */
export function listConfigs() {
  return get<ModelConfigResp[]>('/model/configs')
}

/** 创建模型配置 */
export function createConfig(req: ModelConfigReq) {
  return post<ModelConfigResp>('/model/configs', req)
}

/** 更新模型配置 */
export function updateConfig(id: number, req: ModelConfigReq) {
  return put<ModelConfigResp>(`/model/configs/${id}`, req)
}

/** 删除模型配置 */
export function deleteConfig(id: number) {
  return del<void>(`/model/configs/${id}`)
}

/** 测试模型连接 */
export function testConnection(id: number) {
  return post<ModelTestResp>(`/model/configs/${id}/test`)
}