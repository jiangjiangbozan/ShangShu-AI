import { get, post, put, del } from '@/utils/request'

/** Prompt 模板分类 */
export type PromptCategory = 'chat' | 'reasoning' | 'planning' | 'tool_calling' | 'reflection'

/** Prompt 模板响应 */
export interface PromptTemplateResp {
  id: number
  name: string
  category: PromptCategory
  content: string
  variables: string[]
  modelId: string | null
  isActive: boolean
  version: number
  description: string | null
  createdBy: string | null
  createdAt: string
  updatedAt: string
}

/** Prompt 模板请求 */
export interface PromptTemplateReq {
  name: string
  category?: PromptCategory
  content: string
  variables?: string[]
  modelId?: string
  isActive?: boolean
  description?: string
}

/** 分页结果 */
export interface PromptPageResult {
  list: PromptTemplateResp[]
  total: number
}

/** 分类标签映射 */
export const CATEGORY_LABELS: Record<PromptCategory, string> = {
  chat: '对话',
  reasoning: '推理',
  planning: '规划',
  tool_calling: '工具调用',
  reflection: '反思',
}

export function listPromptTemplates(params: {
  keyword?: string
  category?: string
  page?: number
  size?: number
}) {
  return get<PromptPageResult>('/prompt-templates', params)
}

export function getPromptTemplate(id: number) {
  return get<PromptTemplateResp>(`/prompt-templates/${id}`)
}

export function createPromptTemplate(req: PromptTemplateReq) {
  return post<PromptTemplateResp>('/prompt-templates', req)
}

export function updatePromptTemplate(id: number, req: PromptTemplateReq) {
  return put<PromptTemplateResp>(`/prompt-templates/${id}`, req)
}

export function createNewVersion(id: number) {
  return post<PromptTemplateResp>(`/prompt-templates/${id}/new-version`)
}

export function deletePromptTemplate(id: number) {
  return del(`/prompt-templates/${id}`)
}
