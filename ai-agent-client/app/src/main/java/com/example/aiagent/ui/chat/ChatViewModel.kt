package com.example.aiagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.core.storage.DraftManager
import com.example.aiagent.core.storage.SearchHistoryManager
import com.example.aiagent.core.storage.WebSearchSettingsManager
import com.example.aiagent.data.model.ChatSearchHit
import com.example.aiagent.data.model.DEFAULT_MODEL_ID
import com.example.aiagent.data.model.KnowledgeBaseResp
import com.example.aiagent.data.model.MessageResp
import com.example.aiagent.data.model.ModelConfigResp
import com.example.aiagent.data.model.SessionResp
import com.example.aiagent.data.model.TokenStatsResp
import com.example.aiagent.data.repository.ChatRepository
import com.example.aiagent.data.repository.ModelRepository
import com.example.aiagent.data.repository.StreamEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** 会话列表分页大小。 */
private const val SESSION_PAGE_SIZE = 20

/** 搜索防抖间隔（毫秒）：连续输入时仅最后一次停顿后真正执行搜索。 */
private const val SEARCH_DEBOUNCE_MS = 300L

data class ChatUiState(
    val sessions: List<SessionResp> = emptyList(),
    val currentSessionId: Long? = null,
    val messages: List<MessageResp> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** 会话列表分页状态（游标分页：lastId = 上一页最后一条 sessionId） */
    val sessionLastId: Long? = null,
    val hasMoreSessions: Boolean = true,
    val loadingMoreSessions: Boolean = false,
    /** 当前会话消息分页状态（游标分页："先最新一屏，向上加载更早"） */
    val hasMoreMessages: Boolean = false,
    val loadingOlderMessages: Boolean = false,
    /** Token 统计 */
    val tokenStats: TokenStatsResp? = null,
    /** RAG 知识库激活 */
    val activeKbId: Long? = null,
    val activeKbName: String = "",
    /** 知识库列表（用于 RAG 选择器） */
    val knowledgeBases: List<KnowledgeBaseResp> = emptyList(),
    val showKbSelector: Boolean = false,
    /** 全局搜索 */
    val searchQuery: String = "",
    val searchHits: List<ChatSearchHit> = emptyList(),
    val searchTotal: Int = 0,
    val searchHasMore: Boolean = false,
    val searching: Boolean = false,
    val showSearch: Boolean = false,
    /** 搜索历史（本地持久化，最新在前） */
    val searchHistory: List<String> = emptyList(),
    /** 搜索角色筛选（null = 全部） */
    val searchRoleFilter: String? = null,
    /** 草稿内容（按 sessionId 隔离） */
    val sessionDrafts: Map<Long, String> = emptyMap(),
    /** 网络搜索增强开关（本地持久化，开启后发送消息先搜索网络注入实时信息） */
    val webSearchEnabled: Boolean = false,
    /** 引用内容（用于回复引用功能） */
    val quoteContent: String? = null,
    /** 模型列表 */
    val models: List<ModelConfigResp> = emptyList(),
    /** 当前选中的模型 ID */
    val currentModelId: String? = null,
    /** 模型选择器显示状态 */
    val showModelSelector: Boolean = false,
)

class ChatViewModel(
    private val repository: ChatRepository = ChatRepository(),
    private val modelRepository: ModelRepository = ModelRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 流式请求 Job（用于取消时停止 SSE 读取）。 */
    private var streamJob: Job? = null

    /** 搜索请求 Job（用于防抖：新输入取消上一次未执行的搜索）。 */
    private var searchJob: Job? = null

    /** 标记当前流式会话已完成（Done/Error/手动停止），防止 Closed 重复追加消息。 */
    private var streamCompleted = false

    // 本地临时消息 ID 生成器，以负数递减，避免与后端正数 ID 冲突。
    private var localIdCounter = 0L
    private fun nextLocalId(): Long = --localIdCounter

    /**
     * 生成当前时间的 ISO-8601 字符串（与后端 LocalDateTime 序列化格式一致）。
     *
     * 用于本地临时消息的 createTime，保证 mergeMessages 时可与后端消息按时间排序，
     * 避免中止的回答被错误追加到列表末尾。
     * 格式：yyyy-MM-dd'T'HH:mm:ss.SSS（字典序与时间序一致，可直接用 String 排序）
     */
    private fun nowIso(): String =
        java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"))

    /**
     * 按会话缓存消息（sessionId → messages）。
     *
     * 解决切换会话后 AI 回答丢失的问题：流式完成后 assistant 消息仅在内存 UI state 中，
     * 切换会话时 loadMessages 会覆盖 messages。此缓存在 selectSession 时保存当前消息，
     * 切换回来时先显示缓存内容，再与后端数据合并，确保不丢失。
     */
    private val messageCache = mutableMapOf<Long, List<MessageResp>>()

    init {
        // 订阅草稿变化，同步到 UI state
        viewModelScope.launch {
            DraftManager.drafts.collect { drafts ->
                _uiState.update { it.copy(sessionDrafts = drafts) }
            }
        }
        // 订阅搜索历史变化，同步到 UI state
        viewModelScope.launch {
            SearchHistoryManager.history.collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
        // 订阅网络搜索开关变化，同步到 UI state
        viewModelScope.launch {
            WebSearchSettingsManager.enabled.collect { enabled ->
                _uiState.update { it.copy(webSearchEnabled = enabled) }
            }
        }
    }

    /** 切换网络搜索增强开关（持久化到 DataStore）。 */
    fun toggleWebSearch() {
        WebSearchSettingsManager.toggle()
    }

    // ============ 会话列表（分页） ============

    fun loadSessions() {
        viewModelScope.launch {
            runCatching { repository.listSessions(null, SESSION_PAGE_SIZE) }
                .onSuccess { page ->
                    _uiState.update { state ->
                        val sessions = page.data
                        val selected = state.currentSessionId
                            ?.takeIf { id -> sessions.any { it.sessionId == id } }
                            ?: sessions.firstOrNull()?.sessionId
                        state.copy(
                            sessions = sessions,
                            currentSessionId = selected,
                            sessionLastId = sessions.lastOrNull()?.sessionId,
                            hasMoreSessions = page.hasMore,
                            loadingMoreSessions = false,
                        )
                    }
                    selectedSession()?.let { loadMessages(it) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    /** 加载更多会话（无限滚动触发，游标追加）。 */
    fun loadMoreSessions() {
        val state = _uiState.value
        if (state.loadingMoreSessions || !state.hasMoreSessions) return
        _uiState.update { it.copy(loadingMoreSessions = true) }
        viewModelScope.launch {
            runCatching { repository.listSessions(state.sessionLastId, SESSION_PAGE_SIZE) }
                .onSuccess { page ->
                    _uiState.update { s ->
                        val newSessions = page.data.filter { ns ->
                            s.sessions.none { it.sessionId == ns.sessionId }
                        }
                        s.copy(
                            sessions = s.sessions + newSessions,
                            sessionLastId = page.data.lastOrNull()?.sessionId ?: s.sessionLastId,
                            hasMoreSessions = page.hasMore,
                            loadingMoreSessions = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(loadingMoreSessions = false) }
                }
        }
    }

    fun selectSession(id: Long) {
        // 切换前缓存当前会话的消息
        val currentId = _uiState.value.currentSessionId
        if (currentId != null && currentId != id) {
            messageCache[currentId] = _uiState.value.messages
        }
        _uiState.update { it.copy(currentSessionId = id, error = null) }
        // 先用缓存内容填充 UI，避免切换时出现空白
        val cached = messageCache[id]
        if (cached != null) {
            _uiState.update { it.copy(messages = cached) }
        }
        loadMessages(id)
    }

    fun newSession() {
        val modelId = _uiState.value.currentModelId ?: DEFAULT_MODEL_ID
        viewModelScope.launch {
            runCatching { repository.createSession(modelId) }
                .onSuccess { session ->
                    _uiState.update {
                        it.copy(
                            sessions = listOf(session) + it.sessions,
                            currentSessionId = session.sessionId,
                            messages = emptyList(),
                            hasMoreMessages = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteSession(id) }
                .onSuccess {
                    _uiState.update { state ->
                        val sessions = state.sessions.filterNot { it.sessionId == id }
                        val selected = if (state.currentSessionId == id) {
                            sessions.firstOrNull()?.sessionId
                        } else {
                            state.currentSessionId
                        }
                        state.copy(
                            sessions = sessions,
                            currentSessionId = selected,
                            messages = if (state.currentSessionId == id) emptyList() else state.messages,
                            hasMoreMessages = if (state.currentSessionId == id) false else state.hasMoreMessages,
                        )
                    }
                    messageCache.remove(id)
                    DraftManager.clearDraft(id)
                    selectedSession()?.let { loadMessages(it) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    // ============ 消息发送（SSE 流式） ============

    fun sendMessage(content: String) {
        if (_uiState.value.isStreaming) return
        val text = content.trim()
        if (text.isEmpty()) return
        streamCompleted = false
        streamJob = viewModelScope.launch {
            var sessionId = _uiState.value.currentSessionId
            if (sessionId == null) {
                val modelId = _uiState.value.currentModelId ?: DEFAULT_MODEL_ID
                val created = try {
                    repository.createSession(modelId)
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                    return@launch
                }
                sessionId = created.sessionId
                _uiState.update {
                    it.copy(
                        sessions = listOf(created) + it.sessions,
                        currentSessionId = sessionId,
                    )
                }
            }

            // 发送后清空草稿
            DraftManager.clearDraft(sessionId)

            // 重置流式状态标志，避免上一轮的 streamCompleted=true 导致
            // 本轮 Closed 事件被错误跳过（例如上一轮被中止后立即发送新消息）
            streamCompleted = false

            // 处理引用内容（如果有）
            val quoteContent = _uiState.value.quoteContent
            val finalText = if (quoteContent != null) {
                "> $quoteContent\n\n$text"
            } else {
                text
            }

            val userMsg = MessageResp(
                messageId = nextLocalId(),
                sessionId = sessionId,
                role = "user",
                content = finalText,
                createTime = nowIso(),
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + userMsg,
                    isStreaming = true,
                    streamingContent = "",
                    error = null,
                    quoteContent = null, // 发送后清除引用
                )
            }

            val kbId = _uiState.value.activeKbId
            val webSearch = _uiState.value.webSearchEnabled
            // 搜索来源在流式期间暂存，Done/Closed 时填充到最终 assistant 消息
            var pendingSources: List<String> = emptyList()
            repository.chatStream(sessionId, finalText, kbId, webSearch).collect { event ->
                when (event) {
                    is StreamEvent.Chunk -> _uiState.update {
                        it.copy(streamingContent = it.streamingContent + event.text)
                    }

                    is StreamEvent.Search -> {
                        pendingSources = event.sources
                    }

                    is StreamEvent.Done -> {
                        streamCompleted = true
                        // 使用 streamingContent 构建最终 assistant 消息，确保回答持久显示
                        val content = _uiState.value.streamingContent
                        val assistant = MessageResp(
                            messageId = nextLocalId(),
                            sessionId = sessionId,
                            role = "assistant",
                            content = content,
                            latencyMs = event.latencyMs,
                            createTime = nowIso(),
                            sources = pendingSources,
                        )
                        _uiState.update {
                            it.copy(
                                messages = it.messages + assistant,
                                isStreaming = false,
                                streamingContent = "",
                            )
                        }
                        // 更新消息缓存，确保切换会话后回来不丢失
                        messageCache[sessionId] = _uiState.value.messages
                        streamJob = null
                        refreshSessions()
                    }

                    is StreamEvent.Error -> {
                        streamCompleted = true
                        val assistant = MessageResp(
                            messageId = nextLocalId(),
                            sessionId = sessionId,
                            role = "assistant",
                            content = "[错误] ${event.message}",
                            createTime = nowIso(),
                        )
                        _uiState.update {
                            it.copy(
                                messages = it.messages + assistant,
                                isStreaming = false,
                                streamingContent = "",
                                error = null,
                            )
                        }
                        messageCache[sessionId] = _uiState.value.messages
                        streamJob = null
                    }

                    StreamEvent.Closed -> {
                        // Done/Error 已处理时跳过，防止重复追加或清空已有消息
                        if (streamCompleted) {
                            _uiState.update { it.copy(isStreaming = false, streamingContent = "") }
                            streamJob = null
                            return@collect
                        }
                        streamCompleted = true
                        val partial = _uiState.value.streamingContent
                        val assistant = if (partial.isBlank()) {
                            null
                        } else {
                            MessageResp(
                                messageId = nextLocalId(),
                                sessionId = sessionId,
                                role = "assistant",
                                content = partial,
                                createTime = nowIso(),
                                sources = pendingSources,
                            )
                        }
                        _uiState.update {
                            it.copy(
                                messages = if (assistant != null) it.messages + assistant else it.messages,
                                isStreaming = false,
                                streamingContent = "",
                            )
                        }
                        messageCache[sessionId] = _uiState.value.messages
                        streamJob = null
                    }
                }
            }
        }
    }

    /** 停止流式输出。 */
    fun stopStreaming() {
        repository.stopStreaming()
        streamJob?.cancel()
        streamJob = null
        streamCompleted = true
        // 将当前已产生的 streamingContent 保存为 assistant 消息（如果有）
        val partial = _uiState.value.streamingContent
        if (partial.isNotBlank()) {
            val assistant = MessageResp(
                messageId = nextLocalId(),
                sessionId = _uiState.value.currentSessionId ?: 0,
                role = "assistant",
                content = partial,
                createTime = nowIso(),
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + assistant,
                    isStreaming = false,
                    streamingContent = "",
                )
            }
            _uiState.value.currentSessionId?.let { sid ->
                messageCache[sid] = _uiState.value.messages
            }
        } else {
            _uiState.update { it.copy(isStreaming = false, streamingContent = "") }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ============ 消息操作（借鉴 Operit） ============

    /** 设置引用内容（用于回复引用功能）。 */
    fun setQuoteContent(content: String?) {
        _uiState.update { it.copy(quoteContent = content) }
    }

    /** 清除引用内容。 */
    fun clearQuoteContent() {
        _uiState.update { it.copy(quoteContent = null) }
    }

    /** 重新生成最后一条 AI 消息。 */
    fun regenerateLastMessage() {
        val messages = _uiState.value.messages
        val lastAssistant = messages.lastOrNull { it.role == "assistant" } ?: return
        val lastUser = messages.lastOrNull { it.role == "user" && it.messageId < lastAssistant.messageId }

        lastUser?.let { userMsg ->
            // 删除最后的 AI 回答
            _uiState.update {
                it.copy(messages = it.messages.filterNot { msg -> msg.messageId == lastAssistant.messageId })
            }
            // 重新发送用户消息
            sendMessage(userMsg.content)
        }
    }

    /** 删除消息（本地删除，后端删除需要后端支持）。 */
    fun deleteMessage(message: MessageResp) {
        _uiState.update {
            it.copy(messages = it.messages.filterNot { msg -> msg.messageId == message.messageId })
        }
        // 更新缓存
        _uiState.value.currentSessionId?.let { sid ->
            messageCache[sid] = _uiState.value.messages
        }
    }

    // ============ 模型切换（借鉴 Operit 多模型设计） ============

    /** 加载模型列表。 */
    fun loadModels() {
        viewModelScope.launch {
            runCatching { modelRepository.listModels() }
                .onSuccess { models ->
                    val defaultModel = models.firstOrNull { it.isDefault }
                    _uiState.update { state ->
                        state.copy(
                            models = models,
                            currentModelId = state.currentModelId ?: defaultModel?.id,
                        )
                    }
                }
                .onFailure { /* 模型列表加载失败不影响主流程 */ }
        }
    }

    /** 切换模型选择器显示状态。 */
    fun toggleModelSelector() {
        _uiState.update { it.copy(showModelSelector = !it.showModelSelector) }
        if (!_uiState.value.showModelSelector && _uiState.value.models.isEmpty()) {
            loadModels()
        }
    }

    /** 选择模型。 */
    fun selectModel(model: ModelConfigResp) {
        _uiState.update {
            it.copy(currentModelId = model.id, showModelSelector = false)
        }
    }

    // ============ Token 统计 ============

    fun loadTokenStats(period: String = "today") {
        viewModelScope.launch {
            runCatching { repository.getTokenStats(period) }
                .onSuccess { stats ->
                    _uiState.update { it.copy(tokenStats = stats) }
                }
                .onFailure { /* Token 统计失败不影响主流程 */ }
        }
    }

    // ============ RAG 知识库 ============

    fun loadKnowledgeBases() {
        viewModelScope.launch {
            runCatching { repository.listKnowledgeBases() }
                .onSuccess { kbs ->
                    _uiState.update { it.copy(knowledgeBases = kbs) }
                }
                .onFailure { /* 知识库加载失败静默处理 */ }
        }
    }

    /** 激活 RAG 知识库。 */
    fun activateKb(kbId: Long, kbName: String) {
        _uiState.update {
            it.copy(activeKbId = kbId, activeKbName = kbName, showKbSelector = false)
        }
    }

    /** 关闭 RAG。 */
    fun deactivateKb() {
        _uiState.update { it.copy(activeKbId = null, activeKbName = "") }
    }

    fun toggleKbSelector() {
        _uiState.update { it.copy(showKbSelector = !it.showKbSelector) }
        // 每次打开都刷新列表，确保新建/变更后的知识库能及时呈现
        if (_uiState.value.showKbSelector) {
            loadKnowledgeBases()
        }
    }

    // ============ 全局搜索 ============

    fun openSearch() {
        _uiState.update { it.copy(showSearch = true, searchQuery = "", searchHits = emptyList(), searchTotal = 0, searchHasMore = false, searchRoleFilter = null) }
    }

    fun closeSearch() {
        _uiState.update { it.copy(showSearch = false, searchQuery = "", searchHits = emptyList(), searchTotal = 0, searchHasMore = false, searchRoleFilter = null) }
    }

    /** 设置搜索角色筛选，并自动重新搜索（如果已有搜索词）。 */
    fun setSearchRoleFilter(role: String?) {
        _uiState.update { it.copy(searchRoleFilter = role) }
        val query = _uiState.value.searchQuery.trim()
        if (query.isNotEmpty()) {
            searchChatHistory(query)
        }
    }

    /**
     * 搜索对话历史（带 300ms 防抖）。
     *
     * 修复残留问题：此前每输入一个字符立即发起网络请求，造成请求风暴与结果错乱。
     * 现在连续输入时仅最后一次（停顿 300ms 后）真正执行请求；搜索成功后记录到搜索历史。
     * 输入清空时同步清空结果列表，回退到历史记录展示。
     */
    fun searchChatHistory(q: String) {
        val query = q.trim()
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isEmpty()) {
            _uiState.update { it.copy(searchHits = emptyList(), searchTotal = 0, searchHasMore = false, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS.milliseconds)
            _uiState.update { it.copy(searching = true) }
            val role = _uiState.value.searchRoleFilter
            runCatching { repository.searchChatHistory(query, role = role?.ifBlank { null }) }
                .onSuccess { result ->
                    SearchHistoryManager.add(query)
                    _uiState.update { it.copy(searchHits = result.hits, searchTotal = result.total, searchHasMore = result.hasMore, searching = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(searching = false, searchHits = emptyList(), searchTotal = 0, searchHasMore = false, error = e.toUserMessage())
                    }
                }
        }
    }

    /** 删除单条搜索历史记录。 */
    fun removeSearchHistory(query: String) {
        SearchHistoryManager.remove(query)
    }

    /** 清空全部搜索历史。 */
    fun clearSearchHistory() {
        SearchHistoryManager.clear()
    }

    /** 跳转到搜索命中所在会话，并定位到对应消息。 */
    fun jumpToHit(hit: ChatSearchHit) {
        _uiState.update { it.copy(showSearch = false, searchQuery = "", searchHits = emptyList(), searchTotal = 0, searchHasMore = false) }
        if (hit.sessionId > 0) {
            selectSession(hit.sessionId)
            // 记录高亮消息 ID，供 UI 层滚动定位
            _highlightMessageId = hit.messageId.toLongOrNull()
        }
    }

    /** 搜索跳转携带的高亮消息 ID（消费后清空） */
    @Volatile
    private var _highlightMessageId: Long? = null

    /** 取出并清空高亮消息 ID（一次性消费） */
    fun consumeHighlightMessageId(): Long? {
        val id = _highlightMessageId
        _highlightMessageId = null
        return id
    }

    // ============ 草稿管理 ============

    /** 读取指定会话的草稿。 */
    fun getDraft(sessionId: Long): String = _uiState.value.sessionDrafts[sessionId] ?: ""

    /** 更新指定会话的草稿。 */
    fun setDraft(sessionId: Long, text: String) {
        DraftManager.setDraft(sessionId, text)
    }

    /** 清空指定会话的草稿。 */
    fun clearDraft(sessionId: Long) {
        DraftManager.clearDraft(sessionId)
    }

    // ============ 私有方法 ============

    private fun selectedSession(): Long? = _uiState.value.currentSessionId

    private fun loadMessages(id: Long) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = true, error = null, hasMoreMessages = false)
            }
            runCatching { repository.getMessages(id) }
                .onSuccess { page ->
                    // 合并策略：后端数据为权威来源，但保留本地缓存中后端尚未持久化的消息
                    // （如刚完成的流式回答，MQ 异步写入可能尚未完成）
                    val serverMessages = page.data
                    val localMessages = messageCache[id].orEmpty()
                    val merged = mergeMessages(localMessages, serverMessages)
                    messageCache[id] = merged
                    _uiState.update {
                        it.copy(
                            loading = false,
                            messages = merged,
                            hasMoreMessages = page.hasMore,
                        )
                    }
                }
                .onFailure { e ->
                    // 后端加载失败时，保留缓存内容（如果有），避免清空已有消息
                    val cached = messageCache[id]
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = e.toUserMessage(),
                            messages = cached ?: it.messages,
                        )
                    }
                }
        }
    }

    /** 加载更早消息（用户向上滚动到顶部触发，追加到列表头部）。 */
    fun loadOlderMessages() {
        val state = _uiState.value
        val sessionId = state.currentSessionId ?: return
        if (state.loadingOlderMessages || !state.hasMoreMessages) return
        val beforeId = state.messages.firstOrNull()?.messageId ?: return
        _uiState.update { it.copy(loadingOlderMessages = true) }
        viewModelScope.launch {
            runCatching { repository.getMessages(sessionId, beforeId) }
                .onSuccess { page ->
                    _uiState.update { s ->
                        val older = page.data.filter { om ->
                            s.messages.none { it.messageId == om.messageId }
                        }
                        val merged = older + s.messages
                        messageCache[sessionId] = merged
                        s.copy(
                            messages = merged,
                            hasMoreMessages = page.hasMore,
                            loadingOlderMessages = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(loadingOlderMessages = false) }
                }
        }
    }

    /**
     * 合并本地缓存消息与后端消息，严格按 createTime 升序排列。
     *
     * 背景：之前版本将本地仅有的消息（如被中止的流式回答）直接追加到 server 末尾，
     * 导致顺序错乱为 [Q1, Q2, A2, A1]。现改为：
     * 1. 以 content 去重，后端消息优先（权威来源）
     * 2. 按 createTime 升序排序，保证 Q1→A1→Q2→A2 的正确时间顺序
     * 3. createTime 为空的消息（兼容旧数据）排到末尾并保留相对顺序
     *
     * @param local 内存缓存的消息（可能含未持久化的本地临时消息，如被中止的流式回答）
     * @param server 后端返回的消息（已按 createTime ASC 排序）
     */
    private fun mergeMessages(
        local: List<MessageResp>,
        server: List<MessageResp>,
    ): List<MessageResp> = mergeMessagesForSession(local, server)

    private fun refreshSessions() {
        viewModelScope.launch {
            runCatching { repository.listSessions(null, SESSION_PAGE_SIZE) }
                .onSuccess { page ->
                    _uiState.update { state ->
                        state.copy(
                            sessions = page.data,
                            sessionLastId = page.data.lastOrNull()?.sessionId,
                            hasMoreSessions = page.hasMore,
                            loadingMoreSessions = false,
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(
            repository: ChatRepository = ChatRepository(),
            modelRepository: ModelRepository = ModelRepository(),
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ChatViewModel(repository, modelRepository) }
            }
    }
}

/**
 * 合并本地缓存消息与后端消息，严格按 createTime 升序排列。
 *
 * 提取为包级可见函数以便单元测试，无需实例化 ChatViewModel（依赖 Android 组件）。
 *
 * 算法：
 * 1. 以 content 去重，后端消息优先（权威来源，含正确的 messageId 与 createTime）
 * 2. 合并后按 createTime 升序排序（ISO-8601 字符串字典序 == 时间序）
 * 3. createTime 为空的消息视为最大值排到末尾，保持相对稳定排序
 *
 * @param local 内存缓存的消息（可能含未持久化的本地临时消息，如被中止的流式回答）
 * @param server 后端返回的消息（已按 createTime ASC 排序）
 */
internal fun mergeMessagesForSession(
    local: List<MessageResp>,
    server: List<MessageResp>,
): List<MessageResp> {
    if (local.isEmpty()) return server
    // 1. 以后端 content 为基准去重；本地仅保留后端尚未返回的消息
    val serverContents = server.map { it.content }.toSet()
    val localOnly = local.filter { it.content !in serverContents }
    if (localOnly.isEmpty()) return server

    // 2. 合并后按 createTime 升序排序（ISO-8601 字符串字典序 == 时间序）
    //    sortedWith 使用 TimSort，天然稳定；createTime 为空视为最大值排到末尾
    val combined = server + localOnly
    return combined.sortedWith(compareBy(nullsLast<String>()) { it.createTime.ifBlank { null } })
}
