<script setup lang="ts">
/**
 * 客服工作台页（小程序端）
 *
 * 对标 Web 端 CustomerServiceView（精简版）：
 * - 会话列表（AI/人工/完成/失败状态）+ 人工接管
 * - 标注回流
 * - KB 命中率统计
 */
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import NavBar from '@/components/common/NavBar.vue'
import {
  csChat,
  kbHitSummary,
  listCsSessions,
  listLabels,
  submitLabel,
  takeoverSession,
} from '@/api/customer-service'
import type { CsLabel, CsSession, KbHitSummary } from '@/types/customer-service'

const sessions = ref<CsSession[]>([])
const total = ref(0)
const loading = ref(false)
const statusFilter = ref('') // '' | ai | human | done | failed

const summary = ref<KbHitSummary | null>(null)

const chatRuntimeId = ref('')
const chatQuery = ref('')
const chatting = ref(false)
const chatResp = ref('')
const chatIntent = ref('')

const labelSessionId = ref('')
const labelType = ref('positive')
const labelContent = ref('')
const submitting = ref(false)
const labels = ref<CsLabel[]>([])

const STATUS_TEXT: Record<string, string> = {
  ai: 'AI 接待',
  human: '人工',
  done: '已结束',
  failed: '失败',
}

const FILTERS = [
  { label: '全部', value: '' },
  { label: 'AI', value: 'ai' },
  { label: '人工', value: 'human' },
  { label: '已结束', value: 'done' },
  { label: '失败', value: 'failed' },
]

const LABEL_TYPES = [
  { label: '好评', value: 'positive' },
  { label: '差评', value: 'negative' },
  { label: '中性', value: 'neutral' },
]

onShow(() => {
  loadSessions()
  loadSummary()
})

async function loadSessions() {
  loading.value = true
  try {
    const res = await listCsSessions({ status: statusFilter.value || undefined, size: 50 })
    sessions.value = res.list
    total.value = res.total
  } catch {
    // 已 toast
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  try {
    summary.value = await kbHitSummary()
  } catch {
    // 已 toast
  }
}

function changeFilter(value: string) {
  statusFilter.value = value
  loadSessions()
}

async function handleTakeover(session: CsSession) {
  uni.showModal({
    title: '人工接管',
    content: `确定接管会话 #${session.sessionId} 吗？`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await takeoverSession(session.sessionId)
        uni.showToast({ title: '已接管', icon: 'success' })
        await loadSessions()
      } catch {
        // 已 toast
      }
    },
  })
}

async function handleChat() {
  const id = Number(chatRuntimeId.value)
  const q = chatQuery.value.trim()
  if (!id) {
    uni.showToast({ title: '请输入 runtime ID', icon: 'none' })
    return
  }
  if (!q) {
    uni.showToast({ title: '请输入问题', icon: 'none' })
    return
  }
  chatting.value = true
  chatResp.value = ''
  chatIntent.value = ''
  try {
    const res = await csChat(id, q)
    const intentText = `意图：${res.intent.intent}（置信度 ${Math.round(res.intent.confidence * 100)}%）`
    const slots =
      res.slots.filled_slots && Object.keys(res.slots.filled_slots).length
        ? `已填槽：${JSON.stringify(res.slots.filled_slots)}`
        : ''
    const missing =
      res.slots.missing_slots && res.slots.missing_slots.length
        ? `待补槽位：${res.slots.missing_slots.join('、')}`
        : ''
    chatIntent.value = [intentText, slots, missing].filter(Boolean).join('\n')
    chatResp.value = res.kb_answer || '（未命中知识库）'
  } catch {
    // 已 toast
  } finally {
    chatting.value = false
  }
}

async function handleLoadLabels() {
  const id = Number(labelSessionId.value)
  if (!id) {
    uni.showToast({ title: '请输入会话 ID', icon: 'none' })
    return
  }
  try {
    labels.value = await listLabels(id)
    if (!labels.value.length) {
      uni.showToast({ title: '该会话暂无标注', icon: 'none' })
    }
  } catch {
    // 已 toast
  }
}

async function handleSubmitLabel() {
  const id = Number(labelSessionId.value)
  if (!id) {
    uni.showToast({ title: '请输入会话 ID', icon: 'none' })
    return
  }
  if (!labelContent.value.trim()) {
    uni.showToast({ title: '请输入标注内容', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await submitLabel(id, { labelType: labelType.value, content: labelContent.value.trim() })
    uni.showToast({ title: '标注成功', icon: 'success' })
    labelContent.value = ''
    await handleLoadLabels()
  } catch {
    // 已 toast
  } finally {
    submitting.value = false
  }
}

function statusClass(status: string): string {
  if (status === 'ai') return 'is-ai'
  if (status === 'human') return 'is-human'
  if (status === 'done') return 'is-done'
  return 'is-failed'
}

const hitRate = computed(() =>
  summary.value ? Math.round(summary.value.hit_rate * 100) + '%' : '-',
)
</script>

<template>
  <view class="cs-page">
    <NavBar title="客服工作台" show-back />

    <!-- KB 命中统计 -->
    <view class="cs-card">
      <view class="cs-card__title">KB 命中统计</view>
      <view v-if="summary" class="cs-stats">
        <view class="cs-stat">
          <text class="cs-stat__num">{{ summary.total }}</text>
          <text class="cs-stat__label">总请求</text>
        </view>
        <view class="cs-stat">
          <text class="cs-stat__num">{{ summary.hit }}</text>
          <text class="cs-stat__label">命中</text>
        </view>
        <view class="cs-stat">
          <text class="cs-stat__num cs-stat__num--accent">{{ hitRate }}</text>
          <text class="cs-stat__label">命中率</text>
        </view>
      </view>
      <view v-else class="cs-hint">暂无统计数据</view>
    </view>

    <!-- 客服入口（编排测试） -->
    <view class="cs-card">
      <view class="cs-card__title">客服编排测试</view>
      <view class="cs-row">
        <input
          v-model="chatRuntimeId"
          class="cs-input cs-input--id"
          type="number"
          placeholder="runtime ID"
        />
        <input
          v-model="chatQuery"
          class="cs-input"
          placeholder="输入用户问题"
          @confirm="handleChat"
        />
        <button class="cs-btn" :loading="chatting" :disabled="chatting" @tap="handleChat">发送</button>
      </view>
      <view v-if="chatIntent" class="cs-chat__intent">{{ chatIntent }}</view>
      <view v-if="chatResp" class="cs-chat__answer">{{ chatResp }}</view>
    </view>

    <!-- 会话列表 -->
    <view class="cs-card">
      <view class="cs-card__row">
        <text class="cs-card__title">会话列表（{{ total }}）</text>
      </view>
      <view class="cs-filters">
        <text
          v-for="f in FILTERS"
          :key="f.value"
          class="cs-filter"
          :class="{ 'is-active': statusFilter === f.value }"
          @tap="changeFilter(f.value)"
        >{{ f.label }}</text>
      </view>

      <view v-if="loading" class="cs-hint">加载中...</view>
      <view v-else-if="!sessions.length" class="cs-hint">暂无会话</view>
      <view v-else class="cs-session">
        <view v-for="s in sessions" :key="s.id" class="cs-session__item">
          <view class="cs-session__info">
            <view class="cs-session__head">
              <text class="cs-session__id">会话 #{{ s.sessionId }}</text>
              <text class="cs-session__tag" :class="statusClass(s.status)">{{ STATUS_TEXT[s.status] || s.status }}</text>
            </view>
            <text class="cs-session__meta">runtime: {{ s.runtimeId ?? '-' }}</text>
            <text v-if="s.assignedTo" class="cs-session__meta">客服 #{{ s.assignedTo }}</text>
          </view>
          <button
            v-if="s.status === 'ai'"
            class="cs-btn cs-btn--mini"
            @tap="handleTakeover(s)"
          >接管</button>
        </view>
      </view>
    </view>

    <!-- 标注回流 -->
    <view class="cs-card">
      <view class="cs-card__title">标注回流</view>
      <view class="cs-row">
        <input v-model="labelSessionId" class="cs-input cs-input--id" type="number" placeholder="会话 ID" />
        <button class="cs-btn cs-btn--ghost" @tap="handleLoadLabels">查看标注</button>
      </view>
      <view class="cs-label-types">
        <text
          v-for="t in LABEL_TYPES"
          :key="t.value"
          class="cs-label-type"
          :class="{ 'is-active': labelType === t.value }"
          @tap="labelType = t.value"
        >{{ t.label }}</text>
      </view>
      <textarea
        v-model="labelContent"
        class="cs-textarea"
        placeholder="标注内容，如：回答准确、需要补充细节等"
        :maxlength="500"
      />
      <view class="cs-row cs-row--end">
        <button class="cs-btn" :loading="submitting" :disabled="submitting" @tap="handleSubmitLabel">提交标注</button>
      </view>
      <view v-if="labels.length" class="cs-labels">
        <view v-for="l in labels" :key="l.id" class="cs-label">
          <text class="cs-label__type">{{ l.labelType }}</text>
          <text class="cs-label__content">{{ l.labelContent }}</text>
          <text class="cs-label__time">{{ l.createTime }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.cs-page {
  min-height: 100vh;
  background-color: $bg-page;
  padding-bottom: $space-8;
}

.cs-card {
  margin: $space-3;
  padding: $space-4;
  background-color: $bg-card;
  border-radius: $radius-xl;
  box-shadow: $shadow-xs;
}

.cs-card__title {
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  color: $text-primary;
}

.cs-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cs-hint {
  padding: $space-4 0;
  text-align: center;
  font-size: $font-size-sm;
  color: $text-tertiary;
}

/* 统计 */
.cs-stats {
  display: flex;
  justify-content: space-around;
  margin-top: $space-3;
}

.cs-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.cs-stat__num {
  font-size: $font-size-2xl;
  font-weight: $font-weight-bold;
  color: $text-primary;

  &--accent {
    color: $color-primary;
  }
}

.cs-stat__label {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

/* 输入 */
.cs-row {
  display: flex;
  align-items: center;
  gap: $space-2;
  margin-top: $space-3;
  flex-wrap: wrap;

  &--end {
    justify-content: flex-end;
  }
}

.cs-input {
  flex: 1;
  min-width: 180rpx;
  height: 68rpx;
  padding: 0 $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  color: $text-primary;

  &--id {
    flex: none;
    width: 200rpx;
    min-width: 0;
  }
}

.cs-btn {
  margin: 0;
  height: 68rpx;
  line-height: 68rpx;
  padding: 0 $space-4;
  font-size: $font-size-sm;
  color: $text-inverse;
  background-color: $color-primary;
  border-radius: $radius-lg;
  border: none;

  &::after {
    border: none;
  }

  &--ghost {
    color: $text-secondary;
    background-color: $bg-card;
    border: 1rpx solid $border-default;
  }

  &--mini {
    height: 56rpx;
    line-height: 56rpx;
    padding: 0 $space-3;
    font-size: $font-size-xs;
    flex-shrink: 0;
  }
}

.cs-chat__intent {
  margin-top: $space-3;
  padding: $space-2 $space-3;
  font-size: $font-size-sm;
  color: $color-info;
  background-color: $color-info-soft;
  border-radius: $radius-md;
  white-space: pre-wrap;
}

.cs-chat__answer {
  margin-top: $space-2;
  padding: $space-3;
  font-size: $font-size-sm;
  color: $text-primary;
  background-color: $bg-subtle;
  border-radius: $radius-md;
  line-height: $line-height-snug;
  word-break: break-all;
}

/* 筛选 */
.cs-filters {
  display: flex;
  gap: $space-2;
  margin: $space-3 0;
  flex-wrap: wrap;
}

.cs-filter {
  padding: 6rpx 20rpx;
  font-size: $font-size-xs;
  color: $text-secondary;
  background-color: $bg-subtle;
  border-radius: $radius-full;

  &.is-active {
    color: $text-inverse;
    background-color: $color-primary;
  }
}

/* 会话 */
.cs-session {
  display: flex;
  flex-direction: column;
  gap: $space-2;
}

.cs-session__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-2;
  padding: $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
}

.cs-session__info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  flex: 1;
}

.cs-session__head {
  display: flex;
  align-items: center;
  gap: $space-2;
}

.cs-session__id {
  font-size: $font-size-sm;
  font-weight: $font-weight-semibold;
  color: $text-primary;
}

.cs-session__tag {
  font-size: $font-size-xs;
  padding: 2rpx 12rpx;
  border-radius: $radius-full;

  &.is-ai {
    color: $color-primary;
    background-color: $color-primary-50;
  }

  &.is-human {
    color: $color-info;
    background-color: $color-info-soft;
  }

  &.is-done {
    color: $color-success;
    background-color: $color-success-soft;
  }

  &.is-failed {
    color: $color-danger;
    background-color: $color-danger-soft;
  }
}

.cs-session__meta {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

/* 标注 */
.cs-label-types {
  display: flex;
  gap: $space-2;
  margin-top: $space-3;
}

.cs-label-type {
  padding: 6rpx 20rpx;
  font-size: $font-size-xs;
  color: $text-secondary;
  background-color: $bg-subtle;
  border-radius: $radius-full;

  &.is-active {
    color: $text-inverse;
    background-color: $color-primary;
  }
}

.cs-textarea {
  width: 100%;
  height: 140rpx;
  margin-top: $space-2;
  padding: $space-2 $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  color: $text-primary;
  box-sizing: border-box;
}

.cs-labels {
  margin-top: $space-3;
  display: flex;
  flex-direction: column;
  gap: $space-2;
}

.cs-label {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  padding: $space-2 $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-md;
}

.cs-label__type {
  font-size: $font-size-xs;
  color: $color-primary-600;
}

.cs-label__content {
  font-size: $font-size-sm;
  color: $text-primary;
}

.cs-label__time {
  font-size: $font-size-xs;
  color: $text-tertiary;
}
</style>
