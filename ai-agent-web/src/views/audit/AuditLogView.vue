<script setup lang="ts">
/**
 * 操作审计日志页（A6）
 *
 * 展示 @OperationLog 注解自动记录的操作日志：模块/动作/操作人/IP/参数/耗时/结果，
 * 支持多条件筛选、详情查看与按天数清理历史日志。
 */
import { onMounted, ref } from 'vue'
import {
  ElCard, ElButton, ElTable, ElTableColumn, ElTag, ElInput, ElSelect, ElOption,
  ElDatePicker, ElDialog, ElMessage, ElMessageBox, ElPagination, ElEmpty, ElDescriptions,
  ElDescriptionsItem,
} from 'element-plus'
import { Search, Delete, Refresh } from '@element-plus/icons-vue'
import {
  listAuditLogs, cleanAuditLogs, type OperationLogResp,
} from '@/api/audit-log'

const MODULE_LABELS: Record<string, string> = {
  'model-config': '模型配置',
  knowledge: '知识库',
  'prompt-template': 'Prompt 模板',
  'token-limit': '限额/限流',
  'model-route': '模型路由',
  audit: '审计',
}

const loading = ref(false)
const list = ref<OperationLogResp[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filter = ref<{ username: string; module: string; action: string; status: string; range: [string, string] | null }>({
  username: '',
  module: '',
  action: '',
  status: '',
  range: null,
})

const detailVisible = ref(false)
const detail = ref<OperationLogResp | null>(null)

function moduleLabel(m: string) {
  return MODULE_LABELS[m] ?? m
}

function statusTag(s: string) {
  return s === 'success' ? 'success' : 'danger'
}

async function load() {
  loading.value = true
  try {
    const res = await listAuditLogs({
      username: filter.value.username || undefined,
      module: filter.value.module || undefined,
      action: filter.value.action || undefined,
      status: filter.value.status || undefined,
      start: filter.value.range?.[0],
      end: filter.value.range?.[1],
      page: page.value,
      size: size.value,
    })
    list.value = res.list
    total.value = res.total
  } catch {
    /* toast */
  } finally {
    loading.value = false
  }
}

function reset() {
  filter.value = { username: '', module: '', action: '', status: '', range: null }
  page.value = 1
  load()
}

function showDetail(row: OperationLogResp) {
  detail.value = row
  detailVisible.value = true
}

async function handleClean() {
  let days: string
  try {
    const result = await ElMessageBox.prompt(
      '清理 N 天之前的操作日志（默认 30 天），该操作不可恢复。',
      '清理日志',
      { inputValue: '30', inputPattern: /^\d+$/, inputErrorMessage: '请输入数字' },
    )
    days = result.value
  } catch {
    return
  }
  const res = await cleanAuditLogs(Number(days))
  ElMessage.success(`已清理 ${res.cleaned} 条（${res.before} 之前）`)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="audit-log-view">
    <el-card shadow="never">
      <div class="page-head">
        <div class="head-left">
          <h2 class="page-title">操作审计日志</h2>
          <p class="page-desc">记录平台核心写操作（模型配置/知识库/Prompt 模板/限额/路由等），用于安全审计与问题溯源。</p>
        </div>
        <el-button type="danger" plain :icon="Delete" @click="handleClean">清理历史</el-button>
      </div>

      <div class="toolbar">
        <el-input
          v-model="filter.username"
          placeholder="操作人"
          clearable
          style="width: 140px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-select v-model="filter.module" placeholder="模块" clearable style="width: 160px" @change="load">
          <el-option v-for="(label, val) in MODULE_LABELS" :key="val" :label="label" :value="val" />
        </el-select>
        <el-input
          v-model="filter.action"
          placeholder="动作"
          clearable
          style="width: 140px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-select v-model="filter.status" placeholder="结果" clearable style="width: 120px" @change="load">
          <el-option label="成功" value="success" />
          <el-option label="失败" value="fail" />
        </el-select>
        <el-date-picker
          v-model="filter.range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 340px"
        />
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="操作人" width="120">
          <template #default="{ row }">
            <span>{{ row.username || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="模块" width="130">
          <template #default="{ row }">
            <el-tag type="info">{{ moduleLabel(row.module) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="120" />
        <el-table-column label="请求" min-width="220">
          <template #default="{ row }">
            <span class="mono">{{ row.method }} {{ row.path }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">
            <span v-if="row.durationMs != null">{{ row.durationMs }}ms</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">
              {{ row.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.createTime }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="showDetail(row as OperationLogResp)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @change="load"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="日志详情" width="680px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="statusTag(detail.status)" size="small">{{ detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.username || '-' }} (ID: {{ detail.userId ?? '-' }})</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ moduleLabel(detail.module) }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ detail.action }}</el-descriptions-item>
        <el-descriptions-item label="请求" :span="2">
          <span class="mono">{{ detail.method }} {{ detail.path }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.durationMs != null ? detail.durationMs + 'ms' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre class="detail-pre">{{ detail.params || '（无）' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="失败原因" :span="2">
          <pre class="detail-pre error">{{ detail.errorMsg }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.page-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-tertiary);
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.mono {
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  font-size: 12px;
}
.detail-pre {
  margin: 0;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  background: var(--bg-secondary, #f5f5f5);
  border-radius: 4px;
  padding: 8px;
}
.detail-pre.error {
  color: #f56c6c;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
