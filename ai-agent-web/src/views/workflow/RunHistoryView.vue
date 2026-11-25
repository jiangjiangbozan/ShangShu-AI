<template>
  <div class="run-history">
    <div class="run-history__card">
      <div class="run-history__header">
        <div class="run-history__header-left">
          <span class="run-history__title">工作流执行记录</span>
          <span class="run-history__count">{{ total }}</span>
        </div>
        <div class="run-history__filter">
          <el-select
            v-model="filterStatus"
            placeholder="全部状态"
            clearable
            size="default"
            style="width: 150px"
            @change="onRefresh"
          >
            <el-option label="运行中" value="RUNNING" />
            <el-option label="成功" value="SUCCEEDED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="暂停" value="PAUSED" />
          </el-select>
          <el-button :icon="Refresh" @click="onRefresh">刷新</el-button>
        </div>
      </div>

      <el-table
        :data="records"
        v-loading="loading"
        class="run-history__table"
        :header-cell-style="{
          background: '#f9fafc',
          color: '#6b7280',
          fontWeight: 600,
          fontSize: '12px',
        }"
      >
        <el-table-column prop="runId" label="Run ID" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="run-history__code">{{ row.runId }}</code>
          </template>
        </el-table-column>
        <el-table-column
          prop="workflowId"
          label="工作流 ID"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="run-history__chip" :class="'is-' + (row.status || '').toLowerCase()">
              <span class="run-history__chip-dot" />
              {{ statusLabel(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.elapsedMs != null" class="run-history__mono"
              >{{ (row.elapsedMs / 1000).toFixed(1) }}s</span
            >
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="Token" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.totalTokens != null" class="run-history__mono">{{
              row.totalTokens
            }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="triggeredBy" label="触发人" width="120" />
        <el-table-column prop="createdAt" label="开始时间" width="180" />
        <el-table-column prop="finishedAt" label="结束时间" width="180" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="onViewDetail(row)">查看</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="run-history__table-empty">暂无执行记录</div>
        </template>
      </el-table>

      <div class="run-history__pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="onRefresh"
          @current-change="onRefresh"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listAllWorkflowRuns, type WorkflowRunItem } from '@/api/workflow-runs'

const router = useRouter()

const loading = ref(false)
const records = ref<WorkflowRunItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterStatus = ref<string | undefined>(undefined)

async function loadData() {
  loading.value = true
  try {
    const res = await listAllWorkflowRuns(pageNum.value, pageSize.value, filterStatus.value)
    records.value = res.records
    total.value = res.total
  } catch (err) {
    ElMessage.error('加载执行记录失败: ' + (err instanceof Error ? err.message : String(err)))
  } finally {
    loading.value = false
  }
}

function onRefresh() {
  loadData()
}

function onViewDetail(row: WorkflowRunItem) {
  router.push(`/workflow/runs/${row.runId}`)
}

function statusLabel(status: string) {
  switch (status) {
    case 'SUCCEEDED':
      return '成功'
    case 'FAILED':
      return '失败'
    case 'RUNNING':
      return '运行中'
    case 'PAUSED':
      return '暂停'
    default:
      return status
  }
}

onMounted(loadData)
</script>

<style scoped>
.run-history {
  min-height: 100%;
  padding: 24px 28px;
  background: #f7f8fc;
}
.run-history__card {
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(17, 24, 39, 0.04);
}
.run-history__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.run-history__header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.run-history__title {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.2px;
}
.run-history__count {
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
.run-history__filter {
  display: flex;
  gap: 8px;
  align-items: center;
}
.run-history__table {
  --el-table-border-color: #eef0f4;
  border-radius: 10px;
  overflow: hidden;
}
.run-history__code {
  font-family: var(--font-mono);
  font-size: 12px;
  color: #4f46e5;
  background: #eef2ff;
  padding: 2px 8px;
  border-radius: 6px;
}
.run-history__chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 9px;
  border-radius: 999px;
  line-height: 18px;
}
.run-history__chip-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.run-history__chip.is-succeeded {
  color: #059669;
  background: #ecfdf5;
}
.run-history__chip.is-failed {
  color: #dc2626;
  background: #fef2f2;
}
.run-history__chip.is-running {
  color: #d97706;
  background: #fffbeb;
}
.run-history__chip.is-paused {
  color: #6b7280;
  background: #f3f4f6;
}
.run-history__mono {
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  color: #4b5563;
}
.run-history__table-empty {
  padding: 32px 0;
  color: #9ca3af;
  font-size: 13px;
}
.run-history__pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
