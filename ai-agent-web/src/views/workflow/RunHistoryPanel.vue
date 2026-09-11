<template>
  <el-drawer
    v-model="visible"
    title="运行历史"
    direction="rtl"
    size="380px"
    destroy-on-close
  >
    <div v-if="loading" class="run-panel__loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>
    <div v-else-if="!runs.length" class="run-panel__empty">
      <el-empty description="暂无运行记录" />
    </div>
    <div v-else class="run-panel__list">
      <el-card v-for="run in runs" :key="run.runId" class="run-panel__card" shadow="never">
        <div class="run-panel__header">
          <el-tag :type="statusType(run.status)" size="small">{{ statusLabel(run.status) }}</el-tag>
          <span class="run-panel__time">{{ formatTime(run.createdAt) }}</span>
        </div>
        <div class="run-panel__meta">
          <span>版本 v{{ run.version }}</span>
          <span v-if="run.elapsedMs">{{ (run.elapsedMs / 1000).toFixed(1) }}s</span>
          <span v-if="run.totalTokens">{{ run.totalTokens }} tokens</span>
        </div>
        <div v-if="run.errorMessage" class="run-panel__error">{{ run.errorMessage }}</div>
      </el-card>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { useWorkflowStore } from './store'
import { listWorkflowRuns, type WorkflowRun } from '@/api/workflow'

const store = useWorkflowStore()
const visible = ref(false)
const runs = ref<WorkflowRun[]>([])
const loading = ref(false)

watch(visible, async (v) => {
  if (v && store.workflowId) {
    loading.value = true
    try {
      runs.value = await listWorkflowRuns(store.workflowId)
    } finally {
      loading.value = false
    }
  }
})

function statusType(s: string) {
  if (s === 'succeeded') return 'success'
  if (s === 'failed') return 'danger'
  if (s === 'running') return 'warning'
  return 'info'
}

function statusLabel(s: string) {
  const map: Record<string, string> = { running: '运行中', succeeded: '成功', failed: '失败', stopped: '已停止' }
  return map[s] ?? s
}

function formatTime(t: string) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

defineExpose({ visible })
</script>

<style scoped>
.run-panel__loading,
.run-panel__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  color: #909399;
  gap: 8px;
}
.run-panel__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.run-panel__card :deep(.el-card__body) {
  padding: 12px;
}
.run-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.run-panel__time {
  font-size: 12px;
  color: #909399;
}
.run-panel__meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #606266;
}
.run-panel__error {
  margin-top: 6px;
  font-size: 12px;
  color: #f56c6c;
  word-break: break-all;
}
</style>
