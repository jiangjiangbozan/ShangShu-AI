<script setup lang="ts">
/**
 * 消息输入框组件
 *
 * 对标 Web 端 MessageInput.vue。
 * 自适应高度 textarea + 发送/停止按钮。
 */
import { ref, watch } from 'vue'
import { useChatStore } from '@/stores/chat'

const props = defineProps<{
  sessionId: number
}>()

const chatStore = useChatStore()

/** 输入内容 */
const inputText = ref('')
/** textarea 高度 */
const textareaHeight = ref(72)

/** 从 store 恢复草稿 */
inputText.value = chatStore.getDraft(props.sessionId)

/** 监听输入变化，同步草稿 */
watch(inputText, (val) => {
  chatStore.setDraft(props.sessionId, val)
  // 自适应高度：每行约 36rpx，最小 72rpx，最大 240rpx
  const lines = val.split('\n').length
  textareaHeight.value = Math.min(Math.max(72, lines * 48), 240)
})

/** 发送消息 */
function handleSend() {
  const text = inputText.value.trim()
  if (!text) return
  if (chatStore.isStreaming) return

  chatStore.sendMessage(text)
  inputText.value = ''
  textareaHeight.value = 72
}

/** 停止生成 */
function handleStop() {
  chatStore.stopStreaming()
}

/** 输入确认（回车发送） */
function handleConfirm() {
  handleSend()
}
</script>

<template>
  <view class="message-input">
    <view class="message-input__bar">
      <textarea
        v-model="inputText"
        class="message-input__textarea"
        :style="{ height: textareaHeight + 'rpx' }"
        placeholder="输入消息..."
        :maxlength="4000"
        :auto-height="false"
        :show-confirm-bar="false"
        :disable-default-padding="true"
        confirm-type="send"
        @confirm="handleConfirm"
      />

      <!-- 发送/停止按钮 -->
      <view
        v-if="chatStore.isStreaming"
        class="message-input__btn message-input__btn--stop"
        @tap="handleStop"
      >
        <text class="message-input__btn-text">停止</text>
      </view>
      <view
        v-else
        class="message-input__btn"
        :class="{ 'message-input__btn--active': inputText.trim() }"
        @tap="handleSend"
      >
        <text class="message-input__btn-text">发送</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';
@import '@/styles/mixins.scss';

.message-input {
  padding: $space-2 $space-3;
  background-color: $bg-card;
  border-top: 2rpx solid $border-default;
  @include safe-area-bottom;
}

.message-input__bar {
  display: flex;
  align-items: flex-end;
  gap: $space-2;
}

.message-input__textarea {
  flex: 1;
  min-height: 72rpx;
  max-height: 240rpx;
  padding: $space-2 $space-3;
  border: 2rpx solid $border-default;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  color: $text-primary;
  background-color: $bg-page;
  line-height: $line-height-normal;
}

.message-input__btn {
  min-width: 100rpx;
  height: 72rpx;
  border-radius: $radius-lg;
  background-color: $bg-subtle;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &--active {
    background-color: $color-primary;

    .message-input__btn-text {
      color: $text-inverse;
    }
  }

  &--stop {
    background-color: $color-danger;

    .message-input__btn-text {
      color: $text-inverse;
    }
  }

  &:active {
    opacity: 0.85;
  }
}

.message-input__btn-text {
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $text-tertiary;
}

.message-input__btn--active .message-input__btn-text,
.message-input__btn--stop .message-input__btn-text {
  color: $text-inverse;
}
</style>
