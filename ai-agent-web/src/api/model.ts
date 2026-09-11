/**
 * 模型相关 API 封装
 *
 * 对应后端 com.ai.api.model.controller.ModelPingController
 */
import { post } from '@/utils/request'

/** 模型连通性测试请求 */
export interface ModelPingReq {
  /** 模型 ID，如 deepseek-chat / qwen-plus / qwen2.5:7b */
  modelId: string
  /** 测试提示词 */
  prompt: string
}

/** 模型连通性测试结果 */
export interface ModelPingResult {
  /** 模型响应内容 */
  content: string
  /** 调用的模型 ID */
  modelId: string
  /** 调用耗时（毫秒） */
  latencyMs: number
  /** 输入 Token 数 */
  promptTokens: number
  /** 输出 Token 数 */
  completionTokens: number
  /** 总 Token 数 */
  totalTokens: number
}

/**
 * 模型连通性测试
 * POST /api/v1/model/ping
 */
export function pingModel(req: ModelPingReq) {
  return post<ModelPingResult>('/model/ping', req)
}
