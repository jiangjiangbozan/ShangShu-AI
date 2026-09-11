<script setup lang="ts">
/**
 * 会话列表页（TabBar 首页）
 *
 * 对标 Web 端 ChatView 的侧边栏会话列表部分。
 * 支持下拉刷新、新建会话、删除会话、点击进入对话、全局搜索。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import { searchChatHistory } from '@/api/chat'
import type { ChatSearchHit } from '@/types/chat'
import NavBar from '@/components/common/NavBar.vue'
import SessionCard from '@/components/chat/SessionCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const chatStore = useChatStore()

/** 下拉刷新状态 */
const isRefreshing = ref(false)
/** 新建会话弹窗 */
const showNewDialog = ref(false)
const newModelId = ref('ollama')
const newTitle = ref('')

/** 搜索相关 */
const showSearchDialog = ref(false)
const searchKeyword = ref('')
const searchHits = ref<ChatSearchHit[]>([])
const searching = ref(false)
const searchError = ref('')
const searchTotal = ref(0)
const searchPage = ref(1)
const searchHasMore = ref(false)

/** 时间范围筛选 */
const searchTimeFilter = ref<'all' | '7d' | '30d'>('all')

/** 角色筛选 */
const searchRoleFilter = ref<'' | 'user' | 'assistant'>('')

/**
 * 首次加载时获取会话列表。
 *
 * 页面可见时刷新策略：
 * - uni-app 小程序端：通过 uni.$on('onShow') 监听页面可见事件
 * - H5 端：通过 document.visibilitychange 监听页面切回前台
 * - 登录后 switchTab 回到 TabBar 页时 onMounted 不再触发，
 *   需要额外监听确保刷新列表。
 */
let hasMounted = false

/** 刷新会话列表（非首次加载时） */
async function refreshIfNeeded() {
  if (hasMounted) {
    await chatStore.fetchSessions()
  }
  hasMounted = true
}

onMounted(async () => {
  await chatStore.fetchSessions()
  hasMounted = true

  // 小程序端：监听页面 onShow 事件
  if (typeof uni !== 'undefined' && uni.$on) {
    uni.$on('chatIndexShow', refreshIfNeeded)
  }

  // H5 端：监听页面可见性变化
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', handleVisibilityChange)
  }
})

onUnmounted(() => {
  if (typeof uni !== 'undefined' && uni.$off) {
    uni.$off('chatIndexShow', refreshIfNeeded)
  }
  if (typeof document !== 'undefined') {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }
})

/** H5 页面可见性变化处理 */
function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    refreshIfNeeded()
  }
}

/** 下拉刷新 */
async function onRefresh() {
  isRefreshing.value = true
  try {
    await chatStore.fetchSessions()
  } finally {
    isRefreshing.value = false
  }
}

/** 滚动到底部加载更多会话（游标分页追加） */
function onScrollToLower() {
  chatStore.loadMoreSessions()
}

/** 进入对话页 */
function handleSelectSession(sessionId: number) {
  chatStore.selectSession(sessionId)
  uni.navigateTo({
    url: `/pages/chat/conversation?sessionId=${sessionId}`,
  })
}

/** 删除会话 */
async function handleDeleteSession(sessionId: number) {
  await chatStore.removeSession(sessionId)
}

/** 打开新建会话弹窗 */
function openNewSession() {
  showNewDialog.value = true
  newTitle.value = ''
  newModelId.value = 'ollama'
}

/** 确认新建会话 */
async function confirmNewSession() {
  if (!newModelId.value.trim()) return
  const session = await chatStore.createNewSession(newModelId.value, newTitle.value || undefined)
  showNewDialog.value = false
  uni.navigateTo({
    url: `/pages/chat/conversation?sessionId=${session.sessionId}`,
  })
}

/** 选择模型 */
function selectModel(modelId: string) {
  newModelId.value = modelId
}

/** 打开搜索弹窗 */
function openSearch() {
  showSearchDialog.value = true
  searchKeyword.value = ''
  searchHits.value = []
  searchError.value = ''
  searchTotal.value = 0
  searchPage.value = 1
  searchHasMore.value = false
  searchTimeFilter.value = 'all'
  searchRoleFilter.value = ''
}

/** 计算搜索时间范围 */
function resolveTimeRange(): {
  sessionId?: number
  role?: 'user' | 'assistant'
  startTime?: number
  endTime?: number
} {
  const now = Date.now()
  const DAY = 24 * 60 * 60 * 1000
  const result: {
    sessionId?: number
    role?: 'user' | 'assistant'
    startTime?: number
    endTime?: number
  } = {}
  if (searchTimeFilter.value === '7d') {
    result.startTime = now - 7 * DAY
    result.endTime = now
  }
  if (searchTimeFilter.value === '30d') {
    result.startTime = now - 30 * DAY
    result.endTime = now
  }
  if (searchRoleFilter.value) {
    result.role = searchRoleFilter.value as 'user' | 'assistant'
  }
  return result
}

/** 执行搜索 */
async function doSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  searching.value = true
  searchError.value = ''
  searchPage.value = 1
  try {
    const res = await searchChatHistory(kw, 1, 20, resolveTimeRange())
    searchHits.value = res.hits
    searchTotal.value = res.total
    searchHasMore.value = res.hasMore
  } catch (err: unknown) {
    searchError.value = err instanceof Error ? err.message : '检索失败'
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
    const res = await searchChatHistory(searchKeyword.value.trim(), nextPage, 20, resolveTimeRange())
    searchHits.value.push(...res.hits)
    searchPage.value = nextPage
    searchHasMore.value = res.hasMore
  } catch (err: unknown) {
    searchError.value = err instanceof Error ? err.message : '加载更多失败'
  } finally {
    searching.value = false
  }
}

/** 跳转到搜索命中记录所在会话，并定位到对应消息 */
async function jumpToHit(hit: ChatSearchHit) {
  if (hit.sessionId == null) return
  showSearchDialog.value = false
  chatStore.selectSession(hit.sessionId)
  try {
    await chatStore.fetchMessages(hit.sessionId)
  } catch {
    uni.showToast({ title: '该会话已删除或无法访问', icon: 'none' })
    return
  }
  const params = `/pages/chat/conversation?sessionId=${hit.sessionId}`
  const messageIdParam = hit.messageId ? `&highlightMessageId=${hit.messageId}` : ''
  uni.navigateTo({
    url: params + messageIdParam,
  })
}

/** 高亮关键词：简易版，用标签包裹 */
function highlightText(text: string, keyword: string): string {
  if (!keyword || !keyword.trim()) return escapeHtml(text)
  const escaped = escapeHtml(text)
  const safeKw = keyword.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(${safeKw})`, 'gi')
  return escaped.replace(regex, '<em style="color:#6366f1;font-style:normal;font-weight:600">$1</em>')
}

/** 为 ES 返回的 <em> 高亮标签添加内联样式（mini 端 rich-text 不支持 CSS 选择器） */
function styleEsHighlight(html: string): string {
  return html.replace(/<em>/g, '<em style="color:#6366f1;font-style:normal;font-weight:600">')
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}
</script>

<template>
  <view class="chat-list-page">
    <NavBar title="AI 助手">
      <template #right>
        <view class="chat-list-page__actions">
          <view class="chat-list-page__search" @tap="openSearch">
            <text class="chat-list-page__search-icon">&#x1F50D;</text>
          </view>
          <view class="chat-list-page__add" @tap="openNewSession">
            <text class="chat-list-page__add-icon">+</text>
          </view>
        </view>
      </template>
    </NavBar>

    <!-- 会话列表 -->
    <scroll-view
      scroll-y
      class="chat-list-page__list"
      refresher-enabled
      :refresher-triggered="isRefreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="onScrollToLower"
    >
      <!-- 空状态 -->
      <EmptyState
        v-if="chatStore.sessions.length === 0 && !isRefreshing"
        icon="💬"
        title="暂无对话"
        subtitle="点击右上角 + 开始新对话"
      />

      <!-- 会话卡片 -->
      <view v-else class="chat-list-page__cards">
        <SessionCard
          v-for="session in chatStore.sessions"
          :key="session.sessionId"
          :session="session"
          :is-active="session.sessionId === chatStore.currentSessionId"
          @select="handleSelectSession"
          @delete="handleDeleteSession"
        />
      </view>

      <!-- 加载更多会话 -->
      <view v-if="chatStore.sessionLoadingMore" class="chat-list-page__load-more">
        <text class="chat-list-page__load-more-text">加载中...</text>
      </view>
    </scroll-view>

    <!-- 新建会话弹窗 -->
    <view v-if="showNewDialog" class="chat-list-page__mask" @tap="showNewDialog = false">
      <view class="chat-list-page__dialog" @tap.stop>
        <!-- ... existing new session dialog content ... -->
        <text class="dialog__title">新建对话</text>

        <text class="dialog__label">选择模型</text>
        <view class="dialog__models">
          <view
            class="dialog__model-item"
            :class="{ 'dialog__model-item--active': newModelId === 'deepseek' }"
            @tap="selectModel('deepseek')"
          >
            <text>DeepSeek</text>
          </view>
          <view
            class="dialog__model-item"
            :class="{ 'dialog__model-item--active': newModelId === 'qwen' }"
            @tap="selectModel('qwen')"
          >
            <text>通义千问</text>
          </view>
          <view
            class="dialog__model-item"
            :class="{ 'dialog__model-item--active': newModelId === 'ollama' }"
            @tap="selectModel('ollama')"
          >
            <text>Ollama 本地</text>
          </view>
        </view>

        <text class="dialog__label">会话标题（可选）</text>
        <input
          v-model="newTitle"
          class="dialog__input"
          placeholder="留空则自动生成"
          maxlength="128"
        />

        <view class="dialog__actions">
          <button class="dialog__btn dialog__btn--cancel" @tap="showNewDialog = false">
            取消
          </button>
          <button
            class="dialog__btn dialog__btn--confirm"
            :disabled="!newModelId.trim()"
            @tap="confirmNewSession"
          >
            开始对话
          </button>
        </view>
      </view>
    </view>

    <!-- 搜索弹窗 -->
    <view v-if="showSearchDialog" class="chat-list-page__mask" @tap="showSearchDialog = false">
      <view class="chat-list-page__dialog chat-list-page__dialog--search" @tap.stop>
        <text class="dialog__title">搜索历史对话</text>

        <!-- 搜索输入 -->
        <view class="search-bar">
          <input
            v-model="searchKeyword"
            class="search-bar__input"
            placeholder="输入关键词搜索"
            confirm-type="search"
            @confirm="doSearch"
          />
          <button class="search-bar__btn" :loading="searching" @tap="doSearch">搜索</button>
        </view>

        <!-- 时间 + 角色筛选 -->
        <view class="search-filter">
          <view
            class="search-filter__item"
            :class="{ 'search-filter__item--active': searchTimeFilter === 'all' }"
            @tap="searchTimeFilter = 'all'; doSearch()"
          >
            <text>全部</text>
          </view>
          <view
            class="search-filter__item"
            :class="{ 'search-filter__item--active': searchTimeFilter === '7d' }"
            @tap="searchTimeFilter = '7d'; doSearch()"
          >
            <text>7天</text>
          </view>
          <view
            class="search-filter__item"
            :class="{ 'search-filter__item--active': searchTimeFilter === '30d' }"
            @tap="searchTimeFilter = '30d'; doSearch()"
          >
            <text>30天</text>
          </view>
          <view
            class="search-filter__item"
            :class="{ 'search-filter__item--active': searchRoleFilter === 'user' }"
            @tap="searchRoleFilter = searchRoleFilter === 'user' ? '' : 'user'; doSearch()"
          >
            <text>用户</text>
          </view>
          <view
            class="search-filter__item"
            :class="{ 'search-filter__item--active': searchRoleFilter === 'assistant' }"
            @tap="searchRoleFilter = searchRoleFilter === 'assistant' ? '' : 'assistant'; doSearch()"
          >
            <text>AI</text>
          </view>
        </view>

        <!-- 搜索结果统计 -->
        <view v-if="searchTotal > 0" class="search-summary">
          <text class="search-summary__text">找到 {{ searchTotal }} 条结果</text>
        </view>

        <!-- 搜索错误 -->
        <view v-if="searchError" class="search-error">
          <text class="search-error__text">{{ searchError }}</text>
        </view>

        <!-- 搜索空状态 -->
        <view
          v-if="!searching && searchHits.length === 0 && searchKeyword.trim()"
          class="search-empty"
        >
          <text class="search-empty__text">未找到匹配的对话记录</text>
        </view>

        <!-- 搜索结果 -->
        <scroll-view scroll-y class="search-results">
          <view
            v-for="hit in searchHits"
            :key="hit.messageId"
            class="search-item"
            @tap="jumpToHit(hit)"
          >
            <view class="search-item__head">
              <text class="search-item__role" :class="'search-item__role--' + hit.role">
                {{ hit.role === 'user' ? '用户' : 'AI' }}
              </text>
              <text class="search-item__title">{{ hit.title || '未命名会话' }}</text>
            </view>
            <rich-text
              :nodes="hit.highlight ? styleEsHighlight(hit.highlight) : highlightText(hit.content, searchKeyword)"
              class="search-item__content"
            />
          </view>
          <!-- 加载更多 -->
          <view v-if="searchHasMore" class="search-loadmore" @tap="loadMoreSearch">
            <text class="search-loadmore__text">{{ searching ? '加载中...' : '加载更多' }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.chat-list-page {
  min-height: 100vh;
  background-color: $bg-page;
}

.chat-list-page__add {
  width: 64rpx;
  height: 64rpx;
  border-radius: $radius-full;
  background-color: $color-primary;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    background-color: $color-primary-600;
  }
}

.chat-list-page__add-icon {
  font-size: $font-size-xl;
  color: $text-inverse;
  line-height: 1;
}

.chat-list-page__actions {
  display: flex;
  align-items: center;
  gap: $space-2;
}

.chat-list-page__search {
  width: 64rpx;
  height: 64rpx;
  border-radius: $radius-full;
  background-color: $bg-subtle;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    background-color: $border-default;
  }
}

.chat-list-page__search-icon {
  font-size: $font-size-md;
  line-height: 1;
}

.chat-list-page__list {
  height: calc(100vh - 88px);
  padding-top: $space-2;
  padding-bottom: $space-4;
}

.chat-list-page__cards {
  padding-bottom: $space-4;
}

.chat-list-page__load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $space-4 0;
}

.chat-list-page__load-more-text {
  font-size: 24rpx;
  color: $text-tertiary;
}

/* ============ 新建会话弹窗 ============ */
.chat-list-page__mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-list-page__dialog {
  width: 600rpx;
  background-color: $bg-card;
  border-radius: $radius-xl;
  padding: $space-6;
}

.dialog__title {
  display: block;
  font-size: $font-size-lg;
  font-weight: $font-weight-semibold;
  color: $text-primary;
  text-align: center;
  margin-bottom: $space-5;
}

.dialog__label {
  display: block;
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $text-secondary;
  margin-bottom: $space-2;
  margin-top: $space-3;
}

.dialog__models {
  display: flex;
  gap: $space-2;
}

.dialog__model-item {
  flex: 1;
  height: 72rpx;
  border: 2rpx solid $border-default;
  border-radius: $radius-lg;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $font-size-sm;
  color: $text-secondary;

  &--active {
    border-color: $color-primary;
    background-color: $color-primary-50;
    color: $color-primary;
    font-weight: $font-weight-medium;
  }
}

.dialog__input {
  width: 100%;
  height: 72rpx;
  padding: 0 $space-3;
  border: 2rpx solid $border-default;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  color: $text-primary;
}

.dialog__actions {
  display: flex;
  gap: $space-3;
  margin-top: $space-5;
}

.dialog__btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  font-weight: $font-weight-medium;
  text-align: center;

  &--cancel {
    background-color: $bg-subtle;
    color: $text-secondary;
  }

  &--confirm {
    background-color: $color-primary;
    color: $text-inverse;

    &[disabled] {
      opacity: 0.5;
    }
  }
}

/* ============ 搜索弹窗 ============ */
.chat-list-page__dialog--search {
  max-height: 85vh;
  display: flex;
  flex-direction: column;
}

.search-bar {
  display: flex;
  gap: $space-2;
  margin-bottom: $space-3;
}

.search-bar__input {
  flex: 1;
  height: 72rpx;
  padding: 0 $space-3;
  border: 2rpx solid $border-default;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  color: $text-primary;
}

.search-bar__btn {
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 $space-4;
  background-color: $color-primary;
  color: $text-inverse;
  border-radius: $radius-lg;
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  text-align: center;
  white-space: nowrap;

  &[loading] {
    opacity: 0.7;
  }
}

.search-filter {
  display: flex;
  gap: $space-2;
  margin-bottom: $space-3;
}

.search-filter__item {
  padding: $space-1 $space-3;
  border: 2rpx solid $border-default;
  border-radius: $radius-full;
  font-size: $font-size-xs;
  color: $text-secondary;

  &--active {
    border-color: $color-primary;
    background-color: $color-primary-50;
    color: $color-primary;
    font-weight: $font-weight-medium;
  }
}

.search-error {
  padding: $space-2 $space-3;
  margin-bottom: $space-2;
}

.search-error__text {
  font-size: $font-size-sm;
  color: $color-danger;
}

.search-empty {
  padding: $space-6 0;
  text-align: center;
}

.search-empty__text {
  font-size: $font-size-sm;
  color: $text-tertiary;
}

.search-results {
  max-height: 60vh;
}

.search-item {
  padding: $space-2 $space-3;
  border-bottom: 2rpx solid $border-subtle;

  &:active {
    background-color: $bg-hover;
  }
}

.search-item__head {
  display: flex;
  align-items: center;
  gap: $space-2;
  margin-bottom: 4rpx;
}

.search-item__role {
  font-size: $font-size-xs;
  padding: 2rpx $space-1;
  border-radius: $radius-sm;
  font-weight: $font-weight-medium;

  &--user {
    background-color: $bg-subtle;
    color: $text-secondary;
  }

  &--assistant {
    background-color: $color-primary-50;
    color: $color-primary;
  }
}

.search-item__title {
  font-size: $font-size-xs;
  color: $text-secondary;
  max-width: 200rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-item__content {
  font-size: $font-size-sm;
  color: $text-secondary;
  line-height: $line-height-relaxed;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.search-summary {
  padding: $space-1 $space-3;
  margin-bottom: $space-1;
}

.search-summary__text {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.search-loadmore {
  padding: $space-3 0;
  text-align: center;

  &:active {
    background-color: $bg-hover;
  }
}

.search-loadmore__text {
  font-size: $font-size-sm;
  color: $color-primary;
}
</style>
