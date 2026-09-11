import { get, post } from '@/utils/request'

/**
 * 文档管理增强 API（add-p3-rag-feedback-ops）。
 *
 * 对应后端 BatchDocumentController（/api/v1/documents）。
 */

/** 关联问答记录（GET /documents/{docId}/related-chats） */
export interface RelatedChatResp {
  sessionId: number
  sessionTitle: string
  hitTime: string
  querySnippet: string
  chunkContent: string
  messageId: string
  role: string
}

/** 批量删除响应 */
export interface BatchDeleteResp {
  successCount: number
  failures: { docId: number; reason: string }[]
}

/** 批量重处理响应（异步，立即返回 batchId） */
export interface BatchReprocessResp {
  batchId: string
  queuedCount: number
  skippedDocIds: number[]
  progressUrl: string
}

/** 批量重处理进度 */
export interface BatchProgressResp {
  batchId: string
  total: number
  success: number
  failed: number
  processing: number
  pending: number
  done: boolean
  failures: { docId: number; reason: string }[]
}

/** 查询文档关联问答记录 */
export function getRelatedChats(docId: number) {
  return get<RelatedChatResp[]>(`/documents/${docId}/related-chats`)
}

/** 批量删除文档 */
export function batchDeleteDocuments(docIds: number[]) {
  return post<BatchDeleteResp>('/documents/batch/delete', { docIds })
}

/** 批量重处理文档（异步） */
export function batchReprocessDocuments(docIds: number[]) {
  return post<BatchReprocessResp>('/documents/batch/reprocess', { docIds })
}

/** 查询批量重处理进度 */
export function getBatchProgress(batchId: string) {
  return get<BatchProgressResp>(`/documents/batch/reprocess/status/${batchId}`)
}
