/**
 * 对话状态管理（Pinia Composition API 风格）
 *
 * 对标 Web 端 src/stores/chat.ts，State / Getters / Actions 签名一致。
 * 关键设计：先 push 占位对象再从数组取响应式代理，解决 Vue 响应式更新延迟。
 */
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import {
  listSessions,
  createSession,
  deleteSession,
  getMessages,
  sendMessageStream,
} from '@/api/chat'
import { getStorage, setStorage, removeStorage } from '@/utils/storage'
import type { SessionResp, MessageResp, SSECallbacks } from '@/types/chat'

/** 网络搜索开关 Storage 键 */
const WEB_SEARCH_KEY = 'ai_chat_web_search'

export const useChatStore = defineStore('chat', () => {
  // ============ State ============
  /** 会话列表 */
  const sessions = ref<SessionResp[]>([])
  /** 当前会话 ID */
  const currentSessionId = ref<number | null>(null)
  /** 当前会话消息列表 */
  const messages = ref<MessageResp[]>([])
  /** 是否正在流式输出 */
  const isStreaming = ref(false)
  /** 流式控制器（RequestTask，可调用 .abort()） */
  const streamTask = ref<UniApp.RequestTask | null>(null)
  /** 加载中 */
  const loading = ref(false)
  /**
   * 会话列表是否还有更多（游标分页）。
   * 首屏加载后置为响应值；加载更多时追加到列表尾部，不再整体替换。
   */
  const sessionHasMore = ref(false)
  /** 正在加载更多会话 */
  const sessionLoadingMore = ref(false)
  /**
   * 当前会话是否还有更早的消息（游标分页）。
   * 首屏加载"最新一屏"，向上滚动时用最旧一条 messageId 作为 beforeId 加载更早消息。
   */
  const messageHasMore = ref(false)
  /** 正在加载更早消息 */
  const messageLoadingOlder = ref(false)
  /** 当前激活的知识库 ID */
  const activeKbId = ref<number | null>(null)
  /** 网络搜索增强开关 */
  const isWebSearchEnabled = ref(getStorage<string>(WEB_SEARCH_KEY) === '1')
  /** 每会话独立草稿 */
  const sessionDrafts = ref<Record<number, string>>({})

  // ============ Getters ============
  /** 当前会话 */
  const currentSession = computed(() =>
    sessions.value.find((s) => s.sessionId === currentSessionId.value) ?? null,
  )

  // ============ Actions ============

  /** 获取会话列表（首屏，游标分页重置；仅刷新列表，不影响当前对话消息） */
  async function fetchSessions(limit = 20) {
    try {
      const page = await listSessions({ limit })
      sessions.value = page.data
      sessionHasMore.value = page.hasMore
      sessionLoadingMore.value = false
    } catch {
      // 列表刷新失败不影响当前对话状态
    }
  }

  /** 加载更多会话（滚动到列表底部触发，游标追加） */
  async function loadMoreSessions(limit = 20) {
    if (sessionLoadingMore.value || !sessionHasMore.value) return
    const lastId = sessions.value[sessions.value.length - 1]?.sessionId
    if (lastId == null) return
    sessionLoadingMore.value = true
    try {
      const page = await listSessions({ lastId, limit })
      const merged = [...sessions.value]
      for (const s of page.data) {
        if (!merged.some((existing) => existing.sessionId === s.sessionId)) {
          merged.push(s)
        }
      }
      sessions.value = merged
      sessionHasMore.value = page.hasMore
    } catch {
      // 加载更多失败仅复位状态，保留已加载列表
    } finally {
      sessionLoadingMore.value = false
    }
  }

  /** 创建新会话 */
  async function createNewSession(modelId: string, title?: string) {
    const session = await createSession(modelId, title)
    sessions.value.unshift(session)
    currentSessionId.value = session.sessionId
    messages.value = []
    messageHasMore.value = false
    return session
  }

  /** 删除会话 */
  async function removeSession(sessionId: number) {
    await deleteSession(sessionId)
    sessions.value = sessions.value.filter((s) => s.sessionId !== sessionId)
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = null
      messages.value = []
      messageHasMore.value = false
    }
    delete sessionDrafts.value[sessionId]
  }

  /** 获取消息历史（首屏：最新一屏，游标分页重置） */
  async function fetchMessages(sessionId: number, limit = 50) {
    loading.value = true
    try {
      const page = await getMessages(sessionId, { limit })
      messages.value = page.data
      messageHasMore.value = page.hasMore
      messageLoadingOlder.value = false
      currentSessionId.value = sessionId
    } finally {
      loading.value = false
    }
  }

  /** 加载更早消息（向上滚动到顶部触发，追加到列表头部） */
  async function loadOlderMessages(limit = 50) {
    const sessionId = currentSessionId.value
    if (sessionId == null || messageLoadingOlder.value || !messageHasMore.value) return
    const beforeId = messages.value[0]?.messageId
    if (beforeId == null) return
    messageLoadingOlder.value = true
    try {
      const page = await getMessages(sessionId, { beforeId, limit })
      const older = page.data.filter(
        (m) => !messages.value.some((existing) => existing.messageId === m.messageId),
      )
      messages.value = [...older, ...messages.value]
      messageHasMore.value = page.hasMore
    } finally {
      messageLoadingOlder.value = false
    }
  }

  /**
   * 发送消息（SSE 流式）
   *
   * 继承 Web 端经验：先 push 占位对象再从数组取响应式代理，
   * 避免流式输出期间界面不刷新。
   */
  function sendMessage(content: string) {
    if (!currentSessionId.value) return
    if (isStreaming.value) return

    // 清空当前会话草稿
    clearDraft(currentSessionId.value)

    // 添加用户消息
    messages.value.push({
      messageId: Date.now(),
      sessionId: currentSessionId.value,
      role: 'user',
      content,
      promptTokens: 0,
      completionTokens: 0,
      totalTokens: 0,
      latencyMs: 0,
      modelId: '',
      createTime: new Date().toISOString(),
    })

    // 关键：先 push 占位对象，再从数组取响应式代理
    messages.value.push({
      messageId: Date.now() + 1,
      sessionId: currentSessionId.value,
      role: 'assistant',
      content: '',
      promptTokens: 0,
      completionTokens: 0,
      totalTokens: 0,
      latencyMs: 0,
      modelId: '',
      createTime: new Date().toISOString(),
    })
    const aiMessage = messages.value[messages.value.length - 1]!

    isStreaming.value = true

    const callbacks: SSECallbacks = {
      onMessage(chunk: string) {
        aiMessage.content += chunk
      },
      onSearch(sources: string[]) {
        aiMessage.sources = sources
      },
      onDone(data: { messageId: string; latencyMs: number }) {
        // 不修改 messageId，避免 v-for key 变化导致组件销毁重建
        aiMessage.latencyMs = data.latencyMs
        isStreaming.value = false
        streamTask.value = null
        // 延迟刷新会话列表（等待后端自动生成标题）
        const isFirstRound = messages.value.filter((m) => m.role === 'user').length === 1
        const delay = isFirstRound ? 4000 : 500
        setTimeout(() => fetchSessions(), delay)
      },
      onError(error: string) {
        aiMessage.content = `[错误] ${error}`
        isStreaming.value = false
        streamTask.value = null
      },
      onClose() {
        if (isStreaming.value) {
          isStreaming.value = false
          streamTask.value = null
        }
      },
    }

    streamTask.value = sendMessageStream(
      currentSessionId.value,
      content,
      callbacks,
      activeKbId.value ?? undefined,
      isWebSearchEnabled.value,
    )
  }

  /** 切换网络搜索增强开关 */
  function toggleWebSearch() {
    isWebSearchEnabled.value = !isWebSearchEnabled.value
    setStorage(WEB_SEARCH_KEY, isWebSearchEnabled.value ? '1' : '0')
  }

  /** 停止流式输出 */
  function stopStreaming() {
    streamTask.value?.abort()
    isStreaming.value = false
    streamTask.value = null
  }

  /** 选择会话 */
  function selectSession(sessionId: number) {
    currentSessionId.value = sessionId
  }

  /** 读取草稿 */
  function getDraft(sessionId: number): string {
    return sessionDrafts.value[sessionId] ?? ''
  }

  /** 更新草稿 */
  function setDraft(sessionId: number, text: string) {
    sessionDrafts.value[sessionId] = text
  }

  /** 清空草稿 */
  function clearDraft(sessionId: number) {
    delete sessionDrafts.value[sessionId]
  }

  return {
    // state
    sessions,
    currentSessionId,
    messages,
    isStreaming,
    streamTask,
    loading,
    sessionHasMore,
    sessionLoadingMore,
    messageHasMore,
    messageLoadingOlder,
    activeKbId,
    isWebSearchEnabled,
    sessionDrafts,
    // getters
    currentSession,
    // actions
    fetchSessions,
    loadMoreSessions,
    createNewSession,
    removeSession,
    fetchMessages,
    loadOlderMessages,
    sendMessage,
    stopStreaming,
    selectSession,
    toggleWebSearch,
    getDraft,
    setDraft,
    clearDraft,
  }
})
