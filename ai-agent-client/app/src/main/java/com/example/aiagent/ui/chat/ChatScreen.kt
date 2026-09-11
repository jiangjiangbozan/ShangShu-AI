package com.example.aiagent.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aiagent.data.model.ChatSearchHit
import com.example.aiagent.data.model.MessageResp
import com.example.aiagent.data.model.SessionResp
import com.example.aiagent.data.model.TokenStatsResp
import com.example.aiagent.ui.chat.components.VoiceRecordButton
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** 会话列表预加载阈值：距离底部 N 项时触发加载更多。 */
private const val PREFETCH_THRESHOLD = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDelete by remember { mutableStateOf<SessionResp?>(null) }

    // 首次进入：加载会话列表 + Token 统计 + 知识库列表 + 模型列表
    LaunchedEffect(Unit) {
        viewModel.loadSessions()
        viewModel.loadTokenStats()
        viewModel.loadKnowledgeBases()
        viewModel.loadModels()
    }

    LaunchedEffect(uiState.error) {
        val msg = uiState.error
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SessionListPane(
                    sessions = uiState.sessions,
                    currentSessionId = uiState.currentSessionId,
                    sessionDrafts = uiState.sessionDrafts,
                    hasMoreSessions = uiState.hasMoreSessions,
                    loadingMoreSessions = uiState.loadingMoreSessions,
                    onSelect = { id ->
                        viewModel.selectSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onNew = {
                        viewModel.newSession()
                        scope.launch { drawerState.close() }
                    },
                    onDelete = { pendingDelete = it },
                    onLoadMore = { viewModel.loadMoreSessions() },
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.sessions
                                    .firstOrNull { it.sessionId == uiState.currentSessionId }
                                    ?.title?.ifBlank { "对话" } ?: "对话",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row {
                                uiState.tokenStats?.let { stats ->
                                    Text(
                                        text = "今日 Token: ${stats.totalTokens}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // 当前模型名称
                                val currentModel = uiState.models
                                    .firstOrNull { it.id == uiState.currentModelId }
                                if (currentModel != null) {
                                    if (uiState.tokenStats != null) {
                                        Text(
                                            text = " · ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = currentModel.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "会话列表")
                        }
                    },
                    actions = {
                        // 模型切换按钮
                        IconButton(onClick = { viewModel.toggleModelSelector() }) {
                            Icon(
                                Icons.Filled.SmartToy,
                                contentDescription = "切换模型",
                                tint = if (uiState.currentModelId != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        // RAG 知识库激活按钮
                        IconButton(onClick = { viewModel.toggleKbSelector() }) {
                            Icon(
                                Icons.Filled.Storage,
                                contentDescription = "知识库",
                                tint = if (uiState.activeKbId != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        // 全局搜索
                        IconButton(onClick = { viewModel.openSearch() }) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                // RAG 激活状态横幅
                if (uiState.activeKbId != null) {
                    RagBanner(
                        kbName = uiState.activeKbName,
                        onClose = { viewModel.deactivateKb() },
                    )
                }
                // 消息列表（权重填充剩余空间）
                Box(modifier = Modifier.weight(1f)) {
                    MessageListPane(
                        messages = uiState.messages,
                        streamingContent = uiState.streamingContent,
                        isStreaming = uiState.isStreaming,
                        loading = uiState.loading,
                        hasMoreMessages = uiState.hasMoreMessages,
                        loadingOlderMessages = uiState.loadingOlderMessages,
                        onLoadOlder = { viewModel.loadOlderMessages() },
                        highlightMessageId = viewModel.consumeHighlightMessageId(),
                        onQuote = { content -> viewModel.setQuoteContent(content) },
                        onRegenerate = { viewModel.regenerateLastMessage() },
                        onDelete = { msg -> viewModel.deleteMessage(msg) },
                    )
                }
                // 引用预览
                if (uiState.quoteContent != null) {
                    QuotePreview(
                        content = uiState.quoteContent!!,
                        onDismiss = { viewModel.clearQuoteContent() },
                    )
                }
                // 输入区
                MessageInput(
                    sessionId = uiState.currentSessionId,
                    draft = uiState.currentSessionId?.let { uiState.sessionDrafts[it] } ?: "",
                    isStreaming = uiState.isStreaming,
                    webSearchEnabled = uiState.webSearchEnabled,
                    onDraftChange = { viewModel.setDraft(uiState.currentSessionId ?: 0, it) },
                    onSend = { viewModel.sendMessage(it) },
                    onStop = { viewModel.stopStreaming() },
                    onToggleWebSearch = { viewModel.toggleWebSearch() },
                )
            }
        }
    }

    // 知识库选择对话框
    if (uiState.showKbSelector) {
        KnowledgeBaseDialog(
            knowledgeBases = uiState.knowledgeBases,
            activeKbId = uiState.activeKbId,
            onSelect = { id, name -> viewModel.activateKb(id, name) },
            onDeactivate = { viewModel.deactivateKb() },
            onDismiss = { viewModel.toggleKbSelector() },
        )
    }

    // 模型选择对话框
    if (uiState.showModelSelector) {
        com.example.aiagent.ui.chat.components.ModelSelectorDialog(
            models = uiState.models,
            currentModelId = uiState.currentModelId,
            onSelect = { viewModel.selectModel(it) },
            onDismiss = { viewModel.toggleModelSelector() },
        )
    }

    // 全局搜索对话框
    if (uiState.showSearch) {
        GlobalSearchDialog(
            searchQuery = uiState.searchQuery,
            searchHits = uiState.searchHits,
            searchTotal = uiState.searchTotal,
            searchHasMore = uiState.searchHasMore,
            searching = uiState.searching,
            searchHistory = uiState.searchHistory,
            searchRoleFilter = uiState.searchRoleFilter,
            onQueryChange = { viewModel.searchChatHistory(it) },
            onJumpToHit = { viewModel.jumpToHit(it) },
            onHistoryClick = { viewModel.searchChatHistory(it) },
            onHistoryRemove = { viewModel.removeSearchHistory(it) },
            onHistoryClear = { viewModel.clearSearchHistory() },
            onRoleFilterChange = { viewModel.setSearchRoleFilter(it) },
            onDismiss = { viewModel.closeSearch() },
        )
    }

    // 删除确认对话框
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除会话") },
            text = { Text("确定删除「${target.title.ifBlank { "新对话" }}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(target.sessionId)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/** RAG 激活状态横幅。 */
@Composable
private fun RagBanner(kbName: String, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = "RAG: $kbName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭 RAG",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 会话列表侧边栏（无限滚动）。 */
@Composable
private fun SessionListPane(
    sessions: List<SessionResp>,
    currentSessionId: Long?,
    sessionDrafts: Map<Long, String>,
    hasMoreSessions: Boolean,
    loadingMoreSessions: Boolean,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    onDelete: (SessionResp) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    // 无限滚动：接近底部时触发加载更多
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layout.totalItemsCount - PREFETCH_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "会话",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNew) {
                Icon(Icons.Filled.Add, contentDescription = "新建会话")
            }
        }
        HorizontalDivider()
        LazyColumn(state = listState) {
            items(sessions, key = { it.sessionId }) { session ->
                val draft = sessionDrafts[session.sessionId] ?: ""
                SessionItem(
                    session = session,
                    draft = draft,
                    selected = session.sessionId == currentSessionId,
                    onClick = { onSelect(session.sessionId) },
                    onDelete = { onDelete(session) },
                )
            }
            if (loadingMoreSessions) {
                item(key = "loading_more") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: SessionResp,
    draft: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title.ifBlank { "新对话" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (draft.isNotBlank()) {
                    Text(
                        text = "[草稿] $draft",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = session.updateTime.take(16),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除会话",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 消息列表。 */
@Composable
private fun MessageListPane(
    messages: List<MessageResp>,
    streamingContent: String,
    isStreaming: Boolean,
    loading: Boolean,
    hasMoreMessages: Boolean,
    loadingOlderMessages: Boolean,
    onLoadOlder: () -> Unit,
    highlightMessageId: Long? = null,
    onQuote: ((String) -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onDelete: ((MessageResp) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val totalItems = messages.size + (if (isStreaming) 1 else 0)

    // 触顶加载更早消息时记录的锚点：原顶部消息 ID 与其滚动偏移，
    // 加载完成后把该消息恢复到原位置，避免视口跳动。
    var anchorMessageId by remember { mutableStateOf<Long?>(null) }
    var anchorOffset by remember { mutableStateOf(0) }
    // 加载更早期间禁止自动滚底，否则列表长度变化会强制跳到末尾
    val isRestoringAnchor = anchorMessageId != null

    // 始终引用最新值，避免 LaunchedEffect 闭包捕获过期状态
    val currentMessages by rememberUpdatedState(messages)
    val currentHasMore by rememberUpdatedState(hasMoreMessages)
    val currentLoadingOlder by rememberUpdatedState(loadingOlderMessages)
    val currentOnLoadOlder by rememberUpdatedState(onLoadOlder)

    // 触顶（firstVisibleItemIndex == 0）时加载更早消息
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (currentHasMore && !currentLoadingOlder) {
                    val first = currentMessages.firstOrNull()?.messageId
                    if (first != null) {
                        anchorMessageId = first
                        anchorOffset = listState.firstVisibleItemScrollOffset
                        currentOnLoadOlder()
                    }
                }
            }
    }

    // 新消息追加 / 流式时自动滚底（加载更早期间跳过）
    LaunchedEffect(totalItems) {
        if (totalItems > 0 && !isRestoringAnchor) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    // 加载更早完成后，恢复锚点消息到原视口位置
    LaunchedEffect(messages.size, loadingOlderMessages) {
        val anchorId = anchorMessageId ?: return@LaunchedEffect
        val newIndex = messages.indexOfFirst { it.messageId == anchorId }
        if (newIndex >= 0) {
            listState.scrollToItem(newIndex, anchorOffset)
        }
        anchorMessageId = null
    }

    // 搜索跳转：滚动到高亮消息
    LaunchedEffect(highlightMessageId) {
        val msgId = highlightMessageId ?: return@LaunchedEffect
        val index = messages.indexOfFirst { it.messageId == msgId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    if (loading) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.messageId }) { msg ->
                MessageBubble(
                    msg = msg,
                    onQuote = onQuote,
                    onRegenerate = onRegenerate,
                    onDelete = { onDelete?.invoke(msg) },
                )
            }
            if (isStreaming) {
                item(key = "streaming") {
                    StreamingBubble(streamingContent)
                }
            }
        }
        // 顶部"加载更早"状态指示（浮层，不参与列表布局）
        if (loadingOlderMessages) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp))
                        Text(
                            text = "加载更早消息...",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/** 提取 URL 主机名用于紧凑展示（非法 URL 回退原文）。 */
private fun hostOf(url: String): String = runCatching {
    java.net.URI(url).host?.removePrefix("www.") ?: url
}.getOrDefault(url)

@Composable
private fun MessageBubble(
    msg: MessageResp,
    onQuote: ((String) -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val isUser = msg.isUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isUser) 12.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 12.dp,
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (isUser) {
                        Text(
                            text = msg.content,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        // AI 回答：使用富渲染组件（思考折叠 + 工具卡片 + Markdown）
                        com.example.aiagent.ui.chat.components.MessageContentRenderer(
                            content = msg.content,
                            modifier = Modifier.widthIn(max = 280.dp),
                        )
                    }
                    // 网络搜索引用来源（webSearch 增强时非空）：横向滑动来源卡片
                    if (!isUser && msg.sources.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        SourceChips(sources = msg.sources)
                    }
                    if (msg.latencyMs > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${msg.latencyMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        // 消息操作栏（借鉴 Operit 的消息操作设计）
        com.example.aiagent.ui.chat.components.MessageActionBar(
            message = msg,
            actions = com.example.aiagent.ui.chat.components.MessageActions(
                onCopy = { /* 复制已在组件内部处理 */ },
                onQuote = { onQuote?.invoke(msg.content) },
                onRegenerate = { onRegenerate?.invoke() },
                onDelete = { onDelete?.invoke() },
            ),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 引用预览条（显示被引用的内容，可点击关闭）。 */
@Composable
private fun QuotePreview(
    content: String,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = content.take(50) + if (content.length > 50) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "取消引用",
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** 流式输出气泡（显示打字机效果，Markdown 增量渲染）。 */
@Composable
private fun StreamingBubble(content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp, topEnd = 12.dp,
                bottomStart = 4.dp, bottomEnd = 12.dp,
            ),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Markdown(
                content = content.ifBlank { "..." },
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

/**
 * 网络搜索引用来源：横向滑动的来源卡片列表。
 * 每张卡片显示序号 + 域名，超出气泡宽度时可左右滑动。
 */
@Composable
private fun SourceChips(sources: List<String>) {
    Column(modifier = Modifier.widthIn(max = 280.dp)) {
        Text(
            text = "参考来源",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            sources.forEachIndexed { index, url ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = hostOf(url),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 消息输入区（支持草稿保存 + 流式停止 + 网络搜索开关）。 */
@Composable
private fun MessageInput(
    sessionId: Long?,
    draft: String,
    isStreaming: Boolean,
    webSearchEnabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onToggleWebSearch: () -> Unit,
) {
    var text by rememberSaveable(sessionId) { mutableStateOf(draft) }

    // 当草稿从外部加载/变化时同步到本地 text
    LaunchedEffect(sessionId, draft) {
        if (draft != text) text = draft
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 网络搜索增强开关行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Switch(
                checked = webSearchEnabled,
                onCheckedChange = { onToggleWebSearch() },
                modifier = Modifier.size(width = 40.dp, height = 24.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "网络搜索",
                style = MaterialTheme.typography.labelMedium,
                color = if (webSearchEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                text = newValue
                sessionId?.let { onDraftChange(newValue) }
            },
            placeholder = { Text("输入消息…") },
            minLines = 1,
            maxLines = 4,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (isStreaming) {
            IconButton(onClick = onStop) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = "停止",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            IconButton(
                onClick = {
                    onSend(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
        // 语音录制按钮
        VoiceRecordButton(
            onTranscriptionResult = { transcribed ->
                text = transcribed
            },
            onError = { /* error handled internally */ },
        )
    }
    } // Column
}

/** 知识库选择对话框。 */
@Composable
private fun KnowledgeBaseDialog(
    knowledgeBases: List<com.example.aiagent.data.model.KnowledgeBaseResp>,
    activeKbId: Long?,
    onSelect: (Long, String) -> Unit,
    onDeactivate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RAG 知识库") },
        text = {
            Column {
                if (activeKbId != null) {
                    Text(
                        "当前已激活知识库，对话将基于知识库内容回答",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onDeactivate, modifier = Modifier.fillMaxWidth()) {
                        Text("关闭 RAG")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (knowledgeBases.isEmpty()) {
                    Text("暂无可用知识库")
                } else {
                    knowledgeBases.forEach { kb ->
                        Surface(
                            onClick = { onSelect(kb.id, kb.name) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (kb.id == activeKbId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(kb.name, fontWeight = FontWeight.Bold)
                                if (kb.description.isNotBlank()) {
                                    Text(
                                        kb.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    "${kb.documentCount} 文档 / ${kb.chunkCount} 分片",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 全局搜索对话框（含搜索历史：展示/再次搜索/单条删除/清空）。 */
@Composable
private fun GlobalSearchDialog(
    searchQuery: String,
    searchHits: List<ChatSearchHit>,
    searchTotal: Int,
    searchHasMore: Boolean,
    searching: Boolean,
    searchHistory: List<String>,
    searchRoleFilter: String?,
    onQueryChange: (String) -> Unit,
    onJumpToHit: (ChatSearchHit) -> Unit,
    onHistoryClick: (String) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onHistoryClear: () -> Unit,
    onRoleFilterChange: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全局搜索历史对话") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("输入关键词…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 角色筛选：全部 / 用户 / AI
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf(null to "全部", "user" to "用户", "assistant" to "AI").forEach { (role, label) ->
                        val selected = searchRoleFilter == role
                        Surface(
                            onClick = { onRoleFilterChange(role) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 搜索结果统计
                if (searchTotal > 0) {
                    Text(
                        "找到 $searchTotal 条结果",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                when {
                    searching -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }

                    // 已有关键词：显示搜索结果（或无结果提示）
                    searchQuery.isNotBlank() -> {
                        if (searchHits.isEmpty()) {
                            Text("无结果", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LazyColumn {
                                items(searchHits) { hit ->
                                    SearchHitItem(hit = hit, keyword = searchQuery, onClick = { onJumpToHit(hit) })
                                }
                            }
                        }
                    }

                    // 无关键词且有历史：展示搜索历史
                    searchHistory.isNotEmpty() -> {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "搜索历史",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onHistoryClear) {
                                    Text("清空", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            LazyColumn {
                                items(searchHistory) { keyword ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = keyword,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onHistoryClick(keyword) }
                                                .padding(vertical = 6.dp),
                                        )
                                        IconButton(
                                            onClick = { onHistoryRemove(keyword) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "删除该记录",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> Text(
                        "暂无搜索历史",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun SearchHitItem(hit: ChatSearchHit, keyword: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(
                    text = if (hit.role == "user") "用户" else "AI",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (hit.role == "user") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (!hit.title.isNullOrBlank()) {
                    Text(
                        text = hit.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = hit.createTime ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (!hit.highlight.isNullOrBlank()) parseEsHighlight(hit.highlight) else highlightKeyword(hit.content, keyword),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 高亮关键词（背景色 + 加粗，用于搜索结果展示）。 */
private fun highlightKeyword(content: String, keyword: String): AnnotatedString {
    if (keyword.isBlank()) return AnnotatedString(content)
    val highlightStyle = SpanStyle(
        fontWeight = FontWeight.Bold,
        background = Color(0xFFFFF3B0),
    )
    return buildAnnotatedString {
        var remaining = content
        val kw = keyword.trim().lowercase()
        while (remaining.isNotEmpty()) {
            val idx = remaining.lowercase().indexOf(kw)
            if (idx < 0) {
                append(remaining)
                break
            }
            append(remaining.substring(0, idx))
            withStyle(highlightStyle) {
                append(remaining.substring(idx, idx + keyword.trim().length))
            }
            remaining = remaining.substring(idx + keyword.trim().length)
        }
    }
}

/** 解析 ES 返回的 <em> 高亮标签为 AnnotatedString。 */
private fun parseEsHighlight(html: String): AnnotatedString {
    val highlightStyle = SpanStyle(
        fontWeight = FontWeight.Bold,
        background = Color(0xFFFFF3B0),
    )
    val emOpen = "<em>"
    val emClose = "</em>"
    return buildAnnotatedString {
        var remaining = html
        while (remaining.isNotEmpty()) {
            val openIdx = remaining.indexOf(emOpen)
            if (openIdx < 0) {
                append(remaining)
                break
            }
            append(remaining.substring(0, openIdx))
            val afterOpen = remaining.substring(openIdx + emOpen.length)
            val closeIdx = afterOpen.indexOf(emClose)
            if (closeIdx < 0) {
                withStyle(highlightStyle) { append(afterOpen) }
                break
            }
            withStyle(highlightStyle) { append(afterOpen.substring(0, closeIdx)) }
            remaining = afterOpen.substring(closeIdx + emClose.length)
        }
    }
}
