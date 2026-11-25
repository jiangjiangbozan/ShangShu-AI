/** AI 应用管理 API
 *
 * 对应后端 AiApplicationController（/api/v1/apps）：
 * - 内部管理 CRUD + 发布（JWT 鉴权）
 * - 统一调用 POST /app/{appKey}/chat（API Key 鉴权，OpenAI Chat Completions 兼容响应）
 */
import { del, get, post, put } from '@/utils/request'

/** AI 应用类型 */
export type AppType = 'chat' | 'rag' | 'agent' | 'workflow'

/** AI 应用实体（后端 AiApplication） */
export interface AiApp {
  id: number
  name: string
  /** chat / rag / agent / workflow */
  type: AppType
  /** 类型相关配置：rag={kbId,topK} agent={agentConfigId} workflow={workflowId,inputs} */
  configJson: string
  /** 发布后非空（哈希，仅用于判断是否已发布） */
  apiKeyHash: string | null
  description: string | null
  enabled: boolean | number
  createTime: string
  updateTime: string
}

/** MyBatis-Plus Page 序列化结构 */
export interface AppPage {
  records: AiApp[]
  total: number
}

/** 分页查询 AI 应用 */
export function listApps(page = 1, size = 20) {
  return get<AppPage>('/apps', { page, size })
}

/** 应用详情 */
export function getApp(id: number) {
  return get<AiApp>(`/apps/${id}`)
}

/** 应用创建/编辑载荷 */
export interface AppPayload {
  name: string
  type: AppType
  configJson: string
  description?: string
}

/** 创建应用 */
export function createApp(payload: AppPayload) {
  return post<AiApp>('/apps', payload)
}

/** 更新应用 */
export function updateApp(id: number, payload: AppPayload) {
  return put<AiApp>(`/apps/${id}`, payload)
}

/** 删除应用 */
export function deleteApp(id: number) {
  return del<void>(`/apps/${id}`)
}

/** 发布应用：生成 API Key（明文仅本次响应返回一次） */
export function publishApp(id: number) {
  return post<{ apiKey: string; message: string }>(`/apps/${id}/publish`)
}

/** OpenAI 兼容聊天请求体 */
export interface AppChatReq {
  message?: string
  messages?: { role: string; content: string }[]
  history?: { role: string; content: string }[]
}

/** OpenAI Chat Completions 兼容响应（后端直接返回该结构，无 Result 包裹） */
export interface OpenAIChatResp {
  id: string
  model: string
  choices: { index: number; message: { role: string; content: string }; finish_reason: string }[]
  error?: { message: string; type: string }
}

/**
 * 统一调用入口：POST /api/v1/app/{appKey}/chat。
 *
 * 注意：该端点返回 OpenAI 兼容结构而非 Result<T>，request 响应拦截器对非 Result
 * 响应原样返回 AxiosResponse，因此此处取 `.data` 兜底解包。
 */
export async function chatWithApp(appKey: string, body: AppChatReq): Promise<OpenAIChatResp> {
  const resp = await post<OpenAIChatResp>(`/app/${appKey}/chat`, body)
  const maybe = resp as OpenAIChatResp & { data?: OpenAIChatResp }
  return maybe?.data?.choices ? maybe.data : (resp as OpenAIChatResp)
}
