<template>
  <div class="wf-list">
    <!-- 顶部栏 -->
    <div class="wf-list__topbar">
      <div class="wf-list__topbar-left">
        <h2 class="wf-list__title">工作流</h2>
        <span class="wf-list__count">{{ store.workflowList.length }}</span>
      </div>
      <div class="wf-list__topbar-right">
        <el-input
          v-model="searchQuery"
          placeholder="搜索工作流..."
          :prefix-icon="Search"
          clearable
          class="wf-list__search"
          size="default"
        />
        <el-button type="primary" class="wf-list__create-btn" @click="handleCreate" :loading="creating">
          <el-icon><Plus /></el-icon>
          <span>新建工作流</span>
        </el-button>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="store.listLoading" class="wf-list__loading">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <!-- 空态 -->
    <div v-else-if="!filteredList.length" class="wf-list__empty">
      <div class="wf-list__empty-icon">
        <svg width="56" height="56" viewBox="0 0 56 56" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="4" y="4" width="48" height="48" rx="12" fill="#F0F2F5"/>
          <path d="M20 22h16M20 28h10M20 34h14" stroke="#C0C4CC" stroke-width="2" stroke-linecap="round"/>
          <circle cx="38" cy="34" r="6" fill="#E6E8EB" stroke="#C0C4CC" stroke-width="1.5"/>
          <path d="M36 34h4M38 32v4" stroke="#C0C4CC" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </div>
      <p v-if="searchQuery">未找到匹配"{{ searchQuery }}"的工作流</p>
      <p v-else>暂无工作流</p>
      <el-button v-if="!searchQuery" type="primary" @click="handleCreate" :loading="creating">
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
            <el-icon :size="28"><Plus /></el-icon>
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
          <svg class="wf-card__cover-svg" viewBox="0 0 280 120" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="44" y="44" width="32" height="32" rx="8" fill="#2970FF" opacity="0.85"/>
            <path d="M76 60H112" stroke="#C8D1DB" stroke-width="1.5" stroke-linecap="round"/>
            <circle cx="128" cy="60" r="16" fill="#7C3AED" opacity="0.85"/>
            <path d="M144 60H180" stroke="#C8D1DB" stroke-width="1.5" stroke-linecap="round"/>
            <rect x="196" y="44" width="32" height="32" rx="8" fill="#F04438" opacity="0.85"/>
            <path d="M52 52l8 8-8 8" stroke="white" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.9"/>
            <path d="M204 52h16M204 60h12M204 68h8" stroke="white" stroke-width="1.5" stroke-linecap="round" opacity="0.9"/>
            <circle cx="96" cy="36" r="3" fill="#E4E7EC"/>
            <circle cx="168" cy="84" r="3" fill="#E4E7EC"/>
            <circle cx="112" cy="88" r="2" fill="#E4E7EC"/>
          </svg>
        </div>

        <!-- 信息区域 -->
        <div class="wf-card__body">
          <div class="wf-card__name-row">
            <span class="wf-card__name" :title="wf.name">{{ wf.name }}</span>
            <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, wf.workflowId)" @click.stop>
              <el-icon class="wf-card__more" :size="16"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">
                    <el-icon><EditPen /></el-icon>重命名
                  </el-dropdown-item>
                  <el-dropdown-item command="duplicate">
                    <el-icon><CopyDocument /></el-icon>复制
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided style="color: #f56c6c">
                    <el-icon><Delete /></el-icon>删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="wf-card__meta">
            <div class="wf-card__meta-left">
              <el-tag
                :type="wf.published ? 'success' : 'info'"
                size="small"
                effect="light"
                round
                class="wf-card__status"
              >
                {{ wf.published ? '已发布' : '草稿' }}
              </el-tag>
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
import { Loading, Plus, MoreFilled, EditPen, CopyDocument, Delete, Search } from '@element-plus/icons-vue'
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
    'linear-gradient(135deg, #f0f5ff 0%, #e8f4fd 50%, #f5f0ff 100%)',
    'linear-gradient(135deg, #f0fdf4 0%, #ecfeff 50%, #f0f5ff 100%)',
    'linear-gradient(135deg, #fef3f2 0%, #fef0e7 50%, #fefce8 100%)',
    'linear-gradient(135deg, #f5f3ff 0%, #f0f5ff 50%, #ecfeff 100%)',
    'linear-gradient(135deg, #ecfeff 0%, #f0fdf4 50%, #fefce8 100%)',
  ]
  let hash = 0
  for (let i = 0; i < id.length; i++) {
    hash = ((hash << 5) - hash) + id.charCodeAt(i)
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
  min-height: 100%;
  background: #f9fafb;
  padding: 28px 32px;
}

/* ============ 顶部栏 ============ */
.wf-list__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}
.wf-list__topbar-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.wf-list__title {
  font-size: 24px;
  font-weight: 700;
  color: #101828;
  margin: 0;
}
.wf-list__count {
  font-size: 14px;
  color: #98a2b3;
  font-weight: 400;
}
.wf-list__topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.wf-list__search {
  width: 240px;
}
.wf-list__create-btn {
  white-space: nowrap;
}

/* ============ 加载态 & 空态 ============ */
.wf-list__loading,
.wf-list__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px 0;
  color: #98a2b3;
  font-size: 14px;
}
.wf-list__empty-icon {
  margin-bottom: 4px;
}

/* ============ 卡片网格 ============ */
.wf-list__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

/* ============ 卡片基础 ============ */
.wf-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #eaecf0;
  cursor: pointer;
  transition: all 0.2s ease;
  overflow: hidden;
}
.wf-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  border-color: #d0d5dd;
  transform: translateY(-1px);
}

/* ============ 新建卡片 ============ */
.wf-card--create {
  border: 2px dashed #d0d5dd;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  border-radius: 12px;
  transition: all 0.2s ease;
}
.wf-card--create:hover {
  border-color: #2970ff;
  background: rgba(41, 112, 255, 0.02);
  transform: translateY(-1px);
}
.wf-card--create-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: #2970ff;
  font-size: 14px;
  font-weight: 500;
}
.wf-card--create-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(41, 112, 255, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease;
}
.wf-card--create:hover .wf-card--create-icon {
  background: rgba(41, 112, 255, 0.1);
}

/* ============ 卡片封面 ============ */
.wf-card__cover {
  height: 120px;
  position: relative;
  overflow: hidden;
}
.wf-card__cover-svg {
  width: 100%;
  height: 100%;
}

/* ============ 卡片信息 ============ */
.wf-card__body {
  padding: 14px 16px;
}
.wf-card__name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
.wf-card__name {
  font-size: 14px;
  font-weight: 600;
  color: #101828;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.wf-card__more {
  color: #98a2b3;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  flex-shrink: 0;
  transition: background 0.15s, color 0.15s;
}
.wf-card__more:hover {
  background: #f2f4f7;
  color: #344054;
}
.wf-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.wf-card__meta-left {
  display: flex;
  align-items: center;
  gap: 6px;
}
.wf-card__status {
  font-size: 11px;
}
.wf-card__version {
  font-size: 11px;
  color: #98a2b3;
  font-weight: 500;
}
.wf-card__time {
  font-size: 11px;
  color: #98a2b3;
}
</style>
