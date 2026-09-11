/** 知识库 RAG API 封装
 *
 * 对应后端 com.ai.api.knowledge.controller.KnowledgeController
 */
import { del, get, post, put } from '@/utils/request'
import request from '@/utils/request'

/** 知识库响应 */
export interface KnowledgeBaseResp {
  id: number
  name: string
  description: string | null
  ownerId: number
  embeddingModel: string
  chunkSize: number
  chunkOverlap: number
  similarityThreshold: number | null
  documentCount: number
  chunkCount: number
  status: string
  createTime: string
  updateTime: string
}

/** 文档响应 */
export interface KnowledgeDocumentResp {
  id: number
  knowledgeBaseId: number
  fileName: string
  filePath: string
  fileType: string
  fileSize: number
  contentHash: string | null
  chunkCount: number
  status: 'pending' | 'parsing' | 'vectorizing' | 'completed' | 'failed'
  errorMessage: string | null
  createTime: string
  updateTime: string
}

/** 创建知识库请求 */
export interface CreateKnowledgeBaseReq {
  name: string
  description?: string
}

/** 检索请求 */
export interface SearchReq {
  query: string
  topK?: number
}

/** 创建知识库 */
export function createKnowledgeBase(req: CreateKnowledgeBaseReq) {
  return post<KnowledgeBaseResp>('/knowledge', req)
}

/** 列出知识库 */
export function listKnowledgeBases(page = 1, size = 20) {
  return get<KnowledgeBaseResp[]>('/knowledge', { page, size })
}

/**
 * 上传文档到知识库
 *
 * 直接使用 axios 实例发起 multipart/form-data 请求，
 * 避免公共 post 封装的 application/json 默认 header 干扰 FormData 边界生成。
 */
export function uploadDocument(kbId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  // 通过 onUploadProgress 可挂接进度条；此处暂不暴露
  return request.post<unknown, KnowledgeDocumentResp>(
    `/knowledge/${kbId}/upload`,
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000, // 文档解析+向量化较慢，5min
    },
  )
}

/** 检索命中项（增强版，含相似度分数和来源信息） */
export interface SearchHitItem {
  content: string
  score: number
  documentName: string | null
  chunkIndex: number | null
}

/** 检索测试响应 */
export interface SearchTestResp {
  query: string
  hits: SearchHitItem[]
  latencyMs: number
}

/** 测试检索 */
export function searchKnowledge(req: SearchReq) {
  return post<SearchTestResp>('/knowledge/search', req)
}

/** 分片响应（文档详情页预览） */
export interface KnowledgeChunkResp {
  id: number
  knowledgeBaseId: number
  documentId: number
  chunkIndex: number
  content: string
  tokenCount: number
  vectorId: string | null
}

/** 文档详情响应（GET /documents/{id}） */
export interface KnowledgeDocumentDetailResp {
  document: KnowledgeDocumentResp
  chunkPreview: KnowledgeChunkResp[]
}

/** 列出指定知识库下的文档 */
export function listDocuments(
  kbId: number,
  params: { page?: number; size?: number; keyword?: string; status?: string } = {},
) {
  return get<KnowledgeDocumentResp[]>(`/knowledge/${kbId}/documents`, params)
}

/** 查询文档详情（含前 5 个分片预览） */
export function getDocumentDetail(id: number) {
  return get<KnowledgeDocumentDetailResp>(`/knowledge/documents/${id}`)
}

/** 查询文档全部分片（B3 分片预览/编辑） */
export function listDocumentChunks(docId: number) {
  return get<KnowledgeChunkResp[]>(`/knowledge/documents/${docId}/chunks`)
}

/** 编辑分片内容（B3，同步更新 MySQL + ES 向量） */
export function updateChunk(chunkId: number, content: string) {
  return put<KnowledgeChunkResp>(`/knowledge/chunks/${chunkId}`, { content })
}

/** 删除文档（含物理文件、ES 向量、MySQL 元数据） */
export function deleteDocument(id: number) {
  return del<void>(`/knowledge/documents/${id}`)
}

/** 重新处理文档（失败重试） */
export function reprocessDocument(id: number) {
  return post<KnowledgeDocumentResp>(`/knowledge/documents/${id}/reprocess`)
}

/** 反馈日志条目 */
export interface ChunkFeedbackLogItem {
  id: number
  chunkId: number
  userId: number
  feedbackType: string
  createTime: string
}

/** 反馈日志分页结果 */
export interface FeedbackPageResult {
  list: ChunkFeedbackLogItem[]
  total: number
}

/** 分页查询反馈日志 */
export function listFeedbackLogs(params: {
  page?: number
  size?: number
  chunkId?: number
  userId?: number
  feedbackType?: string
}) {
  return get<FeedbackPageResult>('/knowledge/feedback', params)
}
