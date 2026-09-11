<script setup lang="ts">
/**
 * Dashboard 首页仪表盘
 *
 * 聚合展示平台核心资源（会话/消息/模型/知识库/Prompt/Agent）与近 N 天 LLM 运行状况：
 * - KPI 统计卡片
 * - 每日 LLM 调用趋势（ECharts 柱状图）
 * - 模型调用分布（ECharts 饼图）
 * - LLM 运行状况 + 最近调用列表
 */
import { onMounted, onUnmounted, ref, shallowRef } from 'vue'
import { ElCard, ElRow, ElCol, ElRadioGroup, ElRadioButton, ElButton, ElTable, ElTableColumn, ElTag, ElEmpty, ElSkeleton } from 'element-plus'
import { Refresh, ChatDotRound, ChatLineSquare, Cpu, DataAnalysis, Document, Monitor, Odometer, SuccessFilled, CircleCloseFilled, WarningFilled, TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getDashboardSummary, getRecentCalls, type DashboardSummaryResp, type RecentLlmLog } from '@/api/dashboard'

/** 时间范围（天） */
const days = ref(7)
const loading = ref(true)
const recentLoading = ref(true)
const summary = shallowRef<DashboardSummaryResp | null>(null)
const recentCalls = shallowRef<RecentLlmLog[]>([])

/** 趋势图 / 分布图实例 */
const trendChartRef = ref<HTMLElement>()
const pieChartRef = ref<HTMLElement>()
const tokenChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null
let tokenChart: echarts.ECharts | null = null

/** KPI 卡片配置 */
const kpiCards = (c: Record<string, number>, tokens: number) => [
  { label: '对话会话', value: c.sessions ?? 0, icon: ChatLineSquare, color: '#409eff' },
  { label: '消息总数', value: c.messages ?? 0, icon: ChatDotRound, color: '#67c23a' },
  { label: 'Token 消耗', value: tokens, icon: Odometer, color: '#e6a23c', big: true },
  { label: '模型配置', value: c.models ?? 0, icon: Cpu, color: '#f56c6c' },
  { label: '知识库', value: c.knowledgeBases ?? 0, icon: DataAnalysis, color: '#909399' },
  { label: '文档数量', value: c.documents ?? 0, icon: Document, color: '#409eff' },
  { label: 'Prompt 模板', value: c.promptTemplates ?? 0, icon: Document, color: '#67c23a' },
  { label: 'Agent 运行', value: c.agentRuntimes ?? 0, icon: Monitor, color: '#e6a23c' },
]

function formatNumber(n: number): string {
  if (n >= 1_0000_0000) return (n / 1_0000_0000).toFixed(2) + ' 亿'
  if (n >= 1_0000) return (n / 1_0000).toFixed(2) + ' 万'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

async function load() {
  loading.value = true
  try {
    summary.value = await getDashboardSummary(days.value)
  } finally {
    loading.value = false
  }
  renderCharts()
}

async function loadRecent() {
  recentLoading.value = true
  try {
    recentCalls.value = await getRecentCalls(10)
  } finally {
    recentLoading.value = false
  }
}

function renderCharts() {
  if (!summary.value) return
  renderTrendChart()
  renderPieChart()
  renderTokenChart()
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  const data = summary.value?.dailyTrend ?? []
  trendChart.setOption(
    {
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 30, bottom: 30 },
      xAxis: {
        type: 'category',
        data: data.map((d) => d.day),
        axisLabel: { fontSize: 11 },
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: '调用量',
          type: 'bar',
          data: data.map((d) => Number(d.cnt) || 0),
          itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] },
          barMaxWidth: 36,
        },
      ],
    },
    true,
  )
}

function renderPieChart() {
  if (!pieChartRef.value) return
  if (!pieChart) pieChart = echarts.init(pieChartRef.value)
  const data = summary.value?.modelDistribution ?? []
  pieChart.setOption(
    {
      tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' },
      legend: { type: 'scroll', bottom: 0, textStyle: { fontSize: 11 } },
      series: [
        {
          name: '模型调用',
          type: 'pie',
          radius: ['38%', '62%'],
          center: ['50%', '45%'],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          emphasis: { label: { show: true, fontSize: 12, fontWeight: 'bold' } },
          data: data.map((d) => ({ name: d.modelId || '未知', value: Number(d.cnt) || 0 })),
        },
      ],
    },
    true,
  )
}

function renderTokenChart() {
  if (!tokenChartRef.value) return
  if (!tokenChart) tokenChart = echarts.init(tokenChartRef.value)
  const data = summary.value?.dailyTokenTrend ?? []
  tokenChart.setOption(
    {
      tooltip: { trigger: 'axis', formatter: (p: unknown) => {
        const params = (p as Array<{ name: string; value: number }>) ?? []
        return params.length && params[0] ? `${params[0].name}<br/>Token 消耗: ${Number(params[0].value).toLocaleString()}` : ''
      }},
      grid: { left: 60, right: 20, top: 30, bottom: 30 },
      xAxis: {
        type: 'category',
        data: data.map((d) => d.day),
        axisLabel: { fontSize: 11 },
      },
      yAxis: { type: 'value', axisLabel: { formatter: (v: number) => v >= 1000 ? (v / 1000).toFixed(0) + 'k' : String(v) } },
      series: [
        {
          name: 'Token 消耗',
          type: 'line',
          data: data.map((d) => Number(d.tokens) || 0),
          smooth: true,
          areaStyle: { color: 'rgba(230, 162, 60, 0.15)' },
          lineStyle: { color: '#e6a23c', width: 2 },
          itemStyle: { color: '#e6a23c' },
        },
      ],
    },
    true,
  )
}

function onResize() {
  trendChart?.resize()
  pieChart?.resize()
  tokenChart?.resize()
}

onMounted(() => {
  load()
  loadRecent()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  pieChart?.dispose()
  tokenChart?.dispose()
  trendChart = null
  pieChart = null
  tokenChart = null
})
</script>

<template>
  <div class="dashboard-page">
    <!-- 标题 + 时间范围 -->
    <div class="page-header">
      <div class="page-title">
        <h2>平台仪表盘</h2>
        <p class="page-desc">平台资源与 LLM 运行状况总览</p>
      </div>
      <div class="page-actions">
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" size="small" :loading="loading" @click="load; loadRecent()">
          刷新
        </el-button>
      </div>
    </div>

    <!-- KPI 卡片 -->
    <el-skeleton :loading="loading" animated :rows="3">
      <el-row :gutter="16" class="kpi-row">
        <el-col
          v-for="card in kpiCards(summary?.counts ?? {}, summary?.llm.totalTokens ?? 0)"
          :key="card.label"
          :xs="12"
          :sm="12"
          :md="6"
        >
          <el-card shadow="hover" class="kpi-card">
            <div class="kpi-body">
              <div class="kpi-icon" :style="{ color: card.color, background: card.color + '1a' }">
                <el-icon :size="22"><component :is="card.icon" /></el-icon>
              </div>
              <div class="kpi-info">
                <div class="kpi-value">{{ formatNumber(card.value) }}</div>
                <div class="kpi-label">{{ card.label }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-skeleton>

    <!-- 图表区 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>每日 LLM 调用趋势</span>
              <span class="card-sub">近 {{ days }} 天</span>
            </div>
          </template>
          <div v-show="!loading && (summary?.dailyTrend?.length ?? 0) > 0" ref="trendChartRef" class="chart-box" />
          <el-empty v-if="!loading && (summary?.dailyTrend?.length ?? 0) === 0" description="暂无调用数据" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>模型调用分布</span>
            </div>
          </template>
          <div v-show="!loading && (summary?.modelDistribution?.length ?? 0) > 0" ref="pieChartRef" class="chart-box" />
          <el-empty v-if="!loading && (summary?.modelDistribution?.length ?? 0) === 0" description="暂无调用数据" />
        </el-card>
      </el-col>
    </el-row>

    <!-- Token 消耗趋势 -->
    <el-card shadow="never" class="chart-card">
      <template #header>
        <div class="card-header">
          <el-icon style="color: #e6a23c;"><TrendCharts /></el-icon>
          <span>Token 消耗趋势</span>
          <span class="card-sub">近 {{ days }} 天</span>
        </div>
      </template>
      <div v-show="!loading && (summary?.dailyTokenTrend?.length ?? 0) > 0" ref="tokenChartRef" class="chart-box chart-box-sm" />
      <el-empty v-if="!loading && (summary?.dailyTokenTrend?.length ?? 0) === 0" description="暂无 Token 消耗数据" />
    </el-card>

    <!-- Agent 运行统计 + 异常告警 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="card-header">
              <el-icon style="color: #6366f1;"><Monitor /></el-icon>
              <span>Agent 运行统计</span>
              <span class="card-sub">近 {{ days }} 天</span>
            </div>
          </template>
          <el-skeleton :loading="loading" animated :rows="2">
            <el-row :gutter="16">
              <el-col :xs="12" :sm="8" :md="4">
                <div class="stat-item">
                  <div class="stat-value">{{ summary?.agent?.total ?? 0 }}</div>
                  <div class="stat-label">总运行</div>
                </div>
              </el-col>
              <el-col :xs="12" :sm="8" :md="4">
                <div class="stat-item">
                  <div class="stat-value success">{{ summary?.agent?.success ?? 0 }}</div>
                  <div class="stat-label">成功</div>
                </div>
              </el-col>
              <el-col :xs="12" :sm="8" :md="4">
                <div class="stat-item">
                  <div class="stat-value danger">{{ summary?.agent?.failed ?? 0 }}</div>
                  <div class="stat-label">失败</div>
                </div>
              </el-col>
              <el-col :xs="12" :sm="8" :md="4">
                <div class="stat-item">
                  <div class="stat-value" style="color: #409eff;">{{ summary?.agent?.running ?? 0 }}</div>
                  <div class="stat-label">运行中</div>
                </div>
              </el-col>
              <el-col :xs="12" :sm="8" :md="4">
                <div class="stat-item">
                  <div class="stat-value">{{ Math.round((summary?.agent?.successRate ?? 0) * 100) }}%</div>
                  <div class="stat-label">成功率</div>
                </div>
              </el-col>
            </el-row>
          </el-skeleton>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="card-header">
              <el-icon style="color: #f56c6c;"><WarningFilled /></el-icon>
              <span>最近异常告警</span>
              <span class="card-sub">24h</span>
            </div>
          </template>
          <div v-if="!loading && (summary?.recentAlerts?.length ?? 0) === 0" class="alert-empty">
            <el-icon style="color: #67c23a; font-size: 18px;"><SuccessFilled /></el-icon>
            <span>暂无异常</span>
          </div>
          <div v-else class="alert-list">
            <div v-for="(alert, idx) in summary?.recentAlerts ?? []" :key="idx" class="alert-item">
              <el-tag :type="alert.type === 'llm_error' ? 'danger' : 'warning'" size="small" effect="dark">
                {{ alert.type === 'llm_error' ? 'LLM' : 'Agent' }}
              </el-tag>
              <span class="alert-msg">{{ alert.message }}</span>
              <span class="alert-time">{{ alert.time?.substring(11, 19) || '' }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- LLM 运行状况 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span>LLM 运行状况</span>
          <span class="card-sub">近 {{ days }} 天</span>
        </div>
      </template>
      <el-skeleton :loading="loading" animated :rows="2">
        <el-row :gutter="16">
          <el-col :xs="12" :sm="8" :md="4">
            <div class="stat-item">
              <div class="stat-value">{{ summary?.llm.total ?? 0 }}</div>
              <div class="stat-label">总调用</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <div class="stat-item">
              <div class="stat-value success">{{ summary?.llm.success ?? 0 }}</div>
              <div class="stat-label">成功</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <div class="stat-item">
              <div class="stat-value danger">{{ summary?.llm.failed ?? 0 }}</div>
              <div class="stat-label">失败</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <div class="stat-item">
              <div class="stat-value">{{ Math.round((summary?.llm.successRate ?? 0) * 100) }}%</div>
              <div class="stat-label">成功率</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <div class="stat-item">
              <div class="stat-value">{{ summary?.llm.avgLatencyMs ?? 0 }}<span class="unit">ms</span></div>
              <div class="stat-label">平均延迟</div>
            </div>
          </el-col>
        </el-row>
      </el-skeleton>
    </el-card>

    <!-- 最近调用 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span>最近 LLM 调用</span>
        </div>
      </template>
      <el-table :data="recentCalls" v-loading="recentLoading" size="small">
        <el-table-column prop="modelId" label="模型" min-width="140">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.modelId || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalTokens" label="Token" width="100" align="center">
          <template #default="{ row }">{{ row.totalTokens ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="latencyMs" label="延迟" width="100" align="center">
          <template #default="{ row }">{{ row.latencyMs ?? '-' }} ms</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'success'" size="small" type="success">
              <el-icon style="margin-right: 3px"><SuccessFilled /></el-icon>成功
            </el-tag>
            <el-tag v-else size="small" type="danger">
              <el-icon style="margin-right: 3px"><CircleCloseFilled /></el-icon>失败
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="调用时间" width="180">
          <template #default="{ row }">{{ row.createTime ?? '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard-page {
  padding: 4px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.page-title h2 {
  margin: 0;
  font-size: 20px;
}

.page-desc {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.kpi-row {
  margin-bottom: 16px;
}

.kpi-card {
  margin-bottom: 16px;
}

.kpi-body {
  display: flex;
  align-items: center;
  gap: 12px;
}

.kpi-icon {
  width: 46px;
  height: 46px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kpi-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
}

.kpi-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.chart-card {
  margin-bottom: 16px;
}

.chart-box {
  height: 320px;
}

.chart-box-sm {
  height: 240px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.card-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-secondary);
}

.section-card {
  margin-bottom: 16px;
}

.stat-item {
  text-align: center;
  padding: 8px 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
}

.stat-value.success {
  color: var(--el-color-success);
}

.stat-value.danger {
  color: var(--el-color-danger);
}

.stat-value .unit {
  font-size: 13px;
  font-weight: 400;
  color: var(--text-secondary);
  margin-left: 2px;
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

/* 告警区域 */
.alert-empty {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 20px 0;
  color: var(--text-secondary);
  font-size: 13px;
  justify-content: center;
}

.alert-list {
  max-height: 200px;
  overflow-y: auto;
}

.alert-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-subtle, #f0f0f0);
  font-size: 13px;
}

.alert-item:last-child {
  border-bottom: none;
}

.alert-msg {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
}

.alert-time {
  font-size: 11px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}
</style>
