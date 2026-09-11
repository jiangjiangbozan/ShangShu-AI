<script setup lang="ts">
/**
 * 聊天对话页
 *
 * 对标 Web 端 ChatView 的主对话区。
 * 组装 NavBar + MessageList + MessageInput。
 * 通过 URL 参数 sessionId 定位会话。
 */
import { onMounted, ref, computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import NavBar from '@/components/common/NavBar.vue'
import MessageList from '@/components/chat/MessageList.vue'
import MessageInput from '@/components/chat/MessageInput.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const chatStore = useChatStore()

/** 当前会话 ID（从 URL 参数获取） */
const sessionId = ref<number | null>(null)
/** 搜索跳转携带的高亮消息 ID */
const highlightMessageId = ref<number | undefined>(undefined)

/** 当前会话标题 */
const sessionTitle = computed(() => {
  const session = chatStore.sessions.find((s) => s.sessionId === sessionId.value)
  return session?.title || '对话'
})

onMounted(() => {
  // 获取页面参数
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1]
  const options = (currentPage as any)?.options || {}
  const id = Number(options.sessionId)
  if (id && !Number.isNaN(id)) {
    sessionId.value = id
    chatStore.selectSession(id)
    chatStore.fetchMessages(id)
  }
  // 读取搜索跳转携带的高亮消息 ID
  const msgId = Number(options.highlightMessageId)
  if (msgId && !Number.isNaN(msgId)) {
    highlightMessageId.value = msgId
  }
})
</script>

<template>
  <view class="conversation-page">
    <NavBar :title="sessionTitle" :show-back="true" />

    <!-- 无会话 ID -->
    <EmptyState
      v-if="!sessionId"
      icon="💬"
      title="请先选择会话"
      subtitle="从会话列表中选择一个对话"
    />

    <!-- 对话区 -->
    <template v-else>
      <MessageList :session-id="sessionId" :highlight-message-id="highlightMessageId" />
      <MessageInput :session-id="sessionId" />
    </template>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.conversation-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $bg-page;
}
</style>
