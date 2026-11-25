<template>
  <div class="wf-list">
    <!-- 装饰性顶部光晕 -->
    <div class="wf-list__glow" aria-hidden="true" />

    <!-- 顶部栏 -->
    <div class="wf-list__topbar">
      <div class="wf-list__topbar-left">
        <h2 class="wf-list__title">工作流</h2>
        <span class="wf-list__count">{{ store.workflowList.length }}</span>
      </div>
      <div class="wf-list__topbar-right">
        <el-input
          v-model="searchQuery"
          placeholder="搜索工作流名称..."
          :prefix-icon="Search"
          clearable
          class="wf-list__search"
        />
        <el-button
          type="primary"
          class="wf-list__create-btn"
          @click="handleCreate"
          :loading="creating"
        >
          <el-icon class="wf-list__create-icon"><Plus /></el-icon>
          <span>新建工作流</span>
        </el-button>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="store.listLoading" class="wf-list__loading">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      <span>正在加载工作流...</span>
    </div>

    <!-- 空态 -->
    <div v-else-if="!filteredList.length" class="wf-list__empty">
      <div class="wf-list__empty-icon">
        <svg
          width="72"
          height="72"
          viewBox="0 0 72 72"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <rect
            x="8"
            y="8"
            width="56"
            height="56"
            rx="16"
            fill="url(#wf-empty-grad)"
            opacity="0.12"
          />
          <rect x="20" y="24" width="14" height="10" rx="3" fill="#6366f1" opacity="0.55" />
          <rect x="40" y="24" width="14" height="10" rx="3" fill="#8b5cf6" opacity="0.55" />
          <path d="M34 29h6" stroke="#94a3b8" stroke-width="1.6" stroke-linecap="round" />
          <path
            d="M27 34v10a3 3 0 0 0 3 3h5"
            stroke="#94a3b8"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-dasharray="3 3"
          />
          <path
            d="M47 34v10a3 3 0 0 1-3 3h-5"
            stroke="#94a3b8"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-dasharray="3 3"
          />
          <rect x="30" y="44" width="14" height="10" rx="3" fill="#10b981" opacity="0.5" />
          <defs>
            <linearGradient
              id="wf-empty-grad"
              x1="8"
              y1="8"
              x2="64"
              y2="64"
              gradientUnits="userSpaceOnUse"
            >
              <stop stop-color="#6366f1" />
              <stop offset="1" stop-color="#8b5cf6" />
            </linearGradient>
          </defs>
        </svg>
      </div>
      <p v-if="searchQuery" class="wf-list__empty-text">未找到匹配「{{ searchQuery }}」的工作流</p>
      <p v-else class="wf-list__empty-text">还没有工作流，创建第一个开始编排吧</p>
      <el-button v-if="!searchQuery" type="primary" round @click="handleCreate" :loading="creating">
        <el-icon><Plus /></el-icon>
        创建第一个工作流
      </el-button>
    </div>

    <!-- 卡片网格 -->
    <div v-else class="wf-list__grid">
      <!-- 新建卡片 -->
      <div class="wf-card wf-card--create" @click="handleCreate">
        <div class="wf-card--create-inner">
          <div class="wf-card--create-icon">
            <el-icon :size="22"><Plus /></el-icon>
          </div>
          <span>新建工作流</span>
        </div>
      </div>

      <!-- 工作流卡片 -->
      <div
        v-for="wf in filteredList"
        :key="wf.workflowId"
        class="wf-card"
        @click="handleOpen(wf.workflowId)"
      >
        <!-- 封面区域：抽象流程图 -->
        <div class="wf-card__cover" :style="{ background: getCoverGradient(wf.workflowId) }">
          <svg
            class="wf-card__cover-svg"
            viewBox="0 0 280 120"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <rect x="44" y="44" width="32" height="32" rx="9" fill="#6366f1" opacity="0.9" />
            <path
              d="M76 60H112"
              stroke="#94a3b8"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-dasharray="3 3"
            />
            <circle cx="128" cy="60" r="16" fill="#8b5cf6" opacity="0.85" />
            <path
              d="M144 60H180"
              stroke="#94a3b8"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-dasharray="3 3"
            />
            <rect x="196" y="44" width="32" height="32" rx="9" fill="#10b981" opacity="0.85" />
            <path
              d="M52 52l8 8-8 8"
              stroke="white"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
              opacity="0.95"
            />
            <path
              d="M204 52h16M204 60h12M204 68h8"
              stroke="white"
              stroke-width="1.5"
              stroke-linecap="round"
              opacity="0.95"
            />
            <circle cx="96" cy="36" r="3" fill="#c7d2fe" />
            <circle cx="168" cy="84" r="3" fill="#c7d2fe" />
            <circle cx="112" cy="88" r="2" fill="#ddd6fe" />
            <circle cx="228" cy="28" r="2.5" fill="#a5f3fc" />
          </svg>
        </div>

        <!-- 信息区域 -->
        <div class="wf-card__body">
          <div class="wf-card__name-row">
            <span class="wf-card__name" :title="wf.name">{{ wf.name }}</span>
            <el-dropdown
              trigger="click"
              @command="(cmd: string) => handleCommand(cmd, wf.workflowId)"
              @click.stop
            >
              <span class="wf-card__more" @click.stop>
                <el-icon :size="16"><MoreFilled /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">
                    <el-icon><EditPen /></el-icon>重命名
                  </el-dropdown-item>
                  <el-dropdown-item command="duplicate">
                    <el-icon><CopyDocument /></el-icon>复制
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided class="wf-card__danger-item">
                    <el-icon><Delete /></el-icon>删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="wf-card__meta">
            <div class="wf-card__meta-left">
              <span class="wf-card__status" :class="wf.published ? 'is-published' : 'is-draft'">
                <span class="wf-card__status-dot" />
                {{ wf.published ? '已发布' : '草稿' }}
              </span>
              <span class="wf-card__version">v{{ wf.version }}</span>
            </div>
            <span class="wf-card__time">{{ formatRelativeTime(wf.updatedAt) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Loading,
  Plus,
  MoreFilled,
  EditPen,
  CopyDocument,
  Delete,
  Search,
} from '@element-plus/icons-vue'
import { useWorkflowStore } from './store'
import { updateWorkflow } from '@/api/workflow'

const router = useRouter()
const store = useWorkflowStore()
const creating = ref(false)
const searchQuery = ref('')

onMounted(() => {
  store.loadList()
})

const filteredList = computed(() => {
  if (!searchQuery.value) return store.workflowList
  const q = searchQuery.value.toLowerCase()
  return store.workflowList.filter((wf) => wf.name.toLowerCase().includes(q))
})

function getCoverGradient(id: string): string {
  const gradients = [
    'linear-gradient(135deg, #eef2ff 0%, #e0e7ff 50%, #ede9fe 100%)',
    'linear-gradient(135deg, #ecfdf5 0%, #eff6ff 50%, #eef2ff 100%)',
    'linear-gradient(135deg, #fef2f2 0%, #fff7ed 50%, #fefce8 100%)',
    'linear-gradient(135deg, #f5f3ff 0%, #eef2ff 50%, #ecfeff 100%)',
    'linear-gradient(135deg, #ecfeff 0%, #f0fdf4 50%, #fefce8 100%)',
  ]
  let hash = 0
  for (let i = 0; i < id.length; i++) {
    hash = (hash << 5) - hash + id.charCodeAt(i)
    hash |= 0
  }
  return gradients[Math.abs(hash) % gradients.length] ?? gradients[0]!
}

function formatRelativeTime(t: string): string {
  if (!t) return '-'
  const date = new Date(t)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 30) return `${diffDay} 天前`
  return date.toLocaleDateString('zh-CN')
}

async function handleCreate() {
  creating.value = true
  try {
    const result = await store.createNew()
    if (result?.workflowId) {
      router.push({ name: 'workflow-editor', params: { id: result.workflowId } })
    }
  } finally {
    creating.value = false
  }
}

function handleOpen(id: string) {
  router.push({ name: 'workflow-editor', params: { id } })
}

async function handleCommand(cmd: string, id: string) {
  if (cmd === 'delete') {
    await ElMessageBox.confirm('确定要删除此工作流吗？删除后不可恢复。', '删除工作流', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await store.removeWorkflow(id)
    ElMessage.success('已删除')
  } else if (cmd === 'rename') {
    const wf = store.workflowList.find((w) => w.workflowId === id)
    const { value } = await ElMessageBox.prompt('请输入新名称', '重命名工作流', {
      inputValue: wf?.name,
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    if (value && wf) {
      await updateWorkflow(wf.workflowId, { name: value })
      await store.loadList()
      ElMessage.success('已重命名')
    }
  } else if (cmd === 'duplicate') {
    await store.copyWorkflow(id)
    ElMessage.success('已复制')
  }
}
</script>

<style scoped>
/* ============ 页面容器 ============ */
.wf-list {
  position: relative;
  min-height: 100%;
  background: #f7f8fc;
  padding: 32px 40px 48px;
  overflow: hidden;
}
.wf-list__glow {
  position: absolute;
  top: -120px;
  right: -80px;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(99, 102, 241, 0.08) 0%,
    rgba(139, 92, 246, 0.04) 45%,
    transparent 70%
  );
  pointer-events: none;
}

/* ============ 顶部栏 ============ */
.wf-list__topbar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}
.wf-list__topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.wf-list__title {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  margin: 0;
  letter-spacing: -0.3px;
}
.wf-list__count {
  min-width: 24px;
  height: 22px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #4f46e5;
  background: #eef2ff;
  border-radius: 999px;
}
.wf-list__topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.wf-list__search {
  width: 260px;
}
.wf-list__search :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 0 0 1px #e5e7eb inset;
  transition: box-shadow 0.2s;
}
.wf-list__search :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c7d2fe inset;
}
.wf-list__search :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 2px rgba(99, 102, 241, 0.35) inset,
    0 0 0 3px rgba(99, 102, 241, 0.12);
}
.wf-list__create-btn {
  white-space: nowrap;
  border: none;
  border-radius: 10px;
  padding: 10px 18px;
  font-weight: 600;
  background: linear-gradient(135deg, #6366f1 0%, #7c3aed 100%);
  box-shadow:
    0 2px 8px rgba(99, 102, 241, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.18);
  transition:
    box-shadow 0.2s,
    transform 0.15s;
}
.wf-list__create-btn:hover {
  transform: translateY(-1px);
  box-shadow:
    0 4px 14px rgba(99, 102, 241, 0.45),
    inset 0 1px 0 rgba(255, 255, 255, 0.18);
}
.wf-list__create-btn:active {
  transform: translateY(0);
}
.wf-list__create-icon {
  margin-right: 6px;
  font-weight: 700;
}

/* ============ 加载态 & 空态 ============ */
.wf-list__loading,
.wf-list__empty {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 96px 0;
  color: #9ca3af;
  font-size: 14px;
}
.wf-list__loading {
  color: #6366f1;
}
.wf-list__empty-icon {
  margin-bottom: 4px;
  opacity: 0.9;
}
.wf-list__empty-text {
  margin: 0;
  color: #6b7280;
}

/* ============ 卡片网格 ============ */
.wf-list__grid {
  position: relative;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

/* ============ 卡片基础 ============ */
.wf-card {
  background: #fff;
  border-radius: 14px;
  border: 1px solid #eef0f4;
  cursor: pointer;
  transition:
    transform 0.22s cubic-bezier(0.34, 1.4, 0.64, 1),
    box-shadow 0.22s ease,
    border-color 0.22s ease;
  overflow: hidden;
}
.wf-card:hover {
  box-shadow:
    0 12px 28px -6px rgba(17, 24, 39, 0.12),
    0 4px 10px -2px rgba(17, 24, 39, 0.06);
  border-color: #e0e7ff;
  transform: translateY(-3px);
}

/* ============ 新建卡片 ============ */
.wf-card--create {
  border: 1.5px dashed #d1d5db;
  background: rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  border-radius: 14px;
  transition:
    border-color 0.2s,
    background 0.2s,
    transform 0.22s cubic-bezier(0.34, 1.4, 0.64, 1),
    box-shadow 0.2s;
}
.wf-card--create:hover {
  border-color: #6366f1;
  border-style: solid;
  background: #eef2ff;
  transform: translateY(-3px);
  box-shadow: 0 8px 20px -6px rgba(99, 102, 241, 0.25);
}
.wf-card--create-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #6366f1;
  font-size: 14px;
  font-weight: 600;
}
.wf-card--create-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, #6366f1 0%, #7c3aed 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.35);
  transition: transform 0.22s cubic-bezier(0.34, 1.4, 0.64, 1);
}
.wf-card--create:hover .wf-card--create-icon {
  transform: scale(1.08) rotate(90deg);
}

/* ============ 卡片封面 ============ */
.wf-card__cover {
  height: 120px;
  position: relative;
  overflow: hidden;
}
.wf-card__cover::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 60%, rgba(255, 255, 255, 0.25) 100%);
  pointer-events: none;
}
.wf-card__cover-svg {
  width: 100%;
  height: 100%;
  transition: transform 0.35s ease;
}
.wf-card:hover .wf-card__cover-svg {
  transform: scale(1.04);
}

/* ============ 卡片信息 ============ */
.wf-card__body {
  padding: 14px 16px 16px;
}
.wf-card__name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}
.wf-card__name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.wf-card__more {
  color: #9ca3af;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  transition:
    background 0.15s,
    color 0.15s;
}
.wf-card__more:hover {
  background: #f3f4f6;
  color: #374151;
}
.wf-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.wf-card__meta-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wf-card__status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 999px;
  line-height: 18px;
}
.wf-card__status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.wf-card__status.is-published {
  color: #059669;
  background: #ecfdf5;
}
.wf-card__status.is-draft {
  color: #d97706;
  background: #fffbeb;
}
.wf-card__version {
  font-size: 11px;
  color: #9ca3af;
  font-weight: 600;
  font-family: var(--font-mono);
}
.wf-card__time {
  font-size: 11px;
  color: #9ca3af;
}
</style>
