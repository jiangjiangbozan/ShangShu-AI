<template>
  <el-drawer v-model="visible" title="运行历史" direction="rtl" size="400px" destroy-on-close>
    <div v-if="loading" class="run-panel__loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>
    <div v-else-if="!runs.length" class="run-panel__empty">
      <el-empty description="暂无运行记录" :image-size="88" />
    </div>
    <div v-else class="run-panel__list">
      <div v-for="run in runs" :key="run.runId" class="run-panel__card">
        <div class="run-panel__header">
          <span class="run-panel__chip" :class="'is-' + run.status">
            <span class="run-panel__chip-dot" />
            {{ statusLabel(run.status) }}
          </span>
          <span class="run-panel__time">{{ formatTime(run.createdAt) }}</span>
        </div>
        <div class="run-panel__meta">
          <span v-if="run.version !== undefined" class="run-panel__meta-item"
            >v{{ run.version }}</span
          >
          <span v-if="run.elapsedMs" class="run-panel__meta-item"
            >{{ (run.elapsedMs / 1000).toFixed(1) }}s</span
          >
          <span v-if="run.totalTokens" class="run-panel__meta-item"
            >{{ run.totalTokens }} tokens</span
          >
        </div>
        <div v-if="run.errorMessage" class="run-panel__error">{{ run.errorMessage }}</div>
      </div>
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

function statusLabel(s: string) {
  const map: Record<string, string> = {
    running: '运行中',
    succeeded: '成功',
    failed: '失败',
    stopped: '已停止',
  }
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
  color: #9ca3af;
  gap: 8px;
}
.run-panel__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.run-panel__card {
  padding: 12px 14px;
  border: 1px solid #eef0f7;
  border-radius: 12px;
  background: #fff;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.run-panel__card:hover {
  border-color: #dde1f0;
  box-shadow: 0 2px 8px rgba(17, 24, 39, 0.05);
}
.run-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.run-panel__chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 9px;
  border-radius: 999px;
  line-height: 18px;
}
.run-panel__chip-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.run-panel__chip.is-succeeded {
  color: #059669;
  background: #ecfdf5;
}
.run-panel__chip.is-failed {
  color: #dc2626;
  background: #fef2f2;
}
.run-panel__chip.is-running {
  color: #d97706;
  background: #fffbeb;
}
.run-panel__chip.is-stopped,
.run-panel__chip:not(.is-succeeded):not(.is-failed):not(.is-running) {
  color: #6b7280;
  background: #f3f4f6;
}
.run-panel__time {
  font-size: 12px;
  color: #9ca3af;
}
.run-panel__meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.run-panel__meta-item {
  font-size: 11px;
  color: #6b7280;
  background: #f4f5fb;
  padding: 1px 8px;
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
}
.run-panel__error {
  margin-top: 8px;
  font-size: 12px;
  color: #dc2626;
  background: #fef2f2;
  border-radius: 8px;
  padding: 6px 10px;
  word-break: break-all;
}
</style>
