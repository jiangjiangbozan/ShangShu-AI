<script setup lang="ts">
/**
 * 会话列表侧边栏组件
 *
 * 展示用户的所有会话，支持新建、切换、删除。
 */
import { computed, ref } from 'vue'
import { ElButton, ElIcon, ElPopconfirm, ElScrollbar } from 'element-plus'
import { Plus, Delete, ChatLineSquare, Loading } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import type { SessionResp } from '@/api/chat'

const chatStore = useChatStore()

/** el-scrollbar 实例，用于读取滚动容器尺寸（onScroll 事件只携带 scrollTop/scrollLeft） */
const scrollbarRef = ref<InstanceType<typeof ElScrollbar>>()

const props = defineProps<{
  /** 当前选中的会话 ID */
  modelValue: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [sessionId: number | null]
  'new-session': []
  /** 选择会话事件：父组件需在此时调用 fetchMessages 加载历史消息 */
  select: [sessionId: number]
}>()

/** 会话列表 */
const sessions = computed(() => chatStore.sessions)

/** 当前选中 */
const currentId = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

/** 选择会话：先更新本地 v-model，再 emit select 让父组件加载消息 */
function selectSession(session: SessionResp) {
  currentId.value = session.sessionId
  chatStore.selectSession(session.sessionId)
  emit('select', session.sessionId)
}

/** 删除会话 */
async function handleDelete(sessionId: number) {
  await chatStore.removeSession(sessionId)
}

/**
 * 滚动到会话列表底部时加载更多（游标分页追加）。
 * store 内部有 loading/hasMore 防重。
 * el-scrollbar 的 onScroll 只携带 { scrollTop, scrollLeft }，
 * 高度信息需从 wrapRef（内部滚动容器 DOM）读取。
 */
function handleScroll({ scrollTop }: { scrollTop: number }) {
  const wrap = scrollbarRef.value?.wrapRef
  if (!wrap) return
  const { scrollHeight, clientHeight } = wrap
  if (scrollHeight - scrollTop - clientHeight < 60) {
    chatStore.loadMoreSessions()
  }
}

/** 格式化时间 */
function formatTime(dateStr: string): string {
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins}分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}小时前`
  return d.toLocaleDateString('zh-CN')
}
</script>

<template>
  <div class="session-list">
    <!-- 新建会话按钮 -->
    <div class="session-list-header">
      <el-button type="primary" :icon="Plus" class="new-session-btn" @click="emit('new-session')">
        新建对话
      </el-button>
    </div>

    <!-- 会话列表 -->
    <el-scrollbar ref="scrollbarRef" class="session-scrollbar" @scroll="handleScroll">
      <div v-if="sessions.length === 0" class="session-empty">
        <el-icon :size="32"><ChatLineSquare /></el-icon>
        <p>暂无对话</p>
        <p class="text-secondary">点击上方按钮开始新对话</p>
      </div>
      <div
        v-for="session in sessions"
        :key="session.sessionId"
        class="session-item"
        :class="{ active: currentId === session.sessionId }"
        @click="selectSession(session)"
      >
        <div class="session-info">
          <div class="session-title">{{ session.title || '新对话' }}</div>
          <div class="session-meta">
            <span class="session-model">{{ session.modelId }}</span>
            <span class="session-time">{{ formatTime(session.updateTime) }}</span>
          </div>
        </div>
        <el-popconfirm
          title="确定删除此会话？"
          confirm-button-text="删除"
          cancel-button-text="取消"
          @confirm="handleDelete(session.sessionId)"
          @click.stop
        >
          <template #reference>
            <el-button text size="small" :icon="Delete" class="delete-btn" />
          </template>
        </el-popconfirm>
      </div>
      <!-- 加载更多会话 -->
      <div v-if="chatStore.sessionLoadingMore" class="session-load-more">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped>
.session-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-card);
  border-right: 1px solid var(--border-default);
}

.session-list-header {
  padding: var(--space-4);
  border-bottom: 1px solid var(--border-default);
}

.new-session-btn {
  width: 100%;
}

.session-scrollbar {
  flex: 1;
}

.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-10) var(--space-4);
  color: var(--text-tertiary);
  text-align: center;
}

.session-load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-4);
  color: var(--text-tertiary);
  font-size: 12px;
}
.session-empty p {
  margin: var(--space-2) 0 0;
  font-size: 13px;
}
.text-secondary {
  color: var(--text-quaternary);
  font-size: 12px !important;
}

.session-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  cursor: pointer;
  border-bottom: 1px solid var(--border-subtle);
  transition: background-color var(--transition-fast);
}
.session-item:hover {
  background-color: var(--bg-subtle);
}
.session-item.active {
  background-color: var(--color-primary-light);
  border-left: 3px solid var(--color-primary);
}

.session-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
.session-title {
  font-size: 14px;
  font-weight: var(--font-weight-medium);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-meta {
  display: flex;
  gap: var(--space-2);
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
}
.session-model {
  color: var(--color-primary);
}

.delete-btn {
  opacity: 0;
  transition: opacity var(--transition-fast);
}
.session-item:hover .delete-btn {
  opacity: 1;
}
</style>