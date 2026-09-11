/** 标注问答管理 API
 *
 * 对应后端 AnnotationController（/api/v1/annotations）。
 * 借鉴 Dify Annotation Reply 功能：用户可标注 Q&A 对，
 * 系统在对话中命中相似问题时直接返回标注答案，减少 LLM 调用。
 */
import { del, get, post, put } from '@/utils/request'

/** 标注项响应 */
export interface AnnotationResp {
  id: number
  messageId: number | null
  question: string
  answer: string
  hitCount: number
  enabled: number
  createTime: string
  updateTime: string
}

/** 分页查询标注列表 */
export function listAnnotations(keyword?: string, page = 1, size = 20) {
  return get<{ list: AnnotationResp[]; total: number }>('/annotations', {
    ...(keyword ? { keyword } : {}),
    page,
    size,
  })
}

/** 新增标注 */
export function createAnnotation(question: string, answer: string, messageId?: number) {
  return post<AnnotationResp>('/annotations', { question, answer, messageId })
}

/** 编辑标注 */
export function updateAnnotation(id: number, question: string, answer: string) {
  return put<AnnotationResp>(`/annotations/${id}`, { question, answer })
}

/** 删除标注 */
export function deleteAnnotation(id: number) {
  return del<void>(`/annotations/${id}`)
}

/** 批量删除标注 */
export function batchDeleteAnnotations(ids: number[]) {
  return del<void>('/annotations/batch', { data: ids })
}

/** 启用/禁用标注 */
export function toggleAnnotation(id: number, enabled: boolean) {
  return put<void>(`/annotations/${id}/toggle`, { enabled })
}
