<script setup lang="ts">
/**
 * 对话主页
 *
 * 组装 SessionList + MessageList + MessageInput，响应式布局。
 */
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  ElDialog,
  ElForm,
  ElFormItem,
  ElSelect,
  ElOption,
  ElInput,
  ElButton,
  ElTag,
  ElMessage,
} from 'element-plus'
import { useChatStore } from '@/stores/chat'
import { searchChatHistory, type ChatSearchHit } from '@/api/chat'
import SessionList from '@/components/chat/SessionList.vue'
import MessageList from '@/components/chat/MessageList.vue'
import MessageInput from '@/components/chat/MessageInput.vue'

const chatStore = useChatStore()
const route = useRoute()

/** 当前选中的会话 ID */
const currentSessionId = ref<number | null>(null)

/** 新建会话对话框 */
const showNewDialog = ref(false)
const newModelId = ref('ollama')
const newTitle = ref('')

/** 全局搜索对话框 */
const showSearchDialog = ref(false)
const searchKeyword = ref('')
const searchHits = ref<ChatSearchHit[]>([])
const searching = ref(false)
const searchError = ref('')
const searchTotal = ref(0)
const searchPage = ref(1)
const searchHasMore = ref(false)

/** 时间范围筛选 */
const searchTimeFilter = ref<'all' | '7d' | '30d' | 'custom'>('all')
const searchCustomRange = ref<[Date, Date] | null>(null)

/** 角色筛选 */
const searchRoleFilter = ref<'' | 'user' | 'assistant'>('')

/** 根据筛选状态计算 startTime/endTime（毫秒时间戳） */
function resolveTimeRange(): { startTime?: number; endTime?: number } {
  const now = Date.now()
  const DAY = 24 * 60 * 60 * 1000
  if (searchTimeFilter.value === '7d') {
    return { startTime: now - 7 * DAY, endTime: now }
  }
  if (searchTimeFilter.value === '30d') {
    return { startTime: now - 30 * DAY, endTime: now }
  }
  if (searchTimeFilter.value === 'custom' && searchCustomRange.value) {
    const [start, end] = searchCustomRange.value
    return { startTime: start.getTime(), endTime: end.getTime() + DAY - 1 }
  }
  return {}
}

/** 清空筛选 */
function clearSearchFilters() {
  searchTimeFilter.value = 'all'
  searchCustomRange.value = null
  searchRoleFilter.value = ''
}

/** 清空筛选并重跑检索（add-p3-rag-feedback-ops） */
function handleClearFilters() {
  clearSearchFilters()
  if (searchKeyword.value.trim()) {
    doSearch()
  }
}

/** 初始化：读取 kbId 激活 RAG；读取 sessionId 直接定位会话（关联问答跳转） */
onMounted(async () => {
  const kbIdParam = route.query.kbId
  if (typeof kbIdParam === 'string' && kbIdParam.trim()) {
    const id = Number(kbIdParam)
    if (!Number.isNaN(id) && id > 0) {
      chatStore.activeKbId = id
    }
  }
  // 关联问答/检索跳转携带的 sessionId（add-p3-rag-feedback-ops）
  const sessionIdParam = route.query.sessionId
  const targetSessionFromQuery =
    typeof sessionIdParam === 'string' && sessionIdParam.trim()
      ? Number(sessionIdParam)
      : null

  /** 搜索跳转携带的 highlightMessageId：跳转到会话后定位到对应消息 */
  const highlightMessageIdParam = route.query.highlightMessageId
  const targetMessageId =
    typeof highlightMessageIdParam === 'string' && highlightMessageIdParam.trim()
      ? Number(highlightMessageIdParam)
      : null

  // 记录刷新前的选中 id（用于 fetchSessions 后锚定，避免列表短暂为空导致的选中态闪烁）
  const prevSelectedId = currentSessionId.value
  await chatStore.fetchSessions()
  if (chatStore.sessions.length > 0) {
    // 优先沿用路由传入的 sessionId；其次原选中态；最后首项
    const stillExists =
      prevSelectedId != null &&
      chatStore.sessions.some((s) => s.sessionId === prevSelectedId)
    const fromQueryExists =
      targetSessionFromQuery != null &&
      !Number.isNaN(targetSessionFromQuery) &&
      chatStore.sessions.some((s) => s.sessionId === targetSessionFromQuery)
    const targetId = fromQueryExists
      ? (targetSessionFromQuery as number)
      : stillExists
        ? (prevSelectedId as number)
        : (chatStore.sessions[0]?.sessionId as number)
    currentSessionId.value = targetId
    try {
      await chatStore.fetchMessages(targetId)
      // 搜索跳转：定位到高亮消息
      if (targetMessageId && !Number.isNaN(targetMessageId)) {
        scrollToMessage(targetMessageId)
      }
    } catch {
      ElMessage.warning('该会话已删除或无法访问')
    }
  }
})

/** 关闭 RAG */
function disableRag() {
  chatStore.activeKbId = null
}

/** 新建会话 */
async function handleNewSession() {
  showNewDialog.value = true
}

/** 确认新建 */
async function confirmNewSession() {
  if (!newModelId.value.trim()) return
  const session = await chatStore.createNewSession(newModelId.value, newTitle.value || undefined)
  currentSessionId.value = session.sessionId
  showNewDialog.value = false
  newTitle.value = ''
}

/** 切换会话 */
async function handleSelectSession(sessionId: number | null) {
  if (sessionId == null) return
  currentSessionId.value = sessionId
  await chatStore.fetchMessages(sessionId)
}

/** 打开全局搜索 */
function openSearch() {
  showSearchDialog.value = true
  searchKeyword.value = ''
  searchHits.value = []
  searchError.value = ''
  searchTotal.value = 0
  searchPage.value = 1
  searchHasMore.value = false
  searchRoleFilter.value = ''
  clearSearchFilters()
}

/** 执行全局检索 */
async function doSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  searching.value = true
  searchError.value = ''
  searchPage.value = 1
  try {
    const filters = resolveTimeRange()
    if (searchRoleFilter.value) {
      ;(filters as Record<string, unknown>).role = searchRoleFilter.value
    }
    const res = await searchChatHistory(kw, 1, 20, filters)
    searchHits.value = res.hits
    searchTotal.value = res.total
    searchHasMore.value = res.hasMore
  } catch (err: unknown) {
    searchError.value = err instanceof Error ? err.message : String(err ?? '检索失败')
    searchHits.value = []
    searchTotal.value = 0
    searchHasMore.value = false
  } finally {
    searching.value = false
  }
}

/** 加载更多搜索结果 */
async function loadMoreSearch() {
  if (!searchHasMore.value || searching.value) return
  searching.value = true
  try {
    const nextPage = searchPage.value + 1
    const filters = resolveTimeRange()
    if (searchRoleFilter.value) {
      ;(filters as Record<string, unknown>).role = searchRoleFilter.value
    }
    const res = await searchChatHistory(searchKeyword.value.trim(), nextPage, 20, filters)
    searchHits.value.push(...res.hits)
    searchPage.value = nextPage
    searchHasMore.value = res.hasMore
  } catch (err: unknown) {
    searchError.value = err instanceof Error ? err.message : String(err ?? '加载更多失败')
  } finally {
    searching.value = false
  }
}

/** 跳转到命中记录所在会话，并定位到对应消息 */
async function jumpToHit(hit: ChatSearchHit) {
  if (hit.sessionId == null) return
  showSearchDialog.value = false
  currentSessionId.value = hit.sessionId
  try {
    await chatStore.fetchMessages(hit.sessionId)
    // 定位到命中消息
    if (hit.messageId) {
      scrollToMessage(Number(hit.messageId))
    }
  } catch {
    ElMessage.warning('该会话已删除或无法访问')
  }
}

/** 滚动到指定 messageId 的消息位置 */
function scrollToMessage(messageId: number) {
  nextTick(() => {
    const el = document.querySelector(`[data-message-id="${messageId}"]`)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      // 高亮闪烁效果
      el.classList.add('search-hit-highlight')
      setTimeout(() => el.classList.remove('search-hit-highlight'), 3000)
    }
  })
}

/**
 * 高亮关键词：将 content 中匹配 keyword 的片段用 <mark> 包裹。
 * 对 HTML 特殊字符做转义，防止 XSS。
 */
function highlightKeyword(content: string, keyword: string): string {
  if (!keyword || !keyword.trim()) return escapeHtml(content)
  const escaped = escapeHtml(content)
  const kw = keyword.trim()
  // 转义正则特殊字符
  const safeKw = kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(${safeKw})`, 'gi')
  return escaped.replace(regex, '<mark class="search-highlight">$1</mark>')
}

/** HTML 特殊字符转义 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
</script>

<template>
  <div class="chat-view">
    <!-- 侧边栏：会话列表 -->
    <aside class="chat-sidebar">
      <SessionList
        v-model="currentSessionId"
        @new-session="handleNewSession"
        @select="handleSelectSession"
      />
    </aside>

    <!-- 主对话区 -->
    <main class="chat-main">
      <!-- 顶部工具条：全局搜索入口（add-p2-cache-search-rewrite） -->
      <div class="chat-toolbar">
        <el-button text size="small" @click="openSearch">🔍 全局搜索历史对话</el-button>
      </div>

      <!-- RAG 状态条 -->
      <div v-if="chatStore.activeKbId" class="rag-banner">
        <el-tag type="success" effect="dark" size="small" round>RAG</el-tag>
        <span class="rag-banner-text">
          已启用知识库 ID={{ chatStore.activeKbId }}，对话将基于知识库内容回答
        </span>
        <el-button text size="small" @click="disableRag">关闭</el-button>
      </div>

      <!-- 空状态 -->
      <div v-if="!currentSessionId" class="chat-empty">
        <div class="empty-icon">💬</div>
        <h2>选择或创建一个对话</h2>
        <p>从左侧列表选择一个会话，或点击"新建对话"开始</p>
      </div>

      <!-- 对话区 -->
      <template v-else>
        <MessageList />
        <MessageInput />
      </template>
    </main>

    <!-- 新建会话对话框 -->
    <el-dialog
      v-model="showNewDialog"
      title="新建对话"
      width="440px"
      :close-on-click-modal="false"
      align-center
      class="new-session-dialog"
    >
      <el-form label-position="top" class="new-session-form">
        <el-form-item label="模型">
          <el-select v-model="newModelId" placeholder="请选择模型" style="width: 100%">
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="通义千问 (Qwen)" value="qwen" />
            <el-option label="Ollama 本地" value="ollama" />
          </el-select>
        </el-form-item>
        <el-form-item label="会话标题（可选）">
          <el-input
            v-model="newTitle"
            placeholder="留空则自动生成"
            maxlength="128"
            show-word-limit
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showNewDialog = false">取消</el-button>
          <el-button
            type="primary"
            @click="confirmNewSession"
            :disabled="!newModelId.trim()"
          >
            开始对话
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 全局搜索对话框 -->
    <el-dialog
      v-model="showSearchDialog"
      title="全局搜索历史对话"
      width="640px"
      :close-on-click-modal="true"
      align-center
      class="search-dialog"
    >
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="输入关键词，按回车检索"
          clearable
          @keyup.enter="doSearch"
        />
        <el-button type="primary" :loading="searching" @click="doSearch">检索</el-button>
      </div>
      <!-- 筛选：时间范围 + 角色 -->
      <div class="search-filter">
        <el-radio-group v-model="searchTimeFilter" size="small" @change="doSearch">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="7d">7天</el-radio-button>
          <el-radio-button label="30d">30天</el-radio-button>
          <el-radio-button label="custom">自定义</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="searchTimeFilter === 'custom'"
          v-model="searchCustomRange"
          type="daterange"
          size="small"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="doSearch"
        />
        <el-select
          v-model="searchRoleFilter"
          placeholder="角色"
          size="small"
          clearable
          style="width: 110px; margin-left: 8px"
          @change="doSearch"
        >
          <el-option label="全部角色" value="" />
          <el-option label="用户提问" value="user" />
          <el-option label="AI 回答" value="assistant" />
        </el-select>
        <el-button
          text
          size="small"
          :disabled="searchTimeFilter === 'all' && !searchRoleFilter && !searchKeyword"
          @click="handleClearFilters"
        >
          清空筛选
        </el-button>
      </div>
      <!-- 搜索结果统计 -->
      <div v-if="searchTotal > 0" class="search-summary">
        找到 {{ searchTotal }} 条结果
      </div>
      <div v-if="searchError" class="search-error">{{ searchError }}</div>
      <div v-if="!searching && searchHits.length === 0 && searchKeyword" class="search-empty">
        未找到匹配的对话记录
      </div>
      <ul class="search-results">
        <li
          v-for="hit in searchHits"
          :key="hit.messageId"
          class="search-item"
          @click="jumpToHit(hit)"
        >
          <div class="search-item-head">
            <el-tag size="small" :type="hit.role === 'user' ? 'info' : 'success'">
              {{ hit.role === 'user' ? '用户' : 'AI' }}
            </el-tag>
            <span v-if="hit.title || hit.titleHighlight" class="search-item-title">
              会话: <span v-html="hit.titleHighlight || highlightKeyword(hit.title || '', searchKeyword)"></span>
            </span>
          </div>
          <div
            class="search-item-content"
            v-html="hit.highlight || highlightKeyword(hit.content, searchKeyword)"
          ></div>
        </li>
      </ul>
      <!-- 加载更多 -->
      <div v-if="searchHasMore" class="search-loadmore">
        <el-button text :loading="searching" @click="loadMoreSearch">加载更多</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-view {
  display: flex;
  height: calc(100vh - var(--header-height) - var(--space-6) * 2);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  background: var(--bg-page);
}

.chat-sidebar {
  width: 280px;
  flex-shrink: 0;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--bg-page);
}

.rag-banner {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 8px 16px;
  background: linear-gradient(90deg, rgba(34, 197, 94, 0.12), rgba(34, 197, 94, 0.04));
  border-bottom: 1px solid rgba(34, 197, 94, 0.2);
  font-size: 13px;
  color: var(--text-secondary);
}
.rag-banner-text {
  flex: 1;
}

.chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}
.empty-icon {
  font-size: 48px;
  margin-bottom: var(--space-4);
}
.chat-empty h2 {
  font-size: 18px;
  font-weight: var(--font-weight-semibold);
  color: var(--text-secondary);
  margin: 0 0 var(--space-2);
}
.chat-empty p {
  font-size: 14px;
  margin: 0;
}

/* 响应式：移动端隐藏侧边栏 */
@media (max-width: 768px) {
  .chat-sidebar {
    display: none;
  }
}

/* ============ 新建会话对话框 ============ */
.new-session-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: var(--space-5) var(--space-6) var(--space-3);
  border-bottom: 1px solid var(--border-default);
}
.new-session-dialog :deep(.el-dialog__title) {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}
.new-session-dialog :deep(.el-dialog__body) {
  padding: var(--space-5) var(--space-6);
}
.new-session-dialog :deep(.el-dialog__footer) {
  padding: var(--space-3) var(--space-6) var(--space-5);
  border-top: 1px solid var(--border-default);
}

.new-session-form :deep(.el-form-item__label) {
  font-weight: var(--font-weight-medium);
  color: var(--text-secondary);
  padding-bottom: 6px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}

/* ============ 全局搜索对话框（add-p2-cache-search-rewrite） ============ */
.chat-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 4px 12px;
  border-bottom: 1px solid var(--border-default);
  background: var(--bg-card);
}

.search-dialog :deep(.el-dialog__body) {
  padding: var(--space-4) var(--space-5);
}
.search-bar {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.search-filter {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}
.search-error {
  color: var(--color-danger, #ef4444);
  font-size: 13px;
  margin-bottom: var(--space-2);
}
.search-empty {
  color: var(--text-tertiary);
  font-size: 13px;
  padding: var(--space-4) 0;
  text-align: center;
}
.search-results {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 420px;
  overflow-y: auto;
}
.search-item {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--border-default);
  cursor: pointer;
  transition: background 0.15s;
}
.search-item:hover {
  background: var(--bg-hover, rgba(0, 0, 0, 0.03));
}
.search-item-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: 4px;
}
.search-item-session {
  font-size: 12px;
  color: var(--text-tertiary);
}
.search-item-title {
  font-size: 12px;
  color: var(--text-secondary);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-item-content {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  display: box;
  line-clamp: 2;
  box-orient: vertical;
  overflow: hidden;
}
.search-summary {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 8px;
}
.search-loadmore {
  text-align: center;
  padding: 8px 0;
}
.search-highlight {
  background: #fff3b0;
  color: inherit;
  font-weight: 600;
  padding: 0 1px;
  border-radius: 2px;
}

/* ES 搜索高亮 <em> 标签样式 */
.search-item-content em,
.search-item-title em {
  font-style: normal;
  background: #fff3b0;
  color: inherit;
  font-weight: 600;
  padding: 0 1px;
  border-radius: 2px;
}

/* 搜索命中消息高亮闪烁动画 */
:deep(.search-hit-highlight) {
  animation: hit-pulse 0.6s ease-in-out 3;
  border-radius: 8px;
}

@keyframes hit-pulse {
  0%, 100% { background-color: transparent; }
  50% { background-color: rgba(99, 102, 241, 0.12); }
}
</style>