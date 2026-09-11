<script setup lang="ts">
/**
 * 系统运维页面（LLM 调用日志 + 运维备份）
 *
 * 功能：
 * 1. LLM 调用日志：分页查询、按模型/状态/traceId/时间筛选、查看详情、聚合统计
 * 2. 运维备份：MySQL JSONL 备份、ES 快照创建/恢复、Binlog 巡检
 */
import { ref, reactive, onMounted, computed } from 'vue'
import {
  ElCard, ElTabs, ElTabPane, ElTable, ElTableColumn, ElTag, ElInput,
  ElSelect, ElOption, ElDatePicker, ElButton, ElPagination, ElIcon,
  ElDialog, ElDescriptions, ElDescriptionsItem, ElStatistic, ElRow, ElCol,
  ElMessage, ElMessageBox, ElEmpty, ElTooltip,
} from 'element-plus'
import {
  Search, Refresh, Download, Upload, DataLine, Document, Warning, CircleCheck,
} from '@element-plus/icons-vue'
import {
  listLlmLogs, getLlmLogStats, getLlmLogByTraceId, type LlmLogItem, type LlmLogStats,
} from '@/api/llm-log'
import { backupMysql, snapshotEs, restoreEs, verifyBinlog } from '@/api/ops'

const activeTab = ref('logs')

// ============ LLM 日志 Tab ============
const logs = ref<LlmLogItem[]>([])
const logTotal = ref(0)
const logLoading = ref(false)
const logPage = ref(1)
const logSize = ref(20)
const logStats = ref<LlmLogStats | null>(null)

const logFilter = reactive({
  modelId: '',
  status: '',
  traceId: '',
  dateRange: null as [string, string] | null,
})

const statusTagType = (s: string) => s === 'success' ? 'success' : 'danger'

async function fetchLogs() {
  logLoading.value = true
  try {
    const params: Record<string, unknown> = {
      page: logPage.value,
      size: logSize.value,
    }
    if (logFilter.modelId) params.modelId = logFilter.modelId
    if (logFilter.status) params.status = logFilter.status
    if (logFilter.traceId) params.traceId = logFilter.traceId
    if (logFilter.dateRange && logFilter.dateRange.length === 2) {
      params.startDate = logFilter.dateRange[0]
      params.endDate = logFilter.dateRange[1]
    }
    const res = await listLlmLogs(params)
    logs.value = res.list ?? []
    logTotal.value = res.total ?? 0
  } catch { /* ignore */ }
  finally { logLoading.value = false }
}

async function fetchStats() {
  try {
    const params: Record<string, string> = {}
    if (logFilter.dateRange && logFilter.dateRange.length === 2) {
      params.startDate = logFilter.dateRange[0]
      params.endDate = logFilter.dateRange[1]
    }
    logStats.value = await getLlmLogStats(Object.keys(params).length ? params : undefined)
  } catch { /* ignore */ }
}

function handleLogSearch() {
  logPage.value = 1
  fetchLogs()
  fetchStats()
}

function handleLogReset() {
  logFilter.modelId = ''
  logFilter.status = ''
  logFilter.traceId = ''
  logFilter.dateRange = null
  logPage.value = 1
  fetchLogs()
  fetchStats()
}

function handleLogPageChange(p: number) {
  logPage.value = p
  fetchLogs()
}

/** 日志详情弹窗 */
const detailVisible = ref(false)
const detailLog = ref<LlmLogItem | null>(null)
function showDetail(log: LlmLogItem) {
  detailLog.value = log
  detailVisible.value = true
}

/** Trace 链路弹窗 */
const traceVisible = ref(false)
const traceLogs = ref<LlmLogItem[]>([])
const traceLoading = ref(false)
async function showTrace(traceId: string) {
  if (!traceId) return
  traceLoading.value = true
  traceVisible.value = true
  try {
    traceLogs.value = await getLlmLogByTraceId(traceId)
  } catch { traceLogs.value = [] }
  finally { traceLoading.value = false }
}

/** 截断显示 */
function truncate(s: string | null, max = 80) {
  if (!s) return '-'
  return s.length > max ? s.substring(0, max) + '…' : s
}

// ============ 运维备份 Tab ============
const opsLoading = ref(false)
const binlogResult = ref<Record<string, unknown> | null>(null)

async function handleBackupMysql() {
  try {
    await ElMessageBox.confirm('确定触发 MySQL JSONL 逻辑备份？', '备份确认', { type: 'warning' })
    opsLoading.value = true
    await backupMysql()
    ElMessage.success('MySQL 备份已触发')
  } catch { /* cancel */ }
  finally { opsLoading.value = false }
}

async function handleSnapshotEs() {
  try {
    await ElMessageBox.confirm('确定触发 Elasticsearch 快照？', '快照确认', { type: 'warning' })
    opsLoading.value = true
    await snapshotEs()
    ElMessage.success('ES 快照已触发')
  } catch { /* cancel */ }
  finally { opsLoading.value = false }
}

async function handleRestoreEs() {
  try {
    const { value } = await ElMessageBox.prompt('请输入要恢复的快照日期（yyyyMMdd）', 'ES 快照恢复', {
      confirmButtonText: '恢复',
      cancelButtonText: '取消',
      inputPattern: /^\d{8}$/,
      inputErrorMessage: '请输入 8 位日期，如 20260828',
    })
    if (value) {
      opsLoading.value = true
      await restoreEs(value)
      ElMessage.success(`ES 快照 ${value} 恢复已触发`)
    }
  } catch { /* cancel */ }
  finally { opsLoading.value = false }
}

async function handleBinlogVerify() {
  opsLoading.value = true
  try {
    binlogResult.value = await verifyBinlog()
    ElMessage.success('Binlog 巡检完成')
  } catch { /* ignore */ }
  finally { opsLoading.value = false }
}

onMounted(() => {
  fetchLogs()
  fetchStats()
})
</script>

<template>
  <div class="system-ops">
    <h2 style="margin: 0 0 20px; font-size: 20px;">系统运维</h2>

    <el-tabs v-model="activeTab">
      <!-- ============ LLM 调用日志 ============ -->
      <el-tab-pane label="LLM 调用日志" name="logs">
        <!-- 统计卡片 -->
        <el-row :gutter="16" style="margin-bottom: 16px;">
          <el-col :span="5">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="总调用" :value="logStats?.total ?? 0" />
            </el-card>
          </el-col>
          <el-col :span="5">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="成功" :value="logStats?.success ?? 0">
                <template #suffix><el-tag type="success" size="small">success</el-tag></template>
              </el-statistic>
            </el-card>
          </el-col>
          <el-col :span="5">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="失败" :value="logStats?.failed ?? 0">
                <template #suffix><el-tag type="danger" size="small">failed</el-tag></template>
              </el-statistic>
            </el-card>
          </el-col>
          <el-col :span="5">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="总 Token" :value="logStats?.totalTokens ?? 0" />
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="平均延迟(ms)" :value="logStats?.avgLatencyMs ?? 0" />
            </el-card>
          </el-col>
        </el-row>

        <!-- 筛选栏 -->
        <el-card shadow="never" style="margin-bottom: 16px;">
          <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: center;">
            <el-input v-model="logFilter.modelId" placeholder="模型 ID" clearable style="width: 160px;" />
            <el-select v-model="logFilter.status" placeholder="状态" clearable style="width: 120px;">
              <el-option label="成功" value="success" />
              <el-option label="失败" value="failed" />
            </el-select>
            <el-input v-model="logFilter.traceId" placeholder="Trace ID" clearable style="width: 200px;" />
            <el-date-picker
              v-model="logFilter.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 260px;"
            />
            <el-button type="primary" :icon="Search" @click="handleLogSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleLogReset">重置</el-button>
          </div>
        </el-card>

        <!-- 日志表格 -->
        <el-table :data="logs" v-loading="logLoading" stripe border style="width: 100%;">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="Trace ID" width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button v-if="row.traceId" link type="primary" size="small" @click.stop="showTrace(row.traceId)">
                {{ row.traceId }}
              </el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="modelId" label="模型" width="140" />
          <el-table-column label="Prompt" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ truncate(row.prompt, 60) }}</template>
          </el-table-column>
          <el-table-column prop="totalTokens" label="Token" width="90" align="right" />
          <el-table-column prop="latencyMs" label="延迟(ms)" width="100" align="right" />
          <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="170" />
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="showDetail(row as LlmLogItem)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 12px; display: flex; justify-content: flex-end;">
          <el-pagination
            :current-page="logPage"
            :page-size="logSize"
            :total="logTotal"
            layout="total, prev, pager, next"
            @current-change="handleLogPageChange"
          />
        </div>
      </el-tab-pane>

      <!-- ============ 运维备份 ============ -->
      <el-tab-pane label="运维备份" name="ops">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-card shadow="never" class="ops-card">
              <template #header><el-icon><Download /></el-icon> MySQL 备份</template>
              <p>手动触发 MySQL JSONL 逻辑备份（与每日定时任务同一逻辑）。</p>
              <el-button type="primary" :loading="opsLoading" @click="handleBackupMysql">触发备份</el-button>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="ops-card">
              <template #header><el-icon><Upload /></el-icon> ES 快照</template>
              <p>手动触发 Elasticsearch 快照或恢复指定日期快照。</p>
              <div style="display: flex; gap: 8px;">
                <el-button type="primary" :loading="opsLoading" @click="handleSnapshotEs">创建快照</el-button>
                <el-button :loading="opsLoading" @click="handleRestoreEs">恢复快照</el-button>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="ops-card">
              <template #header><el-icon><DataLine /></el-icon> Binlog 巡检</template>
              <p>检查 MySQL binlog 配置是否满足数据恢复要求。</p>
              <el-button type="primary" :loading="opsLoading" @click="handleBinlogVerify">执行巡检</el-button>
              <div v-if="binlogResult" style="margin-top: 12px; font-size: 13px; color: #606266;">
                <div v-for="(val, key) in binlogResult" :key="key">
                  <el-tag :type="val === true || val === 'OK' ? 'success' : 'warning'" size="small">{{ key }}</el-tag>
                  {{ val }}
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!-- 日志详情弹窗 -->
    <el-dialog v-model="detailVisible" title="LLM 调用详情" width="680px" destroy-on-close>
      <el-descriptions :column="2" border v-if="detailLog">
        <el-descriptions-item label="ID">{{ detailLog.id }}</el-descriptions-item>
        <el-descriptions-item label="Trace ID">{{ detailLog.traceId }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ detailLog.modelId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailLog.status)" size="small">{{ detailLog.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Prompt Tokens">{{ detailLog.promptTokens ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Completion Tokens">{{ detailLog.completionTokens ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Total Tokens">{{ detailLog.totalTokens ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="延迟(ms)">{{ detailLog.latencyMs ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="时间" :span="2">{{ detailLog.createTime }}</el-descriptions-item>
        <el-descriptions-item label="Prompt" :span="2">
          <div style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-all;">{{ detailLog.prompt || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="Response" :span="2">
          <div style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-all;">{{ detailLog.response || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailLog.errorMsg" label="错误信息" :span="2">
          <div style="color: #f56c6c;">{{ detailLog.errorMsg }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- Trace 链路弹窗 -->
    <el-dialog v-model="traceVisible" title="Trace 调用链路" width="800px" destroy-on-close>
      <div v-loading="traceLoading">
        <el-empty v-if="!traceLoading && traceLogs.length === 0" description="该 Trace ID 下没有更多调用记录" />
        <el-table v-else :data="traceLogs" stripe border size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="modelId" label="模型" width="140" />
          <el-table-column label="Prompt" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ truncate(row.prompt, 60) }}</template>
          </el-table-column>
          <el-table-column prop="totalTokens" label="Token" width="90" align="right" />
          <el-table-column prop="latencyMs" label="延迟" width="80" align="right">
            <template #default="{ row }">{{ row.latencyMs ?? '-' }}ms</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default { name: 'SystemOpsView' }
</script>

<style scoped>
.system-ops { padding: 4px; }
.stat-card { text-align: center; }
.ops-card p { color: #909399; font-size: 13px; margin: 0 0 12px; min-height: 40px; }
</style>
