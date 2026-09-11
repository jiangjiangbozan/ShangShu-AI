/**
 * 对话相关 API 封装
 *
 * 对标 Web 端 src/api/chat.ts，接口路径完全一致。
 * 包含 SSE 流式消息发送（使用 enableChunked + onChunkReceived）。
 */
import { get, post, del, BASE_URL } from '@/utils/request'
import { getStorage } from '@/utils/storage'
import { generateTraceId } from '@/utils/trace'
import type {
  SessionResp,
  MessageResp,
  CursorPage,
  SSECallbacks,
  ChatSearchResult,
} from '@/types/chat'

/** 创建会话 */
export function createSession(modelId: string, title?: string) {
  return post<SessionResp>('/chat/sessions', { modelId, title })
}

/**
 * 查询会话列表（游标分页）。
 *
 * 按 updateTime 倒序；首屏不传 {@code lastId}，下一页传上一页最后一条的 sessionId。
 */
export function listSessions(params: { lastId?: number; limit?: number } = {}) {
  const query: Record<string, number> = {}
  if (params.lastId != null) query.lastId = params.lastId
  if (params.limit != null) query.limit = params.limit
  return get<CursorPage<SessionResp>>('/chat/sessions', query)
}

/** 删除会话 */
export function deleteSession(sessionId: number) {
  return del<void>(`/chat/sessions/${sessionId}`)
}

/**
 * 查询会话消息历史（游标分页："先最新一屏，向上加载更早"）。
 *
 * 首屏不传 {@code beforeId} 返回最新 limit 条（时间正序）；
 * 向上滚动加载更早时，传当前已加载消息中最旧一条的 messageId。
 */
export function getMessages(
  sessionId: number,
  params: { beforeId?: number; limit?: number } = {},
) {
  const query: Record<string, number> = {}
  if (params.beforeId != null) query.beforeId = params.beforeId
  if (params.limit != null) query.limit = params.limit
  return get<CursorPage<MessageResp>>(`/chat/sessions/${sessionId}/messages`, query)
}

/** 全局检索对话历史 */
export function searchChatHistory(
  q: string,
  page = 1,
  size = 20,
  filters?: {
    sessionId?: number
    role?: 'user' | 'assistant'
    startTime?: number
    endTime?: number
  },
) {
  const params: Record<string, number | string> = { q, page, size }
  if (filters?.sessionId != null) params.sessionId = filters.sessionId
  if (filters?.role) params.role = filters.role
  if (filters?.startTime != null) params.startTime = filters.startTime
  if (filters?.endTime != null) params.endTime = filters.endTime
  return get<ChatSearchResult>('/chat/search', params)
}

/* ============ SSE 流式消息 ============ */

/** SSE 行解析缓冲区 */
interface SSEBuffer {
  /** 累积的未完整文本 */
  buffer: string
  /** 当前事件类型 */
  currentEvent: string
  /** 当前数据行 */
  currentData: string
}

/**
 * SSE 流式发送消息
 *
 * 使用微信小程序 enableChunked + onChunkReceived 实现 SSE。
 * 对标 Web 端 fetchEventSource 方案。
 *
 * @param sessionId 会话 ID
 * @param content   用户消息
 * @param callbacks SSE 事件回调
 * @param kbId      知识库 ID（可选，非空时启用 RAG 检索增强）
 * @param webSearch 是否启用网络搜索增强
 * @returns RequestTask（可调用 .abort() 中断）
 */
export function sendMessageStream(
  sessionId: number,
  content: string,
  callbacks: SSECallbacks,
  kbId?: number,
  webSearch?: boolean,
): UniApp.RequestTask {
  const accessToken = getStorage<string>('ai_auth_access_token')
  const csrfToken = getStorage<string>('ai_csrf_token')

  const header: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Trace-Id': generateTraceId(),
  }
  if (accessToken) header.Authorization = `Bearer ${accessToken}`
  if (csrfToken) header['X-CSRF-Token'] = csrfToken

  // 构建 URL
  let url = `${BASE_URL}/chat/sessions/${sessionId}/messages/stream`
  if (kbId != null) url += `?kbId=${kbId}`

  const body = JSON.stringify({ content, webSearch: webSearch === true })

  // SSE 解析缓冲区
  const sseBuffer: SSEBuffer = { buffer: '', currentEvent: '', currentData: '' }

  const task = uni.request({
    url,
    method: 'POST',
    header,
    data: body,
    timeout: 120000,
    enableChunked: true,
    success: () => {
      // 请求完成
      callbacks.onClose?.()
    },
    fail: (err) => {
      callbacks.onError(err.errMsg || '连接失败')
    },
  })

  // 监听分片数据（onChunkReceived 是 uni-app 扩展 API，标准 RequestTask 类型未声明）
  ;(task as any).onChunkReceived((res: { data: ArrayBuffer }) => {
    try {
      // ArrayBuffer → 字符串
      const text = arrayBufferToString(res.data)
      sseBuffer.buffer += text
      // 按行解析 SSE 事件
      parseSSEBuffer(sseBuffer, callbacks)
    } catch (e) {
      console.error('[SSE] 分片解析失败:', e)
    }
  })

  return task
}

/**
 * ArrayBuffer 转字符串
 */
function arrayBufferToString(buffer: ArrayBuffer): string {
  // 小程序环境支持 TextDecoder
  if (typeof TextDecoder !== 'undefined') {
    const decoder = new TextDecoder('utf-8')
    return decoder.decode(buffer)
  }
  // 降级方案：手动解码
  const bytes = new Uint8Array(buffer)
  const chunks: string[] = []
  for (let i = 0; i < bytes.length; i++) {
    chunks.push(String.fromCharCode(bytes[i]!))
  }
  return decodeURIComponent(escape(chunks.join('')))
}

/**
 * 解析 SSE 缓冲区
 *
 * SSE 协议格式：
 * event: message\n
 * data: 内容\n
 * \n
 */
function parseSSEBuffer(buf: SSEBuffer, callbacks: SSECallbacks): void {
  const lines = buf.buffer.split('\n')
  // 最后一行可能不完整，保留到下次处理
  buf.buffer = lines.pop() || ''

  for (const line of lines) {
    const trimmed = line.trim()

    // 空行 = 事件分隔符
    if (trimmed === '') {
      if (buf.currentData) {
        dispatchSSEEvent(buf.currentEvent, buf.currentData, callbacks)
      }
      buf.currentEvent = ''
      buf.currentData = ''
      continue
    }

    // 注释行
    if (trimmed.startsWith(':')) continue

    // event: xxx
    if (trimmed.startsWith('event:')) {
      buf.currentEvent = trimmed.slice(6).trim()
      continue
    }

    // data: xxx
    if (trimmed.startsWith('data:')) {
      // SSE 规范：只剥离 data: 后的一个可选前导空格，保留其余空白
      const raw = trimmed.slice(5)
      const data = raw.startsWith(' ') ? raw.slice(1) : raw
      // 多行 data 用 \n 拼接
      buf.currentData = buf.currentData ? buf.currentData + '\n' + data : data
    }
  }
}

/**
 * 分发 SSE 事件到回调
 */
function dispatchSSEEvent(event: string, data: string, callbacks: SSECallbacks): void {
  switch (event) {
    case 'message':
      callbacks.onMessage(data)
      break
    case 'search':
      try {
        const parsed = JSON.parse(data)
        callbacks.onSearch?.(Array.isArray(parsed.sources) ? parsed.sources : [])
      } catch {
        // 忽略解析失败
      }
      break
    case 'done':
      try {
        const parsed = JSON.parse(data)
        callbacks.onDone(parsed)
      } catch {
        callbacks.onDone({ messageId: '', latencyMs: 0 })
      }
      break
    case 'error':
      try {
        const parsed = JSON.parse(data)
        callbacks.onError(parsed.error || '未知错误')
      } catch {
        callbacks.onError(data)
      }
      break
  }
}
