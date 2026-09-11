<script setup lang="ts">
/**
 * 消息列表组件
 *
 * 展示会话消息列表，自动滚动到底部；支持向上滚动加载更早历史（Dify 式游标分页），
 * 加载更早消息时通过 scrollHeight 差值恢复滚动位置，避免视口跳动。
 */
import { ref, watch, nextTick, onMounted } from 'vue'
import { ElScrollbar, ElIcon, ElButton } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import MessageBubble from './MessageBubble.vue'

const chatStore = useChatStore()

const scrollRef = ref<InstanceType<typeof ElScrollbar>>()
const isAutoScroll = ref(true)

/** 滚动到底部 */
async function scrollToBottom() {
  await nextTick()
  if (scrollRef.value && isAutoScroll.value) {
    const wrap = scrollRef.value.wrapRef
    if (wrap) {
      wrap.scrollTop = wrap.scrollHeight
    }
  }
}

/**
 * 加载更早消息，并保持当前视口位置。
 *
 * 在追加更早消息前记录 scrollHeight，追加后把 scrollTop 增加
 * (新 scrollHeight - 旧 scrollHeight)，使原本可见的首条消息位置不变。
 */
async function loadOlder() {
  if (chatStore.messageLoadingOlder || !chatStore.messageHasMore) return
  const wrap = scrollRef.value?.wrapRef
  if (!wrap) return
  const prevScrollHeight = wrap.scrollHeight
  await chatStore.loadOlderMessages()
  await nextTick()
  if (scrollRef.value?.wrapRef) {
    const after = scrollRef.value.wrapRef
    after.scrollTop = after.scrollHeight - prevScrollHeight
  }
}

/** 监听滚动事件：判断是否自动滚动、是否到达顶部需要加载更早消息 */
function handleScroll({ scrollTop }: { scrollTop: number }) {
  const wrap = scrollRef.value?.wrapRef
  if (!wrap) return
  const threshold = 80
  isAutoScroll.value = wrap.scrollHeight - scrollTop - wrap.clientHeight < threshold
  // 到达顶部且有更早历史时加载（store 内部有 loading/hasMore 防重）
  if (scrollTop < 40) {
    loadOlder()
  }
}

/** 切换会话：重置自动滚动并定位到底部 */
watch(
  () => chatStore.currentSessionId,
  () => {
    isAutoScroll.value = true
    scrollToBottom()
  },
)

/** 末尾新增消息（用户发送 / 流式完成 / 首屏加载）时自动滚动到底部 */
watch(
  () => chatStore.messages[chatStore.messages.length - 1]?.messageId,
  () => scrollToBottom(),
)

/** 监听消息内容变化（流式更新），自动滚动 */
watch(
  () => {
    const msgs = chatStore.messages
    if (msgs.length === 0) return ''
    return msgs[msgs.length - 1]?.content ?? ''
  },
  () => scrollToBottom(),
)

onMounted(() => scrollToBottom())
</script>

<template>
  <div class="message-list">
    <el-scrollbar ref="scrollRef" class="message-scrollbar" @scroll="handleScroll">
      <!-- 加载更早消息入口（滚动到顶部自动触发） -->
      <div v-if="chatStore.messageHasMore && !chatStore.loading" class="message-load-older">
        <el-button
          text
          size="small"
          :loading="chatStore.messageLoadingOlder"
          @click="loadOlder"
        >
          {{ chatStore.messageLoadingOlder ? '加载中...' : '加载更早消息' }}
        </el-button>
      </div>

      <!-- 加载中 -->
      <div v-if="chatStore.loading" class="message-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <!-- 空状态 -->
      <div v-else-if="chatStore.messages.length === 0" class="message-empty">
        <p>开始一段新对话吧</p>
      </div>

      <!-- 消息列表 -->
      <div v-else class="message-items">
        <MessageBubble
          v-for="msg in chatStore.messages"
          :key="msg.messageId"
          :message="msg"
        />
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.message-scrollbar {
  flex: 1;
}

.message-load-older {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-2) 0;
}

.message-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-10);
  color: var(--text-tertiary);
  font-size: 14px;
}

.message-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-tertiary);
  font-size: 15px;
}

.message-items {
  padding: var(--space-2) 0;
}
</style>
