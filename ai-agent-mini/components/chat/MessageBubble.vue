<script setup lang="ts">
/**
 * 消息气泡组件
 *
 * 对标 Web 端 MessageBubble.vue。
 * 用户消息：纯文本转义显示，右对齐主色气泡。
 * AI 消息：Markdown 渲染 → rich-text 组件展示，左对齐白色气泡。
 *
 * 流式渲染策略：
 * - 流式输出期间节流更新（150ms），避免每个 chunk 都触发 parseMarkdown
 * - 流式结束后强制刷新，确保最终内容完整渲染
 * - 非最后一条消息（历史消息）直接渲染，无需节流
 */
import { ref, watch, onUnmounted, computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import { parseMarkdown } from '@/composables/useMarkdown'
import type { MessageResp } from '@/types/chat'

/** 节流间隔（毫秒）：流式期间每 150ms 最多重解析一次 Markdown */
const RENDER_THROTTLE_MS = 150

const props = defineProps<{
  message: MessageResp
  /** 是否为最后一条消息 */
  isLast: boolean
}>()

const chatStore = useChatStore()

/** 是否为用户消息 */
const isUser = computed(() => props.message.role === 'user')

/** 是否处于“思考中”状态 */
const isThinking = computed(
  () =>
    !isUser.value &&
    chatStore.isStreaming &&
    props.message.content === '' &&
    props.isLast,
)

/** 是否处于“生成中”状态 */
const isGenerating = computed(
  () =>
    !isUser.value &&
    chatStore.isStreaming &&
    props.message.content !== '' &&
    props.isLast,
)

/**
 * 渲染后的 HTML（节流版）
 *
 * 流式输出期间，content 每个 chunk 都会变化，如果每次都调用 parseMarkdown
 * 会导致：1) 长内容时性能崩溃；2) rich-text 高频更新可能丢失渲染。
 * 通过节流 + 流式结束强制刷新，兼顾性能与最终正确性。
 */
const renderedHtml = ref('')
let renderTimer: ReturnType<typeof setTimeout> | null = null
let lastRenderTime = 0

function doRender(): void {
  if (isUser.value) {
    renderedHtml.value = ''
    return
  }
  renderedHtml.value = parseMarkdown(props.message.content)
  lastRenderTime = Date.now()
}

function scheduleRender(): void {
  // 非流式状态或历史消息：立即渲染
  if (!chatStore.isStreaming || !props.isLast) {
    doRender()
    return
  }
  // 流式期间：节流渲染
  const now = Date.now()
  const elapsed = now - lastRenderTime
  if (elapsed >= RENDER_THROTTLE_MS) {
    doRender()
  } else if (!renderTimer) {
    renderTimer = setTimeout(() => {
      renderTimer = null
      doRender()
    }, RENDER_THROTTLE_MS - elapsed)
  }
}

// 监听内容变化 → 触发渲染
watch(
  () => props.message.content,
  () => scheduleRender(),
)

// 流式结束 → 强制最终渲染
watch(
  () => chatStore.isStreaming,
  (streaming) => {
    if (!streaming) {
      // 清除待执行的节流定时器
      if (renderTimer) {
        clearTimeout(renderTimer)
        renderTimer = null
      }
      doRender()
    }
  },
)

// 初始渲染
doRender()

// 组件卸载时清理
onUnmounted(() => {
  if (renderTimer) {
    clearTimeout(renderTimer)
    renderTimer = null
  }
})

/** 用户消息的纯文本（HTML 转义 + 换行符保留） */
const userText = computed(() => {
  return props.message.content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br/>')
})

/** 网络搜索来源 */
const sources = computed(() =>
  (props.message.sources ?? []).map((url, i) => ({
    index: i + 1,
    url,
    host: safeHost(url),
  })),
)

function safeHost(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

/** 复制消息内容 */
function copyContent() {
  uni.setClipboardData({
    data: props.message.content,
    success: () => {
      uni.showToast({ title: '已复制', icon: 'success' })
    },
  })
}
</script>

<template>
  <view class="message-bubble" :class="{ 'is-user': isUser, 'is-assistant': !isUser }" :data-message-id="message.messageId">
    <!-- 头像 -->
    <view class="bubble-avatar" :class="{ 'bubble-avatar--ai': !isUser }">
      <text v-if="isThinking" class="bubble-avatar__thinking">...</text>
      <text v-else class="bubble-avatar__text">{{ isUser ? '我' : 'AI' }}</text>
    </view>

    <!-- 消息体 -->
    <view class="bubble-body">
      <!-- 角色名 -->
      <view class="bubble-header">
        <text class="bubble-role">{{ isUser ? '我' : 'AI 助手' }}</text>
        <text v-if="isThinking" class="bubble-status">思考中...</text>
        <text v-else-if="isGenerating" class="bubble-status">生成中...</text>
      </view>

      <!-- 消息内容 -->
      <!-- 用户消息：纯文本气泡 -->
      <view v-if="isUser" class="bubble-content bubble-content--user">
        <rich-text :nodes="userText" class="bubble-user-text" />
      </view>

      <!-- AI 消息：Markdown 渲染 -->
      <view v-else class="bubble-content bubble-content--ai">
        <rich-text v-if="renderedHtml" :nodes="renderedHtml" class="markdown-body" />
        <text v-else-if="isThinking" class="bubble-thinking">正在思考...</text>
      </view>

      <!-- 网络搜索来源 -->
      <view v-if="sources.length" class="bubble-sources">
        <text class="sources-title">引用来源</text>
        <view v-for="s in sources" :key="s.url" class="source-item">
          <text class="source-text">[{{ s.index }}] {{ s.host }}</text>
        </view>
      </view>

      <!-- 底部操作 -->
      <view class="bubble-footer">
        <text v-if="message.latencyMs" class="bubble-latency">{{ message.latencyMs }}ms</text>
        <text class="bubble-copy" @tap="copyContent">复制</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.message-bubble {
  display: flex;
  gap: $space-2;
  padding: $space-2 $space-3;
}

.bubble-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: $radius-full;
  background-color: $bg-subtle;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &--ai {
    background-color: $color-primary-100;
  }
}

.bubble-avatar__text {
  font-size: $font-size-xs;
  font-weight: $font-weight-semibold;
  color: $text-secondary;
}

.bubble-avatar--ai .bubble-avatar__text {
  color: $color-primary;
}

.bubble-avatar__thinking {
  font-size: $font-size-base;
  color: $color-primary;
  font-weight: $font-weight-bold;
}

.bubble-body {
  flex: 1;
  min-width: 0;
}

.bubble-header {
  display: flex;
  align-items: center;
  gap: $space-1;
  margin-bottom: 4rpx;
}

.bubble-role {
  font-size: $font-size-sm;
  font-weight: $font-weight-semibold;
  color: $text-secondary;
}

.bubble-status {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

/* 用户消息气泡 */
.bubble-content--user {
  background-color: $color-primary;
  padding: $space-2 $space-3;
  border-radius: $radius-md;
  border-bottom-right-radius: $radius-sm;
  display: inline-block;
}

.bubble-user-text {
  font-size: $font-size-base;
  color: $text-inverse;
  line-height: $line-height-relaxed;
  word-break: break-word;
}

/* AI 消息气泡 */
.bubble-content--ai {
  background-color: $bg-card;
  padding: $space-2 $space-3;
  border: 2rpx solid $border-default;
  border-radius: $radius-md;
  border-bottom-left-radius: $radius-sm;
}

.bubble-thinking {
  font-size: $font-size-base;
  color: $text-tertiary;
  font-style: italic;
}

/* 网络搜索来源 */
.bubble-sources {
  display: flex;
  flex-wrap: wrap;
  gap: $space-1;
  margin-top: $space-2;
  padding: $space-2;
  background-color: $bg-subtle;
  border-radius: $radius-sm;
}

.sources-title {
  font-size: $font-size-xs;
  color: $text-secondary;
  font-weight: $font-weight-semibold;
  width: 100%;
}

.source-item {
  padding: 2rpx $space-2;
  background-color: $bg-card;
  border-radius: $radius-sm;
}

.source-text {
  font-size: 20rpx;
  color: $color-primary;
}

/* 底部 */
.bubble-footer {
  display: flex;
  align-items: center;
  gap: $space-2;
  margin-top: 4rpx;
}

.bubble-latency {
  font-size: 20rpx;
  color: $text-tertiary;
}

.bubble-copy {
  font-size: 20rpx;
  color: $text-tertiary;

  &:active {
    color: $color-primary;
  }
}

/* 用户消息右对齐 */
.is-user {
  flex-direction: row-reverse;

  .bubble-body {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
  }
}
</style>

<!--
  Markdown 渲染样式（非 scoped）
  rich-text 组件内部节点不受 scoped 样式影响，
  必须用全局样式才能渲染 p/h/pre/code/table 等标签的视觉效果。
  缺少此样式会导致 AI 回答内容"压缩"——段落无间距、标题无大小、代码块无背景。
-->
<style lang="scss">
@import '@/styles/tokens.scss';

/* ============ rich-text 内 Markdown 全局样式 ============ */
.markdown-body {
  font-size: $font-size-base;
  line-height: $line-height-relaxed;
  color: $text-primary;
  word-break: break-word;
  overflow-wrap: break-word;

  /* 段落 */
  p {
    margin: 0 0 $space-2;
    line-height: $line-height-relaxed;
  }

  /* 标题 */
  h1 { font-size: $font-size-2xl; font-weight: $font-weight-bold; margin: $space-3 0 $space-2; color: $text-primary; }
  h2 { font-size: $font-size-xl; font-weight: $font-weight-semibold; margin: $space-3 0 $space-2; color: $text-primary; }
  h3 { font-size: $font-size-lg; font-weight: $font-weight-semibold; margin: $space-3 0 $space-2; color: $text-primary; }
  h4 { font-size: $font-size-md; font-weight: $font-weight-semibold; margin: $space-2 0 $space-1; color: $text-primary; }
  h5, h6 { font-size: $font-size-base; font-weight: $font-weight-semibold; margin: $space-2 0 $space-1; color: $text-primary; }

  /* 列表 */
  ul, ol {
    padding-left: $space-5;
    margin: $space-2 0;
  }
  li {
    margin-bottom: 4rpx;
    line-height: $line-height-relaxed;
  }

  /* 引用 */
  blockquote {
    border-left: 6rpx solid $color-primary;
    padding-left: $space-3;
    margin: $space-2 0;
    color: $text-secondary;
  }

  /* 行内代码 */
  code {
    font-family: $font-mono;
    font-size: 26rpx;
  }
  .inline-code {
    background-color: $bg-subtle;
    color: $color-primary-700;
    padding: 2rpx 8rpx;
    border-radius: $radius-sm;
    font-family: $font-mono;
    font-size: 26rpx;
  }

  /* 代码块 */
  .code-block {
    background: #1e1e2e;
    border-radius: $radius-md;
    margin: $space-2 0;
    overflow: hidden;
  }
  .code-lang {
    display: block;
    padding: $space-1 $space-3;
    font-size: $font-size-xs;
    color: #9ca3af;
    background: rgba(255, 255, 255, 0.05);
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  }
  .code-block pre {
    padding: $space-3;
    overflow-x: auto;
    margin: 0;
  }
  .code-block code {
    font-family: $font-mono;
    font-size: 26rpx;
    color: #cdd6f4;
    line-height: $line-height-relaxed;
  }

  /* 表格 */
  table {
    width: 100%;
    border-collapse: collapse;
    margin: $space-2 0;
  }
  th, td {
    border: 2rpx solid $border-default;
    padding: $space-1 $space-2;
    font-size: $font-size-sm;
    text-align: left;
  }
  th {
    background-color: $bg-subtle;
    font-weight: $font-weight-semibold;
  }

  /* 分割线 */
  hr {
    border: none;
    border-top: 2rpx solid $border-default;
    margin: $space-4 0;
  }

  /* 链接 */
  a {
    color: $color-primary;
    text-decoration: none;
  }

  /* 强调 */
  strong {
    font-weight: $font-weight-bold;
  }
  em {
    font-style: italic;
  }
  del {
    text-decoration: line-through;
    color: $text-tertiary;
  }

  /* 图片 */
  img {
    max-width: 100%;
    border-radius: $radius-md;
  }
}
</style>
