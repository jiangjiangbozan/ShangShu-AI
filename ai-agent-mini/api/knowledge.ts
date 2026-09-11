/**
 * 知识库 RAG API 封装
 *
 * 对标 Web 端 src/api/knowledge.ts，接口路径完全一致。
 * 对应后端 com.ai.api.knowledge.controller.KnowledgeController。
 */
import { del, get, post } from '@/utils/request'
import { BASE_URL } from '@/utils/request'
import { getStorage } from '@/utils/storage'
import { generateTraceId } from '@/utils/trace'
import type { ApiResult } from '@/types/api'
import type {
  CreateKnowledgeBaseReq,
  KnowledgeBaseResp,
  KnowledgeDocumentDetailResp,
  KnowledgeDocumentResp,
  SearchReq,
} from '@/types/knowledge'

/** 列出知识库 */
export function listKnowledgeBases(page = 1, size = 20) {
  return get<KnowledgeBaseResp[]>('/knowledge', { page, size })
}

/** 创建知识库 */
export function createKnowledgeBase(req: CreateKnowledgeBaseReq) {
  return post<KnowledgeBaseResp>('/knowledge', req)
}

/** 测试检索 */
export function searchKnowledge(req: SearchReq) {
  return post<string[]>('/knowledge/search', req)
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

/** 删除文档 */
export function deleteDocument(id: number) {
  return del<void>(`/knowledge/documents/${id}`)
}

/** 上传文档（uni.uploadFile，multipart 字段名为 file） */
export function uploadDocument(kbId: number, filePath: string) {
  return new Promise<KnowledgeDocumentResp>((resolve, reject) => {
    const headers: Record<string, string> = {}
    headers['X-Trace-Id'] = generateTraceId()
    const accessToken = getStorage<string>('ai_auth_access_token')
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`
    const csrfToken = getStorage<string>('ai_csrf_token')
    if (csrfToken) headers['X-CSRF-Token'] = csrfToken

    uni.uploadFile({
      url: `${BASE_URL}/knowledge/${kbId}/upload`,
      filePath,
      name: 'file',
      header: headers,
      timeout: 120000, // 文档解析 + 向量化较慢
      success: (res) => {
        try {
          const data = JSON.parse(res.data as string) as ApiResult<KnowledgeDocumentResp>
          if (data.code === 0 && data.data) {
            resolve(data.data)
          } else {
            uni.showToast({ title: data.message || '上传失败', icon: 'none' })
            reject(new Error(data.message || '上传失败'))
          }
        } catch (e) {
          reject(e)
        }
      },
      fail: (err) => {
        uni.showToast({ title: err.errMsg || '上传失败', icon: 'none' })
        reject(err)
      },
    })
  })
}
