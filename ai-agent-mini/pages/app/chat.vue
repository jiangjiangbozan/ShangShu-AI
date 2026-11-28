<script setup lang="ts">
/**
 * 公开应用聊天页
 *
 * 由扫码 scene 或分享路径进入，只按 publicCode 获取公开配置并调用 SSE 聊天入口。
 * 会话内容仅保存在当前页面，不写入公共知识库。
 */
import { nextTick, ref } from 'vue'
import {
  onLoad,
  onShareAppMessage,
  onShareTimeline,
  onUnload,
} from '@dcloudio/uni-app'
import NavBar from '@/components/common/NavBar.vue'
import {
  getPublicApp,
  parsePublicCode,
  sendPublicMessageStream,
} from '@/api/publicApp'
import type { PublicApp } from '@/types/publicApp'

/** 页面内消息 */
interface AppMessage {
  content: string
  role: 'user' | 'assistant'
}

/** 历史消息参与上下文的最大条数 */
const MAX_HISTORY = 12
/** 滚动到底部使用的足够大数值 */
const SCROLL_BOTTOM = 999999

const app = ref<PublicApp | null>(null)
const errorMessage = ref('')
const inputText = ref('')
const messages = ref<AppMessage[]>([])
const publicCode = ref('')
const scrollTop = ref(0)
const sending = ref(false)
let streamTask: UniApp.RequestTask | null = null

onLoad((query) => {
  publicCode.value = parsePublicCode(query.scene ?? query.publicCode)
  if (!publicCode.value) {
    errorMessage.value = '二维码或分享链接缺少应用标识'
    return
  }
  loadApp()
})

onUnload(() => {
  streamTask?.abort()
  streamTask = null
})

onShareAppMessage(() => ({
  title: app.value ? `来试试 ${app.value.name}` : 'ShangShu AI 应用',
  path: `/pages/app/chat?scene=${encodeURIComponent(publicCode.value)}`,
}))

onShareTimeline(() => ({
  title: app.value ? `来试试 ${app.value.name}` : 'ShangShu AI 应用',
  query: `publicCode=${encodeURIComponent(publicCode.value)}`,
}))

/** 加载公开应用配置 */
async function loadApp() {
  try {
    app.value = await getPublicApp(publicCode.value)
  } catch (error: unknown) {
    errorMessage.value = error instanceof Error ? error.message : '应用加载失败'
  }
}

/** 构造历史上下文 */
function buildHistory(): Array<{ content: string; role: string }> {
  return messages.value
    .filter((message) => message.content.length > 0)
    .slice(-MAX_HISTORY)
    .map((message) => ({ content: message.content, role: message.role }))
}

/** 滚动到底部 */
function scrollToBottom() {
  nextTick(() => {
    scrollTop.value = SCROLL_BOTTOM
  })
}

/** 发送消息 */
function sendMessage() {
  const text = inputText.value.trim()
  if (!text || sending.value || !publicCode.value) return

  const history = buildHistory()
  inputText.value = ''
  messages.value.push({ role: 'user', content: text })
  const answer: AppMessage = { role: 'assistant', content: '' }
  messages.value.push(answer)
  sending.value = true
  errorMessage.value = ''
  scrollToBottom()

  streamTask = sendPublicMessageStream(publicCode.value, text, history, {
    onMessage: (content) => {
      answer.content += content
      scrollToBottom()
    },
    onDone: () => {
      sending.value = false
      streamTask = null
      scrollToBottom()
    },
    onError: (message) => {
      sending.value = false
      streamTask = null
      if (!answer.content) answer.content = `[错误] ${message}`
      errorMessage.value = message
    },
    onClose: () => {
      sending.value = false
      streamTask = null
    },
  })
}

/** 停止生成 */
function stopMessage() {
  streamTask?.abort()
  streamTask = null
  sending.value = false
}
</script>

<template>
  <view class="public-app">
    <NavBar :title="app?.name || 'AI 应用'" show-back />

    <view v-if="errorMessage && !app" class="public-app__error">{{ errorMessage }}</view>

    <scroll-view scroll-y class="public-app__list" :scroll-top="scrollTop">
      <view v-if="!messages.length" class="public-app__empty">
        <text class="public-app__empty-title">{{ app?.name || 'AI 应用' }}</text>
        <text class="public-app__empty-tip">{{ app?.description || '开始提问吧' }}</text>
      </view>
      <view v-else class="public-app__messages">
        <view
          v-for="(message, index) in messages"
          :key="index"
          class="message"
          :class="message.role === 'user' ? 'message--user' : 'message--assistant'"
        >
          <text class="message__role">{{ message.role === 'user' ? '我' : 'AI' }}</text>
          <text class="message__content">{{ message.content || '生成中...' }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="public-app__input">
      <textarea
        v-model="inputText"
        class="public-app__textarea"
        placeholder="输入你的问题"
        :auto-height="true"
        :maxlength="2000"
      />
      <button v-if="sending" class="public-app__send" @tap="stopMessage">停止</button>
      <button v-else class="public-app__send" :disabled="!inputText.trim()" @tap="sendMessage">发送</button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.public-app {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $bg-page;
}

.public-app__error {
  margin: $space-3;
  padding: $space-3;
  color: $color-danger;
  background-color: $color-danger-soft;
  border-radius: $radius-lg;
  font-size: $font-size-sm;
}

.public-app__list {
  flex: 1;
  padding: $space-3;
}

.public-app__empty {
  padding-top: $space-10;
  text-align: center;
}

.public-app__empty-title {
  display: block;
  font-size: $font-size-lg;
  font-weight: $font-weight-semibold;
  color: $text-primary;
}

.public-app__empty-tip {
  display: block;
  margin-top: $space-2;
  font-size: $font-size-sm;
  color: $text-tertiary;
}

.public-app__messages {
  display: flex;
  flex-direction: column;
  gap: $space-3;
}

.message {
  padding: $space-3;
  border-radius: $radius-lg;
  background-color: $bg-card;
}

.message--user {
  align-self: flex-end;
  background-color: $color-primary;
}

.message__role {
  display: block;
  margin-bottom: 4rpx;
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.message--user .message__role,
.message--user .message__content {
  color: $text-inverse;
}

.message__content {
  font-size: $font-size-sm;
  color: $text-primary;
  white-space: pre-wrap;
  word-break: break-all;
}

.public-app__input {
  display: flex;
  align-items: flex-end;
  gap: $space-2;
  padding: $space-3;
  background-color: $bg-card;
  border-top: 1rpx solid $border-default;
}

.public-app__textarea {
  flex: 1;
  min-height: 72rpx;
  padding: $space-2 $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
  font-size: $font-size-sm;
}

.public-app__send {
  margin: 0;
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 $space-4;
  font-size: $font-size-sm;
  color: $text-inverse;
  background-color: $color-primary;
  border: none;
  border-radius: $radius-lg;

  &::after {
    border: none;
  }
}
</style>
