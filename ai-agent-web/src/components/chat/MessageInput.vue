<script setup lang="ts">
/**
 * 消息输入框组件（每会话独立草稿）
 *
 * 支持 Enter 发送、Shift+Enter 换行、发送按钮、停止生成按钮。
 * 输入内容绑定到 chatStore.sessionDrafts[currentSessionId]，切换会话时不会丢失草稿。
 */
import { computed, ref } from 'vue'
import { ElButton, ElInput, ElSwitch, ElTooltip } from 'element-plus'
import { Promotion, CircleClose } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const inputRef = ref<InstanceType<typeof ElInput>>()

/**
 * 双向绑定当前会话的草稿。
 * - get: 从 store 读取当前 sessionId 对应的草稿
 * - set: 写回 store 的 sessionDrafts map
 * 切换会话时 currentSessionId 变化，computed 自动重算，输入框显示对应会话的草稿。
 */
const inputText = computed<string>({
  get() {
    const sid = chatStore.currentSessionId
    return sid == null ? '' : chatStore.getDraft(sid)
  },
  set(v: string) {
    const sid = chatStore.currentSessionId
    if (sid == null) return
    chatStore.setDraft(sid, v)
  },
})

/** 发送消息 */
function handleSend() {
  const text = inputText.value.trim()
  if (!text || chatStore.isStreaming) return
  if (chatStore.currentSessionId == null) return
  // sendMessage 内部会调用 clearDraft(currentSessionId) 清空草稿
  chatStore.sendMessage(text)
}

/** 键盘事件 */
function handleKeydown(event: Event) {
  const keyboardEvent = event as KeyboardEvent
  if (keyboardEvent.key === 'Enter' && !keyboardEvent.shiftKey) {
    event.preventDefault()
    handleSend()
  }
}

/** 停止生成 */
function handleStop() {
  chatStore.stopStreaming()
}

/** 网络搜索增强开关（读写 store，切换时由 store 持久化到 localStorage） */
const webSearchEnabled = computed<boolean>({
  get: () => chatStore.webSearchEnabled,
  set: () => chatStore.toggleWebSearch(),
})

</script>

<template>
  <div class="message-input">
    <!-- 功能开关行：网络搜索增强 -->
    <div class="input-toggles">
      <el-tooltip
        content="开启后发送消息会先搜索网络，将最新信息注入回答；后端未配置搜索服务时自动忽略"
        placement="top-start"
      >
        <div class="web-search-toggle">
          <el-switch v-model="webSearchEnabled" size="small" />
          <span class="toggle-label">🌐 网络搜索</span>
        </div>
      </el-tooltip>
    </div>
    <div class="input-wrapper">
      <el-input
        ref="inputRef"
        v-model="inputText"
        type="textarea"
        :rows="1"
        :autosize="{ minRows: 1, maxRows: 5 }"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        :disabled="chatStore.isStreaming"
        @keydown="handleKeydown"
      />
      <div class="input-actions">
        <el-button
          v-if="chatStore.isStreaming"
          type="danger"
          :icon="CircleClose"
          @click="handleStop"
        >
          停止生成
        </el-button>
        <el-button
          v-else
          type="primary"
          :icon="Promotion"
          :disabled="!inputText.trim()"
          @click="handleSend"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-input {
  padding: var(--space-4);
  border-top: 1px solid var(--border-default);
  background: var(--bg-card);
}

.input-toggles {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
  min-height: 24px;
}

.web-search-toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  user-select: none;
}

.toggle-label {
  font-size: 13px;
  color: var(--text-secondary);
}


.input-wrapper {
  display: flex;
  gap: var(--space-3);
  align-items: flex-end;
}

.input-wrapper :deep(.el-textarea) {
  flex: 1;
}

.input-wrapper :deep(.el-textarea__inner) {
  border-radius: var(--radius-md);
  resize: none;
  min-height: 40px;
  font-size: 14px;
  line-height: 1.5;
}

.input-actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}
</style>