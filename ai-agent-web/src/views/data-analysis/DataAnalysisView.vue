<script setup lang="ts">
/**
 * 数据分析页面（add-data-analysis）
 *
 * 功能：
 * 1. 自然语言问数（NL2SQL）：输入问题 → 后端生成并执行 SQL → 返回结果集 + 图表规格
 * 2. 图表可视化：bar / line / pie 使用 ECharts 渲染，table 直接表格展示
 * 3. SQL 展示与数据明细
 * 4. 报告导出：基于结果调用 LLM 生成 Markdown 分析报告，可下载 .md
 *
 * 对应后端 DataAnalysisController / Nl2SqlService
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import {
  ElCard,
  ElInput,
  ElButton,
  ElTag,
  ElTable,
  ElTableColumn,
  ElCollapse,
  ElCollapseItem,
  ElIcon,
  ElEmpty,
  ElMessage,
  ElAlert,
} from 'element-plus'
import { ChatLineRound, Download, Document, DataAnalysis, Cpu } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import {
  fetchDataSchema,
  askData,
  generateReport,
  type DataSchemaResp,
  type DataAskResp,
} from '@/api/data-analysis'

// ============ 状态 ============
const schema = ref<DataSchemaResp | null>(null)
const question = ref('')
const loading = ref(false)
const result = ref<DataAskResp | null>(null)
const report = ref('')
const reportLoading = ref(false)
const showSql = ref<string[]>([])

const chartEl = ref<HTMLElement | null>(null)
const chart = shallowRef<echarts.ECharts | null>(null)

/** 快捷问题示例 */
const examples = [
  '近7天每天新增多少会话',
  '按供应商统计模型配置数量',
  '各角色对话消息数量占比',
  '各知识库的文档数对比',
  '近30天每日 LLM 调用次数与 Token 趋势',
]

/** 是否有图表可渲染 */
const hasChart = computed(
  () =>
    !!result.value &&
    result.value.chartType !== 'table' &&
    result.value.rows.length > 0 &&
    result.value.columns.length > 0,
)

/** 报告 markdown 渲染后的 HTML */
const reportHtml = computed(() => DOMPurify.sanitize(marked.parse(report.value || '') as string))

// ============ 数据加载 ============
async function loadSchema() {
  try {
    schema.value = await fetchDataSchema()
  } catch {
    // 错误已由 request 拦截器统一提示
  }
}

/** 自然语言问数 */
async function handleAsk() {
  const q = question.value.trim()
  if (!q) {
    ElMessage.warning('请输入要分析的问题')
    return
  }
  loading.value = true
  report.value = ''
  try {
    result.value = await askData(q)
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

/** 渲染 ECharts 图表 */
function renderChart() {
  const resp = result.value
  if (!resp || !hasChart.value || !chartEl.value) return
  if (!chart.value) {
    chart.value = echarts.init(chartEl.value)
  }
  chart.value.setOption(buildOption(resp), true)
}

/** 根据结果构建图表配置 */
function buildOption(resp: DataAskResp): echarts.EChartsOption {
  const { columns, rows, chartType, chartTitle } = resp
  const toNumber = (v: unknown): number => {
    if (v == null || v === '') return 0
    const n = Number(v)
    return Number.isFinite(n) ? n : 0
  }

  if (chartType === 'pie') {
    const xCol = columns[0] ?? ''
    const valCol = columns[1] ?? xCol
    const data = rows.map((r) => ({
      name: String(r[xCol] ?? '未知'),
      value: toNumber(r[valCol]),
    }))
    return {
      title: { text: chartTitle, left: 'center' },
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{ name: chartTitle, type: 'pie', radius: '62%', data, label: { formatter: '{b}\n{d}%' } }],
    }
  }

  // bar / line：第一列做分类轴，其余数值列做系列
  const xCol = columns[0] ?? ''
  const categories = rows.map((r) => String(r[xCol] ?? ''))
  let series: echarts.SeriesOption[] = []
  if (columns.length > 1) {
    series = columns.slice(1).map((col) => ({
      name: col,
      type: chartType as 'bar' | 'line',
      data: rows.map((r) => toNumber(r[col])),
    }))
  } else {
    series = [
      {
        name: xCol,
        type: chartType as 'bar' | 'line',
        data: rows.map((r) => toNumber(r[xCol])),
      },
    ]
  }
  return {
    title: { text: chartTitle, left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, type: 'scroll' },
    grid: { left: 48, right: 24, top: 48, bottom: 40 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { interval: 0, rotate: categories.length > 8 ? 30 : 0 },
    },
    yAxis: { type: 'value' },
    series,
  }
}

/** 生成 Markdown 分析报告 */
async function handleReport() {
  if (!result.value) return
  reportLoading.value = true
  try {
    const { question: q, sql, columns, rows } = result.value
    report.value = await generateReport({ question: q, sql, columns, rows })
  } finally {
    reportLoading.value = false
  }
}

/** 下载报告为 .md 文件 */
function downloadReport() {
  if (!report.value) return
  const blob = new Blob([`# ${result.value?.question ?? '数据分析报告'}\n\n${report.value}`], {
    type: 'text/markdown;charset=utf-8',
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `数据分析报告-${new Date().toISOString().slice(0, 10)}.md`
  a.click()
  URL.revokeObjectURL(url)
}

function handleResize() {
  chart.value?.resize()
}

onMounted(() => {
  loadSchema()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart.value?.dispose()
})
</script>

<template>
  <div class="data-analysis-view">
    <!-- ============ 问数输入区 ============ -->
    <el-card shadow="never" class="ask-card">
      <div class="ask-header">
        <el-icon class="ask-icon"><DataAnalysis /></el-icon>
        <span class="ask-title">自然语言问数</span>
        <span class="ask-subtitle">用中文提问，自动生成 SQL 并出图</span>
      </div>
      <el-input
        v-model="question"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="例如：近7天每天新增多少会话？"
        class="ask-input"
        @keydown.ctrl.enter="handleAsk"
      />
      <div class="ask-footer">
        <div class="examples">
          <el-tag
            v-for="(ex, i) in examples"
            :key="i"
            size="small"
            effect="plain"
            class="example-tag"
            @click="question = ex"
          >
            {{ ex }}
          </el-tag>
        </div>
        <el-button type="primary" :loading="loading" :icon="ChatLineRound" @click="handleAsk">
          问数
        </el-button>
      </div>
    </el-card>

    <!-- ============ 可分析表清单 ============ -->
    <el-card shadow="never" class="schema-card">
      <el-collapse>
        <el-collapse-item>
          <template #title>
            <el-icon class="schema-icon"><Cpu /></el-icon>
            <span>可分析数据表（{{ schema?.tables.length ?? 0 }} 张）</span>
          </template>
          <div class="schema-grid">
            <div v-for="t in schema?.tables ?? []" :key="t.name" class="schema-table">
              <div class="schema-table-name">{{ t.name }}</div>
              <div class="schema-table-comment">{{ t.comment }}</div>
              <div class="schema-cols">
                <el-tag
                  v-for="c in t.columns"
                  :key="c.name"
                  size="small"
                  type="info"
                  effect="plain"
                >
                  {{ c.name }}
                </el-tag>
              </div>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <!-- ============ 结果区 ============ -->
    <template v-if="result">
      <el-card shadow="never" class="result-card">
        <template #header>
          <div class="result-header">
            <div>
              <div class="result-question">Q：{{ result.question }}</div>
              <div class="result-meta">
                <el-tag size="small" type="success">{{ result.chartType }}</el-tag>
                <span class="latency">耗时 {{ result.latencyMs }}ms</span>
                <span class="row-count">共 {{ result.rows.length }} 行</span>
              </div>
            </div>
            <div class="result-actions">
              <el-button
                size="small"
                :icon="Document"
                :loading="reportLoading"
                @click="handleReport"
              >
                生成报告
              </el-button>
              <el-button v-if="report" size="small" :icon="Download" @click="downloadReport">
                下载 .md
              </el-button>
            </div>
          </div>
        </template>

        <el-alert v-if="result.message" :title="result.message" type="info" :closable="false" class="mb" />

        <!-- 图表 -->
        <div v-if="hasChart" ref="chartEl" class="chart-box" />

        <!-- SQL 展示 -->
        <el-collapse v-model="showSql" class="sql-collapse">
          <el-collapse-item title="查看生成的 SQL" name="sql">
            <pre class="sql-block">{{ result.sql }}</pre>
          </el-collapse-item>
        </el-collapse>

        <!-- 明细表格 -->
        <el-table :data="result.rows" border size="small" max-height="360" class="result-table">
          <el-table-column
            v-for="col in result.columns"
            :key="col"
            :prop="col"
            :label="col"
            min-width="120"
          />
        </el-table>
      </el-card>

      <!-- 报告 -->
      <el-card v-if="report" shadow="never" class="report-card">
        <template #header>
          <div class="report-header">
            <span>分析报告</span>
            <el-button size="small" :icon="Download" @click="downloadReport">下载 .md</el-button>
          </div>
        </template>
        <div class="report-body" v-html="reportHtml" />
      </el-card>
    </template>

    <el-empty v-else-if="!loading" description="输入问题开始数据分析，例如“近7天每天新增多少会话”" />
  </div>
</template>

<style scoped>
.data-analysis-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ask-card .ask-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.ask-icon {
  font-size: 20px;
  color: var(--el-color-primary);
}

.ask-title {
  font-weight: 600;
  font-size: 16px;
}

.ask-subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ask-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.examples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.example-tag {
  cursor: pointer;
}

.schema-icon {
  margin-right: 6px;
  vertical-align: -2px;
}

.schema-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}

.schema-table {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
}

.schema-table-name {
  font-weight: 600;
  font-family: monospace;
  color: var(--el-color-primary);
}

.schema-table-comment {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 2px 0 8px;
}

.schema-cols {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.result-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.result-question {
  font-weight: 600;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.result-actions {
  display: flex;
  gap: 8px;
}

.mb {
  margin-bottom: 12px;
}

.chart-box {
  width: 100%;
  height: 340px;
  margin-bottom: 12px;
}

.sql-collapse {
  margin-bottom: 12px;
}

.sql-block {
  margin: 0;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 13px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.result-table {
  width: 100%;
}

.report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.report-body {
  line-height: 1.7;
  font-size: 14px;
}

.report-body :deep(h1),
.report-body :deep(h2),
.report-body :deep(h3) {
  margin: 12px 0 8px;
}

.report-body :deep(pre) {
  background: var(--el-fill-color-light);
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
}

.report-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
}

.report-body :deep(td),
.report-body :deep(th) {
  border: 1px solid var(--el-border-color-lighter);
  padding: 6px 10px;
}
</style>
