/**
 * 对话相关类型定义
 *
 * 与 Web 端 api/chat.ts 导出类型完全一致。
 */

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
  /** 网络搜索引用来源（webSearch 增强时由 SSE search 事件填充） */
  sources?: string[]
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

/** SSE 流式消息回调接口 */
export interface SSECallbacks {
  /** 收到消息块 */
  onMessage: (chunk: string) => void
  /** 流式完成 */
  onDone: (data: { messageId: string; latencyMs: number }) => void
  /** 错误 */
  onError: (error: string) => void
  /** 收到网络搜索来源事件 */
  onSearch?: (sources: string[]) => void
  /** 连接关闭 */
  onClose?: () => void
}

/** 搜索命中记录 */
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
