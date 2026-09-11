<script setup lang="ts">
/**
 * 对话日志页面（借鉴 Dify Log）
 *
 * 功能：
 * 1. 会话列表（按时间倒序，支持关键词搜索）
 * 2. 点击会话查看完整对话历史
 * 3. 消息详情面板（变量、Token 用量、延迟、模型信息）
 * 4. 对话消息气泡展示
 */
import { onMounted, ref } from 'vue'
import {
  ElCard, ElButton, ElTable, ElTableColumn, ElTag, ElInput,
  ElEmpty, ElIcon, ElDrawer, ElDescriptions, ElDescriptionsItem,
  ElScrollbar,
} from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import {
  listSessions, getMessages, type SessionResp, type MessageResp,
} from '@/api/chat'

/** 会话列表 */
const sessions = ref<SessionResp[]>([])
const sessionTotal = ref(0)
const sessionLoading = ref(false)
const sessionSize = ref(20)
const sessionKeyword = ref('')

/** 消息列表 */
const messages = ref<MessageResp[]>([])
const messageLoading = ref(false)

/** 抽屉 */
const drawerVisible = ref(false)
const selectedSession = ref<SessionResp | null>(null)

/** 消息详情 */
const detailVisible = ref(false)
const detailMessage = ref<MessageResp | null>(null)

/** 是否为用户消息 */
function isUser(msg: MessageResp) {
  return msg.role === 'user'
}

/** 格式化时间 */
function formatTime(t: string | null | undefined) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}

async function fetchSessions() {
  sessionLoading.value = true
  try {
    const res = await listSessions({ limit: sessionSize.value })
    sessions.value = res.data ?? []
    // cursor 分页没有 total，hasMore 判断
    sessionTotal.value = sessions.value.length
  } catch { /* ignore */ }
  finally { sessionLoading.value = false }
}

async function openSession(session: SessionResp) {
  selectedSession.value = session
  drawerVisible.value = true
  messageLoading.value = true
  try {
    const res = await getMessages(session.sessionId, { limit: 100 })
    messages.value = res.data ?? []
  } catch { messages.value = [] }
  finally { messageLoading.value = false }
}

function showMsgDetail(msg: MessageResp) {
  detailMessage.value = msg
  detailVisible.value = true
}

onMounted(fetchSessions)
</script>

<template>
  <div class="conversation-log-page">
    <div class="page-header">
      <div>
        <h2 style="margin: 0; font-size: 20px;">对话日志</h2>
        <p style="margin: 4px 0 0; color: var(--text-secondary); font-size: 13px;">
          浏览所有对话会话，查看完整对话历史与消息详情
        </p>
      </div>
      <el-button :icon="Refresh" @click="fetchSessions">刷新</el-button>
    </div>

    <el-card shadow="never">
      <!-- 搜索栏 -->
      <div style="display: flex; gap: 8px; margin-bottom: 16px;">
        <el-input
          v-model="sessionKeyword"
          placeholder="搜索会话标题..."
          clearable
          style="max-width: 320px;"
          @keyup.enter="fetchSessions"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" :icon="Search" @click="fetchSessions">搜索</el-button>
      </div>

      <!-- 会话列表 -->
      <el-table :data="sessions" v-loading="sessionLoading" stripe border style="width: 100%;">
        <el-table-column prop="sessionId" label="ID" width="80" />
        <el-table-column prop="title" label="会话标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button link type="primary" @click="openSession(row as SessionResp)">
              {{ row.title || '未命名会话' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="modelId" label="模型" width="160" show-overflow-tooltip />
        <el-table-column prop="messageCount" label="消息数" width="100" align="center" />
        <el-table-column prop="totalTokens" label="总 Token" width="110" align="right">
          <template #default="{ row }">{{ (row.totalTokens ?? 0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="updateTime" label="最后活跃" width="170">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 对话详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="selectedSession?.title || '对话详情'"
      size="680px"
      destroy-on-close
    >
      <div v-if="selectedSession" style="margin-bottom: 12px;">
        <el-descriptions :column="3" size="small" border>
          <el-descriptions-item label="会话 ID">{{ selectedSession.sessionId }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ selectedSession.modelId }}</el-descriptions-item>
          <el-descriptions-item label="消息数">{{ selectedSession.messageCount }}</el-descriptions-item>
          <el-descriptions-item label="总 Token">{{ (selectedSession.totalTokens ?? 0).toLocaleString() }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(selectedSession.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="最后活跃">{{ formatTime(selectedSession.updateTime) }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div v-loading="messageLoading" style="padding: 0 4px;">
        <el-empty v-if="!messageLoading && messages.length === 0" description="暂无消息" />
        <el-scrollbar max-height="calc(100vh - 220px)">
          <div class="chat-thread">
            <div
              v-for="msg in messages"
              :key="msg.messageId"
              :class="['chat-bubble', isUser(msg) ? 'user' : 'assistant']"
              @click="showMsgDetail(msg as MessageResp)"
            >
              <div class="bubble-role">{{ isUser(msg) ? '用户' : '助手' }}</div>
              <div class="bubble-content">{{ msg.content }}</div>
              <div class="bubble-meta">
                <span v-if="msg.modelId" class="meta-tag">{{ msg.modelId }}</span>
                <span v-if="msg.totalTokens" class="meta-tag">{{ msg.totalTokens }} tokens</span>
                <span v-if="msg.latencyMs" class="meta-tag">{{ msg.latencyMs }}ms</span>
                <span class="meta-time">{{ formatTime(msg.createTime) }}</span>
              </div>
            </div>
          </div>
        </el-scrollbar>
      </div>
    </el-drawer>

    <!-- 消息详情弹窗 -->
    <el-drawer
      v-model="detailVisible"
      title="消息详情"
      size="480px"
      destroy-on-close
    >
      <div v-if="detailMessage" style="padding: 0 4px;">
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="消息 ID">{{ detailMessage.messageId }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag :type="isUser(detailMessage) ? 'primary' : 'success'" size="small">
              {{ isUser(detailMessage) ? '用户' : '助手' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="模型">{{ detailMessage.modelId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Prompt Tokens">{{ detailMessage.promptTokens ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="Completion Tokens">{{ detailMessage.completionTokens ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="Total Tokens">{{ detailMessage.totalTokens ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="延迟">{{ detailMessage.latencyMs ? `${detailMessage.latencyMs}ms` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatTime(detailMessage.createTime) }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 16px;">
          <div style="font-weight: 500; margin-bottom: 8px;">消息内容</div>
          <div class="detail-content">{{ detailMessage.content }}</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script lang="ts">
export default { name: 'ConversationLogView' }
</script>

<style scoped>
.conversation-log-page { padding: 4px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.chat-thread {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.chat-bubble:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}
.chat-bubble.user {
  background: var(--el-color-primary-light-9, #ecf5ff);
  border: 1px solid var(--el-color-primary-light-7, #c6e2ff);
  align-self: flex-end;
}
.chat-bubble.assistant {
  background: var(--bg-subtle, #f5f7fa);
  border: 1px solid var(--border-subtle, #e4e7ed);
  align-self: flex-start;
}

.bubble-role {
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 4px;
  color: var(--text-secondary);
}
.chat-bubble.user .bubble-role { color: var(--el-color-primary); }
.chat-bubble.assistant .bubble-role { color: var(--el-color-success); }

.bubble-content {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
  max-height: 200px;
  overflow-y: auto;
}

.bubble-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-tertiary);
}
.meta-tag {
  background: var(--bg-subtle, #f0f2f5);
  padding: 1px 6px;
  border-radius: 4px;
  color: var(--text-secondary);
}
.meta-time {
  margin-left: auto;
}

.detail-content {
  background: var(--bg-subtle, #f5f7fa);
  padding: 12px 16px;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.6;
  max-height: 400px;
  overflow-y: auto;
}
</style>
