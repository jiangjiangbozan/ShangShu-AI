/** 知识库 RAG 类型定义 */

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

/** 分片响应 */
export interface KnowledgeChunkResp {
  id: number
  knowledgeBaseId: number
  documentId: number
  chunkIndex: number
  content: string
  tokenCount: number
  vectorId: string | null
}

/** 文档详情响应 */
export interface KnowledgeDocumentDetailResp {
  document: KnowledgeDocumentResp
  chunkPreview: KnowledgeChunkResp[]
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
