/**
 * 公开应用 API
 *
 * 公开入口只使用 publicCode，二维码和分享路径中不携带 apiKey。
 * 聊天接口复用后端 SSE 事件协议：message / done / error。
 */
import { get } from '@/utils/request'
import { API_BASE_URL } from '@/utils/apiConfig'
import { generateTraceId } from '@/utils/trace'
import type { PublicApp, PublicChatCallbacks } from '@/types/publicApp'

/** 分享链接中 scene 参数的候选键 */
const SCENE_KEYS = ['scene', 'publicCode'] as const
/** 历史消息最大条数 */
const MAX_HISTORY = 12
/** SSE 请求超时（毫秒） */
const REQUEST_TIMEOUT = 120000

interface ChunkRequestTask {
  /** 小程序 chunked 响应回调 */
  onChunkReceived: (callback: (response: { data: ArrayBuffer }) => void) => void
}

interface SSEBuffer {
  /** 未解析文本 */
  buffer: string
  /** 当前事件名 */
  event: string
  /** 当前事件数据 */
  data: string
}

/** 获取扫码可见的公开应用列表 */
export function listPublicApps(): Promise<PublicApp[]> {
  return get<PublicApp[]>('/app/public')
}

/** 获取单个公开应用配置 */
export function getPublicApp(publicCode: string): Promise<PublicApp> {
  return get<PublicApp>(`/app/public/${encodeURIComponent(publicCode)}`)
}

/**
 * 从扫码结果或分享路径中提取公开标识。
 * 支持纯 publicCode、带 scene/publicCode 查询参数的 URL 或相对路径。
 */
export function parsePublicCode(value: string | undefined): string {
  const raw = (value ?? '').trim()
  if (!raw) return ''
  for (const key of SCENE_KEYS) {
    const matched = raw.match(new RegExp(`[?&]${key}=([^&]+)`))
    if (matched?.[1]) {
      return decodeURIComponent(matched[1])
    }
  }
  return raw
}

/** 以 SSE 方式向公开应用发送消息 */
export function sendPublicMessageStream(
  publicCode: string,
  message: string,
  history: Array<{ content: string; role: string }>,
  callbacks: PublicChatCallbacks,
): UniApp.RequestTask {
  const sse: SSEBuffer = { buffer: '', event: '', data: '' }
  const header: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Trace-Id': generateTraceId(),
  }
  const accessToken = uni.getStorageSync('ai_auth_access_token')
  if (accessToken) header.Authorization = `Bearer ${accessToken}`

  const task = uni.request({
    url: `${API_BASE_URL}/app/public/${encodeURIComponent(publicCode)}/stream`,
    method: 'POST',
    header,
    data: JSON.stringify({ message, history: history.slice(-MAX_HISTORY) }),
    timeout: REQUEST_TIMEOUT,
    enableChunked: true,
    success: () => callbacks.onClose?.(),
    fail: (error) => callbacks.onError?.(error.errMsg || '连接失败'),
  })

  const chunkTask = task as unknown as ChunkRequestTask
  chunkTask.onChunkReceived((response) => {
    sse.buffer += decodeArrayBuffer(response.data)
    dispatchSSEBuffer(sse, callbacks)
  })
  return task
}

/** ArrayBuffer 转 UTF-8 字符串 */
function decodeArrayBuffer(buffer: ArrayBuffer): string {
  const decoder = new TextDecoder('utf-8')
  return decoder.decode(buffer)
}

/** 按空行切分 SSE 事件，并派发完整事件 */
function dispatchSSEBuffer(sse: SSEBuffer, callbacks: PublicChatCallbacks): void {
  let delimiterIndex = sse.buffer.indexOf('\n\n')
  while (delimiterIndex >= 0) {
    const rawEvent = sse.buffer.slice(0, delimiterIndex)
    sse.buffer = sse.buffer.slice(delimiterIndex + 2)
    for (const line of rawEvent.split('\n')) {
      const normalized = line.replace(/\r$/, '')
      if (normalized.startsWith('event:')) {
        sse.event = normalized.slice(6).trim()
      } else if (normalized.startsWith('data:')) {
        sse.data += normalized.slice(5).trimStart()
      }
    }
    if (sse.event && sse.data) {
      dispatchSSEEvent(sse, callbacks)
    }
    sse.event = ''
    sse.data = ''
    delimiterIndex = sse.buffer.indexOf('\n\n')
  }
}

/** 按事件名分发 SSE 数据 */
function dispatchSSEEvent(sse: SSEBuffer, callbacks: PublicChatCallbacks): void {
  if (sse.event === 'message') {
    try {
      const payload = JSON.parse(sse.data) as { content?: string }
      callbacks.onMessage(payload.content ?? '')
    } catch {
      callbacks.onError?.('回答格式解析失败')
    }
    return
  }
  if (sse.event === 'done') {
    callbacks.onDone()
    return
  }
  if (sse.event === 'error') {
    callbacks.onError?.(sse.data)
  }
}
