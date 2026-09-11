<script setup lang="ts">
/**
 * 消息列表组件
 *
 * 对标 Web 端 MessageList.vue。
 * 使用 scroll-view 实现消息列表，支持触顶加载历史（Dify 式游标分页）、自动滚底。
 * 触顶加载更早消息时：先暂停自动滚底，测量加载前后的内容高度差，
 * 再恢复 scrollTop，避免加载过程中视口跳动。
 */
import { onMounted, ref, getCurrentInstance, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from '@/stores/chat'
import { useAutoScroll } from '@/composables/useAutoScroll'
import MessageBubble from './MessageBubble.vue'

const props = defineProps<{
  sessionId: number
  highlightMessageId?: number
}>()

const chatStore = useChatStore()
// storeToRefs 保持 ref 引用，避免 Pinia 自动解包导致 .value 为 undefined
const { messages, isStreaming } = storeToRefs(chatStore)

const { scrollTop, scrollToBottom, autoScrollEnabled } = useAutoScroll(messages, isStreaming)

/** 是否正在加载更早消息 */
const isLoadingOlder = ref(false)
/** 当前视口 scrollTop（由 @scroll 事件持续更新） */
let viewportScrollTop = 0

/** 测量消息内容区高度 */
function measureHeight(): Promise<number> {
  return new Promise((resolve) => {
    const instance = getCurrentInstance()
    const query = uni.createSelectorQuery().in(instance)
    query
      .select('.message-list__inner')
      .boundingClientRect((rect) => {
        // uni-app 类型中回调参数为 NodeInfo | NodeInfo[]，按数组归一化
        const node = Array.isArray(rect) ? rect[0] : rect
        resolve(node?.height ?? 0)
      })
      .exec()
  })
}

/**
 * 触顶加载更早消息。
 *
 * 1. 记录加载前内容高度，并暂停自动滚底（避免列表长度变化触发跳到底部）；
 * 2. 调用 store.loadOlderMessages 追加更早消息；
 * 3. 计算内容高度增量，把 scrollTop 补偿到原视口位置；
 * 4. 恢复自动滚底。
 */
async function onScrollToUpper() {
  if (isLoadingOlder.value || !chatStore.messageHasMore) return
  autoScrollEnabled.value = false
  isLoadingOlder.value = true
  try {
    const prevHeight = await measureHeight()
    await chatStore.loadOlderMessages()
    await nextTick()
    const afterHeight = await measureHeight()
    const delta = afterHeight - prevHeight
    if (delta > 0) {
      scrollTop.value = viewportScrollTop + delta
    }
  } finally {
    isLoadingOlder.value = false
  }
}

/** 记录滚动位置（供触顶加载后补偿） */
function handleScroll(e: { detail: { scrollTop: number } }) {
  viewportScrollTop = e.detail.scrollTop ?? 0
}

onMounted(() => {
  scrollToBottom()
  // 搜索跳转：定位到高亮消息
  if (props.highlightMessageId) {
    nextTick(() => {
      setTimeout(() => {
        const instance = getCurrentInstance()
        const query = uni.createSelectorQuery().in(instance)
        query
          .select(`[data-message-id="${props.highlightMessageId}"]`)
          .boundingClientRect((rect) => {
            const node = Array.isArray(rect) ? rect[0] : rect
            if (node && node.top != null) {
              // 在 scroll-view 中定位到目标消息
              const currentTop = viewportScrollTop
              scrollTop.value = currentTop + (node.top as number) - 200
            }
          })
          .exec()
      }, 300)
    })
  }
})
</script>

<template>
  <scroll-view
    scroll-y
    class="message-list"
    :scroll-top="scrollTop"
    :scroll-with-animation="!isLoadingOlder"
    @scroll="handleScroll"
    @scrolltoupper="onScrollToUpper"
  >
    <!-- 加载更早消息状态（触顶时自动触发，也可手动点击） -->
    <view
      v-if="chatStore.messageHasMore && !isLoadingOlder"
      class="message-list__history-hint"
      @tap="onScrollToUpper"
    >
      <text class="message-list__history-text">加载更早消息</text>
    </view>
    <view v-if="isLoadingOlder" class="message-list__history-hint">
      <text class="message-list__history-text">加载中...</text>
    </view>

    <view class="message-list__inner">
      <MessageBubble
        v-for="(msg, index) in chatStore.messages"
        :key="msg.messageId"
        :message="msg"
        :is-last="index === chatStore.messages.length - 1"
      />
    </view>
  </scroll-view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.message-list {
  flex: 1;
  height: 100%;
}

.message-list__inner {
  padding: $space-2 0;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.message-list__history-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $space-2 0;
}

.message-list__history-text {
  font-size: 24rpx;
  color: $text-tertiary;
}
</style>
