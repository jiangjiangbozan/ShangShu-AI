<script setup lang="ts">
/**
 * 数据分析页（小程序端）
 *
 * 对标 Web 端 DataAnalysisView（精简版）：
 * - 自然语言问数（NL2SQL）
 * - Canvas 自绘图表（bar / line / pie）
 * - 明细表 + Markdown 报告生成与复制
 */
import { computed, nextTick, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '@/components/common/NavBar.vue'
import { askData, fetchDataSchema, generateReport } from '@/api/data-analysis'
import { parseMarkdown } from '@/composables/useMarkdown'
import type { DataAskResp, DataRow, DataSchemaResp } from '@/types/data-analysis'

const CHART_COLORS = ['#6366f1', '#22d3ee', '#fbbf24', '#34d399', '#f87171', '#a78bfa']

const loading = ref(false)
const schema = ref<DataSchemaResp | null>(null)
const schemaOpen = ref(false)
const question = ref('')
const asking = ref(false)
const result = ref<DataAskResp | null>(null)
const error = ref('')
const reportLoading = ref(false)
const report = ref('')

const SAMPLES = [
  '近7天每天新增多少会话',
  '按供应商统计模型配置数量',
  '各角色对话消息数量占比',
  '各知识库的文档数对比',
]

onLoad(() => {
  loadSchema()
})

async function loadSchema() {
  loading.value = true
  try {
    schema.value = await fetchDataSchema()
  } catch {
    // 失败静默，不阻塞问数
  } finally {
    loading.value = false
  }
}

async function ask() {
  const q = question.value.trim()
  if (!q) {
    uni.showToast({ title: '请输入问题', icon: 'none' })
    return
  }
  asking.value = true
  error.value = ''
  result.value = null
  report.value = ''
  try {
    result.value = await askData(q)
    await nextTick()
    drawChart()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    asking.value = false
  }
}

async function generate() {
  const r = result.value
  if (!r) return
  reportLoading.value = true
  try {
    report.value = await generateReport({
      question: r.question,
      sql: r.sql,
      columns: r.columns,
      rows: r.rows,
    })
  } catch {
    // 错误已由 request 层 toast
  } finally {
    reportLoading.value = false
  }
}

function copyReport() {
  if (!report.value) {
    uni.showToast({ title: '请先生成报告', icon: 'none' })
    return
  }
  uni.setClipboardData({
    data: report.value,
    success: () => uni.showToast({ title: '报告已复制', icon: 'success' }),
  })
}

/** 数值列（排除首列分类列后的其余列） */
const numericColumns = computed(() => {
  const r = result.value
  if (!r || r.columns.length < 2) return []
  return r.columns.slice(1)
})

/** 是否为图表型结果 */
const isChart = computed(() => {
  const r = result.value
  return !!r && r.rows.length > 0 && r.chartType !== 'table'
})

/** 饼图图例（label + 值 + 占比） */
const pieLegend = computed(() => {
  const r = result.value
  if (!r || r.chartType !== 'pie' || r.columns.length < 2) return []
  const [labelCol, valueCol] = r.columns
  const values = r.rows.map((row) => Number(row[valueCol] ?? 0) || 0)
  const total = values.reduce((a, b) => a + b, 0) || 1
  return r.rows.map((row, i) => ({
    label: String(row[labelCol] ?? ''),
    value: values[i],
    percent: ((values[i] / total) * 100).toFixed(1),
    color: CHART_COLORS[i % CHART_COLORS.length],
  }))
})

/** 单元格文本 */
function cellText(row: DataRow, col: string): string {
  const v = row[col]
  return v === null || v === undefined ? '-' : String(v)
}

/** 报告 Markdown → 安全 HTML */
const reportHtml = computed(() => parseMarkdown(report.value))

/* ============ Canvas 图表绘制 ============ */

function drawChart() {
  const r = result.value
  if (!r || !r.rows.length) return
  uni.createSelectorQuery()
    .select('#chart')
    .boundingClientRect((rect: UniApp.NodeInfo | UniApp.NodeInfo[]) => {
      const box = Array.isArray(rect) ? rect[0] : rect
      if (!box || !box.width) return
      const ctx = uni.createCanvasContext('chart')
      const W = box.width
      const H = box.height || 0
      if (r.chartType === 'pie') {
        drawPie(ctx, W, H, r)
      } else if (r.chartType === 'bar' || r.chartType === 'line') {
        drawBarLine(ctx, W, H, r, r.chartType)
      }
      ctx.draw()
    })
    .exec()
}

function shortNum(v: number): string {
  if (v >= 10000) return (v / 10000).toFixed(1) + 'w'
  if (v >= 1000) return (v / 1000).toFixed(1) + 'k'
  return String(Math.round(v))
}

function drawBarLine(
  ctx: UniApp.CanvasContext,
  W: number,
  H: number,
  r: DataAskResp,
  type: 'bar' | 'line',
) {
  const labelCol = r.columns[0]
  const valueCols = r.columns.slice(1)
  const labels = r.rows.map((row) => String(row[labelCol] ?? ''))
  const maxV = Math.max(
    1,
    ...valueCols.flatMap((c) => r.rows.map((row) => Number(row[c] ?? 0) || 0)),
  )

  const left = 44
  const right = 12
  const top = 16
  const bottom = 28
  const plotW = W - left - right
  const plotH = H - top - bottom
  const catCount = labels.length

  // 横向网格线 + Y 轴刻度
  ctx.setFontSize(10)
  ctx.setTextAlign('right')
  for (let g = 0; g <= 4; g++) {
    const v = (maxV / 4) * g
    const y = top + plotH - (g / 4) * plotH
    ctx.setStrokeStyle('#eef0f3')
    ctx.setLineWidth(1)
    ctx.beginPath()
    ctx.moveTo(left, y)
    ctx.lineTo(W - right, y)
    ctx.stroke()
    ctx.setFillStyle('#9ca3af')
    ctx.fillText(shortNum(v), left - 5, y + 3)
  }

  const step = catCount > 1 ? plotW / (catCount - 1) : plotW / 2

  valueCols.forEach((col, s) => {
    const color = CHART_COLORS[s % CHART_COLORS.length]
    ctx.setStrokeStyle(color)
    ctx.setFillStyle(color)
    if (type === 'bar') {
      const groupW = plotW / catCount
      const barW = Math.min(groupW * 0.7, 32)
      for (let i = 0; i < catCount; i++) {
        const v = Number(r.rows[i][col] ?? 0) || 0
        const h = (v / maxV) * plotH
        const x = left + i * groupW + (groupW - barW) / 2
        ctx.fillRect(x, top + plotH - h, barW, h)
      }
    } else {
      // line
      ctx.setLineWidth(2)
      ctx.beginPath()
      for (let i = 0; i < catCount; i++) {
        const v = Number(r.rows[i][col] ?? 0) || 0
        const x = left + step * i
        const y = top + plotH - (v / maxV) * plotH
        if (i === 0) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
      }
      ctx.stroke()
      // 数据点
      for (let i = 0; i < catCount; i++) {
        const v = Number(r.rows[i][col] ?? 0) || 0
        const x = left + step * i
        const y = top + plotH - (v / maxV) * plotH
        ctx.beginPath()
        ctx.arc(x, y, 3, 0, 2 * Math.PI)
        ctx.fill()
      }
    }
  })

  // X 轴分类标签（过多时跳绘）
  ctx.setFontSize(10)
  ctx.setTextAlign('center')
  const skip = catCount > 10 ? Math.ceil(catCount / 8) : 1
  for (let i = 0; i < catCount; i++) {
    if (i % skip !== 0 && i !== catCount - 1) continue
    const x = catCount > 1 ? left + (plotW * i) / (catCount - 1) : left + plotW / 2
    const label = labels[i].length > 8 ? labels[i].slice(0, 8) + '…' : labels[i]
    ctx.setFillStyle('#6b7280')
    ctx.fillText(label, x, H - 10)
  }
}

function drawPie(ctx: UniApp.CanvasContext, W: number, H: number, r: DataAskResp) {
  const valueCol = r.columns[1] ?? ''
  const values = r.rows.map((row) => Number(row[valueCol] ?? 0) || 0)
  const total = values.reduce((a, b) => a + b, 0)
  if (!total) return
  const cx = W / 2
  const cy = H / 2
  const radius = Math.min(W, H) / 2 - 8
  let start = -Math.PI / 2
  values.forEach((v, i) => {
    const angle = (v / total) * Math.PI * 2
    ctx.beginPath()
    ctx.moveTo(cx, cy)
    ctx.arc(cx, cy, radius, start, start + angle)
    ctx.closePath()
    ctx.setFillStyle(CHART_COLORS[i % CHART_COLORS.length])
    ctx.fill()
    start += angle
  })
  // 中心空白（圆环效果）
  ctx.beginPath()
  ctx.arc(cx, cy, radius * 0.55, 0, 2 * Math.PI)
  ctx.setFillStyle('#ffffff')
  ctx.fill()
}
</script>

<template>
  <view class="da-page">
    <NavBar title="数据分析" show-back />

    <!-- 问数输入 -->
    <view class="da-card">
      <view class="da-card__title">自然语言问数</view>
      <view class="da-input-row">
        <input
          v-model="question"
          class="da-input"
          placeholder="例如：近7天每天新增多少会话"
          confirm-type="search"
          @confirm="ask"
        />
        <button class="da-ask-btn" :loading="asking" :disabled="asking" @tap="ask">问数</button>
      </view>
      <view class="da-samples">
        <text
          v-for="s in SAMPLES"
          :key="s"
          class="da-sample"
          @tap="question = s"
        >{{ s }}</text>
      </view>
      <view v-if="error" class="da-error">{{ error }}</view>
    </view>

    <!-- 可分析数据表 -->
    <view class="da-card">
      <view class="da-card__row" @tap="schemaOpen = !schemaOpen">
        <text class="da-card__title">可分析数据表（{{ schema?.tables.length ?? 0 }}）</text>
        <text class="da-card__arrow">{{ schemaOpen ? '收起' : '展开' }}</text>
      </view>
      <view v-if="schemaOpen && schema" class="da-tables">
        <view v-for="t in schema.tables" :key="t.name" class="da-table">
          <view class="da-table__head">
            <text class="da-table__name">{{ t.name }}</text>
            <text class="da-table__comment">{{ t.comment }}</text>
          </view>
          <view class="da-table__cols">
            <text
              v-for="c in t.columns"
              :key="c.name"
              class="da-table__col"
            >{{ c.name }}<text class="da-table__col-comment"> {{ c.comment }}</text></text>
          </view>
        </view>
      </view>
    </view>

    <!-- 图表结果 -->
    <view v-if="result && result.rows.length" class="da-card">
      <view class="da-card__row">
        <text class="da-card__title">{{ result.chartTitle || result.question }}</text>
        <text class="da-card__meta">{{ result.latencyMs }}ms</text>
      </view>

      <!-- 图例（柱/线：系列名；饼：类别） -->
      <view v-if="isChart" class="da-legend">
        <view v-if="result.chartType === 'pie'" v-for="(item, i) in pieLegend" :key="i" class="da-legend__item">
          <view class="da-legend__dot" :style="{ backgroundColor: item.color }" />
          <text class="da-legend__label">{{ item.label }}</text>
          <text class="da-legend__value">{{ item.value }}（{{ item.percent }}%）</text>
        </view>
        <view v-else v-for="(col, i) in numericColumns" :key="col" class="da-legend__item">
          <view class="da-legend__dot" :style="{ backgroundColor: CHART_COLORS[i % CHART_COLORS.length] }" />
          <text class="da-legend__label">{{ col }}</text>
        </view>
      </view>

      <canvas
        v-if="isChart"
        id="chart"
        canvas-id="chart"
        class="da-chart"
      />
    </view>

    <!-- 明细表 -->
    <view v-if="result && result.rows.length" class="da-card">
      <view class="da-card__title">明细（{{ result.rows.length }} 行）</view>
      <scroll-view scroll-x class="da-table-scroll">
        <view class="da-grid">
          <view class="da-grid__row da-grid__row--head">
            <view v-for="c in result.columns" :key="c" class="da-grid__cell da-grid__cell--head">{{ c }}</view>
          </view>
          <view v-for="(row, i) in result.rows" :key="i" class="da-grid__row">
            <view v-for="c in result.columns" :key="c" class="da-grid__cell">{{ cellText(row, c) }}</view>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 分析报告 -->
    <view v-if="result" class="da-card">
      <view class="da-card__row">
        <text class="da-card__title">分析报告</text>
        <view class="da-report-actions">
          <button class="da-mini-btn" :loading="reportLoading" :disabled="reportLoading" @tap="generate">
            {{ report ? '重新生成' : '生成报告' }}
          </button>
          <button v-if="report" class="da-mini-btn" @tap="copyReport">复制</button>
        </view>
      </view>
      <view v-if="report" class="da-report">
        <rich-text class="da-report__text" :nodes="reportHtml" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.da-page {
  min-height: 100vh;
  background-color: $bg-page;
  padding-bottom: $space-8;
}

.da-card {
  margin: $space-3;
  padding: $space-4;
  background-color: $bg-card;
  border-radius: $radius-xl;
  box-shadow: $shadow-xs;
}

.da-card__title {
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  color: $text-primary;
}

.da-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.da-card__meta {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.da-card__arrow {
  font-size: $font-size-sm;
  color: $color-primary;
}

.da-input-row {
  display: flex;
  align-items: center;
  gap: $space-2;
  margin-top: $space-3;
}

.da-input {
  flex: 1;
  height: 76rpx;
  padding: 0 $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  color: $text-primary;
}

.da-ask-btn {
  width: 140rpx;
  height: 76rpx;
  line-height: 76rpx;
  padding: 0;
  margin: 0;
  font-size: $font-size-base;
  color: $text-inverse;
  background-color: $color-primary;
  border-radius: $radius-lg;
  border: none;

  &::after {
    border: none;
  }
}

.da-samples {
  display: flex;
  flex-wrap: wrap;
  gap: $space-2;
  margin-top: $space-3;
}

.da-sample {
  padding: 8rpx 20rpx;
  font-size: $font-size-xs;
  color: $color-primary-600;
  background-color: $color-primary-50;
  border-radius: $radius-full;
}

.da-error {
  margin-top: $space-3;
  padding: $space-2 $space-3;
  font-size: $font-size-sm;
  color: $color-danger;
  background-color: $color-danger-soft;
  border-radius: $radius-md;
}

.da-tables {
  margin-top: $space-3;
  display: flex;
  flex-direction: column;
  gap: $space-2;
}

.da-table {
  padding: $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
}

.da-table__head {
  display: flex;
  align-items: baseline;
  gap: $space-2;
}

.da-table__name {
  font-family: $font-mono;
  font-size: $font-size-sm;
  font-weight: $font-weight-semibold;
  color: $color-primary-700;
}

.da-table__comment {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.da-table__cols {
  margin-top: $space-2;
  display: flex;
  flex-wrap: wrap;
  gap: $space-1;
}

.da-table__col {
  font-size: $font-size-xs;
  color: $text-secondary;
  background-color: $bg-card;
  padding: 4rpx 12rpx;
  border-radius: $radius-sm;
}

.da-table__col-comment {
  color: $text-tertiary;
}

.da-legend {
  display: flex;
  flex-wrap: wrap;
  gap: $space-2;
  margin: $space-3 0 $space-2;
}

.da-legend__item {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.da-legend__dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: $radius-full;
}

.da-legend__label {
  font-size: $font-size-xs;
  color: $text-secondary;
}

.da-legend__value {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.da-chart {
  width: 690rpx;
  height: 460rpx;
}

.da-table-scroll {
  margin-top: $space-2;
  width: 100%;
}

.da-grid {
  display: inline-block;
  min-width: 100%;
}

.da-grid__row {
  display: flex;
  border-bottom: 1rpx solid $border-subtle;

  &--head {
    background-color: $bg-subtle;
  }

  &:last-of-type {
    border-bottom: none;
  }
}

.da-grid__cell {
  flex-shrink: 0;
  width: 200rpx;
  padding: 12rpx 20rpx;
  font-size: $font-size-xs;
  color: $text-primary;
  border-right: 1rpx solid $border-subtle;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  &--head {
    color: $text-secondary;
    font-weight: $font-weight-medium;
  }
}

.da-report-actions {
  display: flex;
  gap: $space-2;
}

.da-mini-btn {
  margin: 0;
  padding: 0 24rpx;
  height: 60rpx;
  line-height: 60rpx;
  font-size: $font-size-xs;
  color: $color-primary-600;
  background-color: $color-primary-50;
  border-radius: $radius-md;
  border: 1rpx solid $color-primary-200;

  &::after {
    border: none;
  }
}

.da-report {
  margin-top: $space-3;
  padding: $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
}

.da-report__text {
  font-size: $font-size-sm;
  line-height: $line-height-relaxed;
  color: $text-primary;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
