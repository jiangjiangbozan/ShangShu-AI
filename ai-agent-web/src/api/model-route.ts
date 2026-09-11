import { get, post, put, del } from '@/utils/request'

/** 模型路由/降级策略响应 */
export interface ModelRoutePolicyResp {
  id: number
  modelId: string
  fallbackModelId: string
  priority: number
  status: string
  remark: string | null
  createTime: string
  updateTime: string
}

/** 请求 */
export interface ModelRoutePolicyReq {
  modelId?: string
  fallbackModelId?: string
  priority?: number
  status?: string
  remark?: string
}

/** 分页结果 */
export interface ModelRoutePageResult {
  list: ModelRoutePolicyResp[]
  total: number
}

export function listModelRoutes(params: { modelId?: string; page?: number; size?: number }) {
  return get<ModelRoutePageResult>('/model-routes', params)
}

export function createModelRoute(req: ModelRoutePolicyReq) {
  return post<ModelRoutePolicyResp>('/model-routes', req)
}

export function updateModelRoute(id: number, req: ModelRoutePolicyReq) {
  return put<ModelRoutePolicyResp>(`/model-routes/${id}`, req)
}

export function deleteModelRoute(id: number) {
  return del(`/model-routes/${id}`)
}
