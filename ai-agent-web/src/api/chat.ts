/**
 * 对话相关 API 封装
 *
 * 对应后端 com.ai.api.chat.controller.ChatController / ChatTokenController
 */
import { get, post, del } from '@/utils/request'
import { fetchEventSource } from '@microsoft/fetch-event-source'

/** 会话响应 */
export interface SessionResp {
  sessionId: number
  title: string
  modelId: string
  messageCount: number
  totalTokens: number
  createTime: string
  updateTime: string
}

/** 知识库 RAG 引用来源（对话启用 RAG 时由 SSE sources 事件填充） */
export interface RagReference {
  docId: string
  score: number
  content: string
}

/** 消息响应 */
export interface MessageResp {
  messageId: number
  sessionId: number
  role: 'user' | 'assistant' | 'system'
  content: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  latencyMs: number
  modelId: string
  createTime: string
  /** 网络搜索引用来源（webSearch 增强时由 SSE search 事件填充，可选） */
  sources?: string[]
  /** 知识库 RAG 引用来源（启用 RAG 时由 SSE sources 事件填充，可选） */
  references?: RagReference[]
}

/** Token 统计响应 */
export interface TokenStatsResp {
  period: string
  totalTokens: number
}

/**
 * 游标分页响应（Dify 式 keyset 分页）。
 *
 * 对应后端 {@code CursorPage<T>}：不返回 total，用 {@code hasMore} 标记是否还有更多数据，
 * 客户端用返回数据首/末条 ID 作为下一页游标。
 */
export interface CursorPage<T> {
  data: T[]
  limit: number
  hasMore: boolean
}

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

/** Token 用量统计 */
export function getTokenStats(period = 'today') {
  return get<TokenStatsResp>('/chat/tokens/stats', { period })
}

/** 全局检索命中记录 */
export interface ChatSearchHit {
  messageId: string
  sessionId: number
  userId?: number
  role: 'user' | 'assistant' | 'system'
  content: string
  /** 会话标题 */
  title?: string
  modelId?: string
  createTime?: number | string
  score?: number
  /** 内容高亮片段（ES 原生高亮或服务端截取的关键词上下文） */
  highlight?: string
  /** 标题高亮片段 */
  titleHighlight?: string
}

/** 全局检索结果（含分页信息） */
export interface ChatSearchResult {
  total: number
  page: number
  size: number
  hasMore: boolean
  hits: ChatSearchHit[]
}

/**
 * 全局检索对话历史。
 *
 * 跨所有会话关键词回溯历史问答，由后端 ES chat_message_log 索引提供。
 * 支持角色筛选（role）、时间范围精细化筛选。
 */
export function searchChatHistory(
  q: string,
  page = 1,
  size = 20,
  filters?: {
    userId?: number
    sessionId?: number
    role?: 'user' | 'assistant'
    startTime?: number
    endTime?: number
  },
) {
  const params: Record<string, number | string> = { q, page, size }
  if (filters?.userId != null) params.userId = filters.userId
  if (filters?.sessionId != null) params.sessionId = filters.sessionId
  if (filters?.role) params.role = filters.role
  if (filters?.startTime != null) params.startTime = filters.startTime
  if (filters?.endTime != null) params.endTime = filters.endTime
  return get<ChatSearchResult>('/chat/search', params)
}

/**
 * SSE 流式消息回调接口
 */
export interface SSECallbacks {
  /** 收到消息块 */
  onMessage: (chunk: string) => void
  /** 流式完成 */
  onDone: (data: { messageId: string; latencyMs: number }) => void
  /** 错误 */
  onError: (error: string) => void
  /** 收到网络搜索来源事件（webSearch 增强时先于消息块下发） */
  onSearch?: (sources: string[]) => void
  /** 收到知识库 RAG 引用来源事件（启用 RAG 时先于消息块下发） */
  onReferences?: (references: RagReference[]) => void
  /** 连接关闭 */
  onClose?: () => void
}

/**
 * SSE 流式发送消息
 *
 * 使用 @microsoft/fetch-event-source 支持 POST + 自定义请求头。
 * 返回 AbortController 用于中断流式响应。
 *
 * @param sessionId 会话 ID
 * @param content 用户消息
 * @param callbacks SSE 事件回调
 * @param kbId 知识库 ID（可选，非空时启用 RAG 检索增强）
 * @param webSearch 是否启用网络搜索增强（可选，服务端未配置搜索服务时自动忽略）
 */
export function sendMessageStream(
  sessionId: number,
  content: string,
  callbacks: SSECallbacks,
  kbId?: number,
  webSearch?: boolean,
): AbortController {
  const controller = new AbortController()

  // 读取认证 token
  const accessToken = localStorage.getItem('ai_auth_access_token')
  const csrfToken = (() => {
    const key = 'csrf-token='
    const cookies = document.cookie.split('; ')
    for (const c of cookies) {
      if (c.startsWith(key)) {
        try {
          return decodeURIComponent(c.slice(key.length))
        } catch {
          return c.slice(key.length)
        }
      }
    }
    return null
  })()

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`
  }
  if (csrfToken) {
    headers['X-CSRF-Token'] = csrfToken
  }

  // 若启用 RAG，将 kbId 拼到 URL query
  const url = new URL(
    `/api/v1/chat/sessions/${sessionId}/messages/stream`,
    window.location.origin,
  )
  if (kbId != null) url.searchParams.set('kbId', String(kbId))

  fetchEventSource(url.pathname + url.search, {
    method: 'POST',
    headers,
    body: JSON.stringify({ content, webSearch: webSearch === true }),
    signal: controller.signal,
    openWhenHidden: true,
    onmessage(event) {
      if (event.event === 'message') {
        callbacks.onMessage(event.data)
      } else if (event.event === 'search') {
        // 网络搜索来源事件：{"sources":["url1","url2",...]}
        try {
          const data = JSON.parse(event.data)
          callbacks.onSearch?.(Array.isArray(data.sources) ? data.sources : [])
        } catch {
          // 忽略解析失败的 search 事件，不影响消息流
        }
      } else if (event.event === 'sources') {
        // 知识库 RAG 引用来源事件：{"references":[{"docId":"...","score":0.8,"content":"..."},...]}
        try {
          const data = JSON.parse(event.data)
          callbacks.onReferences?.(Array.isArray(data.references) ? data.references : [])
        } catch {
          // 忽略解析失败的 sources 事件，不影响消息流
        }
      } else if (event.event === 'done') {
        try {
          const data = JSON.parse(event.data)
          callbacks.onDone(data)
        } catch {
          callbacks.onDone({ messageId: '', latencyMs: 0 })
        }
      } else if (event.event === 'error') {
        try {
          const data = JSON.parse(event.data)
          callbacks.onError(data.error || '未知错误')
        } catch {
          callbacks.onError(event.data)
        }
      }
    },
    onerror(err) {
      callbacks.onError(err instanceof Error ? err.message : '连接失败')
      // 不自动重连
      throw err
    },
    onclose() {
      callbacks.onClose?.()
    },
  })

  return controller
}