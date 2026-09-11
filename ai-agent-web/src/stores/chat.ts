/**
 * 对话状态管理（Pinia Composition API 风格）
 *
 * 管理会话列表、当前会话、消息列表、流式状态。
 */
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import {
  listSessions,
  createSession,
  deleteSession,
  getMessages,
  sendMessageStream,
  type SessionResp,
  type MessageResp,
  type SSECallbacks,
} from '@/api/chat'

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
  /** 流式控制器（用于中断） */
  const streamController = ref<AbortController | null>(null)
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
  /**
   * 当前激活的知识库 ID（用于 RAG 检索增强）。
   * 由 ChatView 在路由 query 带 kbId 进入时设置；非空时下一次发送消息会走 RAG 流程。
   */
  const activeKbId = ref<number | null>(null)
  /**
   * 网络搜索增强开关（持久化到 localStorage，跨会话保留）。
   * 开启后下一次发送消息会先搜索网络并把结果注入回答上下文；
   * 后端未配置搜索服务时自动降级为普通对话。
   */
  const webSearchEnabled = ref(
    typeof localStorage !== 'undefined' && localStorage.getItem('ai_chat_web_search') === '1',
  )
  /**
   * 每个会话独立的草稿输入内容（按 sessionId 隔离）。
   * 切换会话时不会丢失未发送的输入，发送后清空对应草稿。
   * Key: sessionId, Value: 草稿文本
   */
  const sessionDrafts = ref<Record<number, string>>({})

  // ============ Getters ============
  /** 当前会话 */
  const currentSession = computed(() =>
    sessions.value.find((s) => s.sessionId === currentSessionId.value) ?? null,
  )

  // ============ Actions ============

  /** 获取会话列表（首屏，游标分页重置） */
  async function fetchSessions(limit = 20) {
    // 保存当前选中的 sessionId，避免整体替换 sessions.value 后丢失选中状态
    const prevSelectedId = currentSessionId.value
    const page = await listSessions({ limit })
    sessions.value = page.data
    sessionHasMore.value = page.hasMore
    sessionLoadingMore.value = false
    // 若之前选中的会话在刷新后已不存在（被删除或分页未包含），清空选中状态
    if (prevSelectedId !== null && !sessions.value.some((s) => s.sessionId === prevSelectedId)) {
      currentSessionId.value = null
      messages.value = []
      messageHasMore.value = false
    } else {
      currentSessionId.value = prevSelectedId
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
    // 同步清理该会话的草稿，避免内存泄漏
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

  /** 发送消息（SSE 流式） */
  function sendMessage(content: string) {
    if (!currentSessionId.value) return
    if (isStreaming.value) return

    // 发送后清空当前会话草稿
    clearDraft(currentSessionId.value)

    // 添加用户消息到列表
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

    // 关键：先 push 占位对象，再从数组取出响应式代理（reactive proxy）。
    // 直接持有原始对象再修改 content 不会触发 Vue 响应式更新，
    // 会导致流式输出期间界面不刷新，直到 onDone 触发其他响应式变化才整体显现，
    // 表现为"上一轮回答等下一轮提问后才显示"的延迟现象。
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
    // 取出数组内的响应式代理，后续修改才会触发视图更新
    const aiMessage = messages.value[messages.value.length - 1]!

    isStreaming.value = true

    const callbacks: SSECallbacks = {
      onMessage(chunk: string) {
        // 通过响应式代理修改 content，触发实时刷新
        aiMessage.content += chunk
      },
      onSearch(sources: string[]) {
        // 网络搜索来源先于消息块下发，填充到 AI 消息上供前端展示引用
        aiMessage.sources = sources
      },
      onReferences(references) {
        // 知识库 RAG 引用来源先于消息块下发，填充到 AI 消息上供前端展示引用标注
        aiMessage.references = references
      },
      onDone(data: { messageId: string; latencyMs: number }) {
        aiMessage.messageId = Date.now()
        aiMessage.latencyMs = data.latencyMs
        isStreaming.value = false
        streamController.value = null
        // 刷新会话列表以更新统计
        fetchSessions()
        // 自动命名是异步生成（后端 Mono.fromRunnable + boundedElastic），
        // 首轮问答后延迟 4 秒再刷新一次列表，确保拿到 LLM 生成的标题
        if (messages.value.filter((m) => m.role === 'user').length === 1) {
          setTimeout(() => fetchSessions(), 4000)
        }
      },
      onError(error: string) {
        aiMessage.content = `[错误] ${error}`
        isStreaming.value = false
        streamController.value = null
      },
      onClose() {
        if (isStreaming.value) {
          isStreaming.value = false
          streamController.value = null
        }
      },
    }

    streamController.value = sendMessageStream(
      currentSessionId.value,
      content,
      callbacks,
      activeKbId.value ?? undefined,
      webSearchEnabled.value,
    )
  }

  /** 切换网络搜索增强开关（持久化到 localStorage） */
  function toggleWebSearch() {
    webSearchEnabled.value = !webSearchEnabled.value
    try {
      localStorage.setItem('ai_chat_web_search', webSearchEnabled.value ? '1' : '0')
    } catch {
      // localStorage 不可用（隐私模式等）时仅本次生效
    }
  }

  /** 停止流式输出 */
  function stopStreaming() {
    streamController.value?.abort()
    isStreaming.value = false
    streamController.value = null
  }

  /** 选择会话 */
  function selectSession(sessionId: number) {
    currentSessionId.value = sessionId
  }

  // ============ 草稿管理（独立输入框） ============

  /**
   * 读取指定会话的草稿。未创建过草稿时返回空字符串。
   * @param sessionId 会话 ID
   */
  function getDraft(sessionId: number): string {
    return sessionDrafts.value[sessionId] ?? ''
  }

  /**
   * 更新指定会话的草稿。
   * @param sessionId 会话 ID
   * @param text 草稿文本
   */
  function setDraft(sessionId: number, text: string) {
    sessionDrafts.value[sessionId] = text
  }

  /**
   * 清空指定会话的草稿（发送成功后调用）。
   * @param sessionId 会话 ID
   */
  function clearDraft(sessionId: number) {
    delete sessionDrafts.value[sessionId]
  }

  return {
    // state
    sessions,
    currentSessionId,
    messages,
    isStreaming,
    streamController,
    loading,
    sessionHasMore,
    sessionLoadingMore,
    messageHasMore,
    messageLoadingOlder,
    activeKbId,
    webSearchEnabled,
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
    // 草稿管理
    getDraft,
    setDraft,
    clearDraft,
  }
})