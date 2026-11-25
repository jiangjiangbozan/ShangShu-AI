<template>
  <div class="page">
    <el-card shadow="never" class="setup-card">
      <div class="title-bar">
        <span class="title">应用调试</span>
        <span class="sub">
          使用发布时返回的明文 API Key 调用统一入口
          <code>POST /api/v1/app/{appKey}/chat</code>（OpenAI Chat Completions 兼容），
          支持多轮上下文
        </span>
      </div>
      <el-form inline>
        <el-form-item label="应用">
          <el-select
            v-model="selectedId"
            style="width: 220px"
            placeholder="选择应用（可选，仅记录类型）"
            clearable
            filterable
          >
            <el-option v-for="a in apps" :key="a.id" :label="`${a.name} · ${a.type}`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" required>
          <el-input
            v-model="apiKey"
            style="width: 380px"
            placeholder="粘贴发布时保存的明文 API Key（appKey）"
            show-password
          />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="chat-card">
      <div class="messages" ref="msgBox">
        <div v-if="messages.length === 0" class="empty">
          选择应用并粘贴 API Key 后，输入消息开始调试
        </div>
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">
            <div class="meta">{{ m.role === 'user' ? '用户' : '应用' }}</div>
            <div class="content">{{ m.content }}</div>
          </div>
        </div>
        <div v-if="sending" class="msg assistant">
          <div class="bubble">
            <div class="meta">应用</div>
            <div class="content">思考中…</div>
          </div>
        </div>
      </div>

      <div class="input-bar">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="2"
          placeholder="输入消息，Enter 发送 / Shift+Enter 换行"
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="sending" :disabled="!apiKey || !draft.trim()" @click="send">
          发送
        </el-button>
        <el-button :disabled="messages.length === 0" @click="clear">清空会话</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { chatWithApp, listApps, type AiApp } from '@/api/apps'

const route = useRoute()

const apps = ref<AiApp[]>([])
const selectedId = ref<number | null>(null)
const apiKey = ref(typeof route.query.key === 'string' ? route.query.key : '')

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

const messages = ref<ChatMessage[]>([])
const draft = ref('')
const sending = ref(false)
const msgBox = ref<HTMLDivElement>()

/** 从应用列表带 appId 跳入时自动选中 */
watch(
  () => route.query.appId,
  (v) => {
    const id = Number(v)
    if (id) selectedId.value = id
  },
  { immediate: true },
)

async function send() {
  const text = draft.value.trim()
  if (!text || !apiKey.value) return
  messages.value.push({ role: 'user', content: text })
  draft.value = ''
  await scrollBottom()
  sending.value = true
  try {
    // OpenAI 兼容：完整 messages 数组传递（多轮上下文）
    const resp = await chatWithApp(apiKey.value, {
      messages: messages.value.map((m) => ({ role: m.role, content: m.content })),
    })
    if (resp.error) {
      messages.value.push({ role: 'assistant', content: `调用失败：${resp.error.message}` })
      ElMessage.error(resp.error.message)
    } else {
      const content = resp.choices?.[0]?.message?.content ?? '(空响应)'
      messages.value.push({ role: 'assistant', content })
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : '请求失败'
    messages.value.push({ role: 'assistant', content: `请求异常：${message}` })
  } finally {
    sending.value = false
    await scrollBottom()
  }
}

async function scrollBottom() {
  await nextTick()
  msgBox.value?.scrollTo({ top: msgBox.value.scrollHeight })
}

function clear() {
  messages.value = []
}

onMounted(async () => {
  try {
    const data = await listApps(1, 100)
    apps.value = data.records ?? []
  } catch {
    apps.value = []
  }
})
</script>

<style scoped>
.setup-card {
  margin-bottom: 16px;
}

.title-bar {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.sub {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.sub code {
  background: var(--el-fill-color-light);
  padding: 0 4px;
  border-radius: 4px;
}

.chat-card {
  display: flex;
  flex-direction: column;
}

.messages {
  height: 420px;
  overflow-y: auto;
  padding: 8px 4px;
}

.empty {
  text-align: center;
  color: var(--el-text-color-secondary);
  padding-top: 180px;
}

.msg {
  display: flex;
  margin-bottom: 12px;
}

.msg.user {
  justify-content: flex-end;
}

.msg.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 72%;
  border-radius: 8px;
  padding: 8px 12px;
}

.msg.user .bubble {
  background: var(--el-color-primary-light-9);
}

.msg.assistant .bubble {
  background: var(--el-fill-color-light);
}

.meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.content {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.input-bar {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-bar .el-input {
  flex: 1;
}
</style>
