<script setup lang="ts">
/**
 * Agent 评测面板（A3）
 *
 * 基于 agent_runtime + trace 数据构建：运行历史列表、运行详情（trace 时间轴 + 指标）、
 * 人工评分（good/bad + 1-5 分 + 备注）与整体汇总统计。
 */
import { onMounted, ref } from 'vue'
import {
  ElCard, ElButton, ElTable, ElTableColumn, ElTag, ElInput, ElSelect, ElOption,
  ElDialog, ElMessage, ElPagination, ElEmpty, ElDescriptions, ElDescriptionsItem,
  ElRate, ElRow, ElCol, ElStatistic, ElCollapse, ElCollapseItem,
} from 'element-plus'
import { Search, Refresh, View } from '@element-plus/icons-vue'
import {
  listRuns, getRunDetail, submitEval, getEvalStats,
  type AgentRun, type AgentRunDetail, type AgentEvalStats, type TraceItem,
} from '@/api/agent-eval'

const STATUS_LABELS: Record<string, string> = {
  pending: '待执行', running: '执行中', paused: '已暂停', done: '成功', failed: '失败', cancelled: '已取消',
}

const loading = ref(false)
const list = ref<AgentRun[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const stats = ref<AgentEvalStats>({
  total_runs: 0, success_runs: 0, failed_runs: 0, success_rate: 0,
  avg_latency_ms: 0, total_tokens: 0, avg_tokens: 0,
  eval_count: 0, good_count: 0, bad_count: 0,
})

const filter = ref<{ status: string; modelId: string }>({ status: '', modelId: '' })

const detailVisible = ref(false)
const detail = ref<AgentRunDetail | null>(null)
const evalScore = ref<number | undefined>(undefined)
const evalVerdict = ref('none')
const evalNote = ref('')

function statusTag(s: string) {
  if (s === 'done' || s === 'running') return s === 'done' ? 'success' : 'primary'
  if (s === 'failed') return 'danger'
  if (s === 'paused') return 'warning'
  return 'info'
}

function truncate(text: string | null, n = 60) {
  if (!text) return '-'
  const oneLine = text.replace(/\s+/g, ' ').trim()
  return oneLine.length > n ? oneLine.slice(0, n) + '…' : oneLine
}

async function load() {
  loading.value = true
  try {
    const res = await listRuns({
      status: filter.value.status || undefined,
      modelId: filter.value.modelId || undefined,
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

async function loadStats() {
  try {
    stats.value = await getEvalStats()
  } catch {
    /* toast */
  }
}

async function showDetail(row: AgentRun) {
  detailVisible.value = true
  detail.value = null
  const d = await getRunDetail(row.runtimeId)
  detail.value = d
  evalScore.value = d.eval?.score ?? undefined
  evalVerdict.value = d.eval?.verdict ?? 'none'
  evalNote.value = d.eval?.note ?? ''
}

async function handleSubmitEval() {
  if (!detail.value) return
  const res = await submitEval(detail.value.runtimeId, {
    score: evalScore.value ?? undefined,
    verdict: evalVerdict.value,
    note: evalNote.value || undefined,
  })
  ElMessage.success(res.id ? '评测已保存' : '评测已更新')
  detailVisible.value = false
  await Promise.all([load(), loadStats()])
}

function traceTypeLabel(t: TraceItem) {
  switch (t.type) {
    case 'thought': return '思考'
    case 'action': return '动作'
    case 'observation': return '观察'
    case 'plan': return '计划'
    case 'resume': return '续跑'
    case 'error': return '错误'
    default: return t.type
  }
}

onMounted(() => {
  load()
  loadStats()
})
</script>

<template>
  <div class="agent-eval-view">
    <!-- 汇总统计 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="3"><el-card shadow="never"><el-statistic title="运行总数" :value="stats.total_runs" /></el-card></el-col>
      <el-col :span="3"><el-card shadow="never"><el-statistic title="成功" :value="stats.success_runs" /></el-card></el-col>
      <el-col :span="3"><el-card shadow="never"><el-statistic title="失败" :value="stats.failed_runs" /></el-card></el-col>
      <el-col :span="3"><el-card shadow="never"><el-statistic title="成功率" :value="stats.success_rate" suffix="%" /></el-card></el-col>
      <el-col :span="3"><el-card shadow="never"><el-statistic title="平均耗时" :value="stats.avg_latency_ms" suffix="ms" /></el-card></el-col>
      <el-col :span="3"><el-card shadow="never"><el-statistic title="平均Token" :value="stats.avg_tokens" /></el-card></el-col>
      <el-col :span="3"><el-card shadow="never"><el-statistic title="已评测" :value="stats.eval_count" /></el-card></el-col>
      <el-col :span="3"><el-card shadow="never"><el-statistic title="好评/差评" :value="stats.good_count" ><template #suffix>/ {{ stats.bad_count }}</template></el-statistic></el-card></el-col>
    </el-row>

    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="filter.status" placeholder="运行状态" clearable style="width: 140px" @change="load">
          <el-option v-for="(label, val) in STATUS_LABELS" :key="val" :label="label" :value="val" />
        </el-select>
        <el-input
          v-model="filter.modelId" placeholder="模型 ID" clearable style="width: 200px"
          @keyup.enter="load" @clear="load"
        />
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        <el-button :icon="Refresh" @click="() => { filter.status = ''; filter.modelId = ''; load(); loadStats() }">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="runtimeId" label="运行ID" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ STATUS_LABELS[row.status] ?? row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelId" label="模型" width="160">
          <template #default="{ row }">{{ row.modelId || '-' }}</template>
        </el-table-column>
        <el-table-column label="输入" min-width="200">
          <template #default="{ row }">{{ truncate(row.input) }}</template>
        </el-table-column>
        <el-table-column label="输出" min-width="200">
          <template #default="{ row }">{{ truncate(row.output) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ row.metrics.total_latency_ms }}ms</template>
        </el-table-column>
        <el-table-column label="Token" width="100">
          <template #default="{ row }">{{ row.metrics.total_token_in + row.metrics.total_token_out }}</template>
        </el-table-column>
        <el-table-column label="评测" width="120">
          <template #default="{ row }">
            <template v-if="row.eval">
              <el-tag :type="row.eval.verdict === 'good' ? 'success' : row.eval.verdict === 'bad' ? 'danger' : 'info'" size="small">
                {{ row.eval.verdict === 'good' ? '好评' : row.eval.verdict === 'bad' ? '差评' : '未标记' }}
              </el-tag>
              <span v-if="row.eval.score" class="eval-score">★{{ row.eval.score }}</span>
            </template>
            <span v-else class="text-muted">未评测</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="165">
          <template #default="{ row }">{{ row.createTime }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" :icon="View" @click="showDetail(row as AgentRun)">详情/评测</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" @change="load"
        />
      </div>
    </el-card>

    <!-- 运行详情 + 评测 -->
    <el-dialog v-model="detailVisible" title="运行详情 / 人工评测" width="860px" top="4vh">
      <template v-if="detail">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="运行ID">{{ detail.runtimeId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detail.status)" size="small">{{ STATUS_LABELS[detail.status] ?? detail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="模型">{{ detail.modelId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="会话ID">{{ detail.sessionId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detail.metrics.total_latency_ms }}ms</el-descriptions-item>
          <el-descriptions-item label="Token">{{ detail.metrics.total_token_in + detail.metrics.total_token_out }} (in {{ detail.metrics.total_token_in }} / out {{ detail.metrics.total_token_out }})</el-descriptions-item>
          <el-descriptions-item label="步骤数">{{ detail.metrics.step_count }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="错误" v-if="detail.errorMessage">
            <span class="error-text">{{ detail.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="block-title">执行轨迹 (Trace)</div>
        <el-collapse v-if="detail.traces?.length" accordion>
          <el-collapse-item v-for="t in detail.traces" :key="t.id">
            <template #title>
              <el-tag size="small" :type="t.type === 'error' ? 'danger' : 'primary'" class="trace-tag">{{ traceTypeLabel(t) }}</el-tag>
              <span class="trace-step">Step {{ t.stepIndex }}</span>
              <span class="trace-meta" v-if="t.tokenIn || t.tokenOut">token {{ (t.tokenIn ?? 0) + (t.tokenOut ?? 0) }}</span>
              <span class="trace-meta" v-if="t.latencyMs != null">{{ t.latencyMs }}ms</span>
              <span class="trace-time">{{ t.createTime }}</span>
            </template>
            <pre class="trace-content">{{ t.content }}</pre>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-else description="无 trace 数据" :image-size="60" />

        <div class="block-title">人工评测</div>
        <el-row :gutter="12" align="middle">
          <el-col :span="10">
            <div class="eval-field">
              <span class="eval-label">评分 (1-5)</span>
              <el-rate v-model="evalScore" :max="5" />
            </div>
          </el-col>
          <el-col :span="14">
            <div class="eval-field">
              <span class="eval-label">结论</span>
              <el-select v-model="evalVerdict" style="width: 140px">
                <el-option label="好评" value="good" />
                <el-option label="差评" value="bad" />
                <el-option label="未标记" value="none" />
              </el-select>
            </div>
          </el-col>
          <el-col :span="24">
            <el-input v-model="evalNote" type="textarea" :rows="2" placeholder="评测备注（可选）" />
          </el-col>
        </el-row>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitEval">保存评测</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stat-row {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.eval-score {
  margin-left: 4px;
  font-size: 12px;
  color: #e6a23c;
}
.text-muted {
  color: var(--text-tertiary, #999);
  font-size: 12px;
}
.error-text {
  color: #f56c6c;
  font-size: 12px;
}
.block-title {
  margin: 14px 0 8px;
  font-size: 14px;
  font-weight: 600;
}
.trace-tag {
  margin-right: 8px;
}
.trace-step {
  margin-right: 12px;
  font-size: 12px;
  color: var(--text-tertiary, #999);
}
.trace-meta {
  margin-right: 12px;
  font-size: 12px;
  color: var(--text-tertiary, #999);
}
.trace-time {
  font-size: 12px;
  color: var(--text-tertiary, #999);
}
.trace-content {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.6;
  background: var(--bg-secondary, #f7f7f7);
  border-radius: 4px;
  padding: 10px;
}
.eval-field {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.eval-label {
  font-size: 13px;
  color: var(--text-secondary, #666);
  white-space: nowrap;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
