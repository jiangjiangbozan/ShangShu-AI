<script setup lang="ts">
/**
 * Agent 任务编排页（小程序端）
 *
 * 对标 Web 端 AgentView（精简版）：
 * - runtime 生命周期管理（创建/启动/暂停/恢复/取消/刷新）
 * - Skill 列表
 * - Trace 回放时间轴
 *
 * 说明：本页仅做小程序端 UI 编排，不包含 Web 端的高级配置。
 */
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import NavBar from '@/components/common/NavBar.vue'
import {
  cancelRuntime,
  createRuntime,
  getRuntime,
  listSkills,
  pauseRuntime,
  replayTrace,
  resumeRuntime,
  startRuntime,
} from '@/api/agent'
import type { AgentRuntimeResp, AgentTraceResp, SkillDefinitionResp } from '@/types/agent'

const runtime = ref<AgentRuntimeResp | null>(null)
const runtimeIdInput = ref('')
const creating = ref(false)
const operating = ref(false)
const skills = ref<SkillDefinitionResp[]>([])
const traces = ref<AgentTraceResp[]>([])
const traceLoading = ref(false)
const activeTrace = ref(false)

const STATUS_TEXT: Record<string, string> = {
  pending: '等待中',
  running: '运行中',
  paused: '已暂停',
  done: '已完成',
  failed: '失败',
  cancelled: '已取消',
}

const isRunning = computed(() => runtime.value?.status === 'running')
const isPaused = computed(() => runtime.value?.status === 'paused')

onShow(() => {
  loadSkills()
  // 若已有 runtime，刷新状态
  if (runtime.value?.id) refreshRuntime()
})

async function loadSkills() {
  try {
    skills.value = await listSkills()
  } catch {
    // 已 toast
  }
}

async function handleCreate() {
  creating.value = true
  try {
    const res = await createRuntime()
    if (res.runtimeId != null) {
      runtimeIdInput.value = String(res.runtimeId)
      runtime.value = await getRuntime(res.runtimeId)
      uni.showToast({ title: `已创建 runtime ${res.runtimeId}`, icon: 'success' })
    }
  } catch {
    // 已 toast
  } finally {
    creating.value = false
  }
}

async function handleLoad() {
  const id = Number(runtimeIdInput.value)
  if (!id) {
    uni.showToast({ title: '请输入 runtime ID', icon: 'none' })
    return
  }
  await refreshRuntime()
}

async function refreshRuntime() {
  const id = Number(runtimeIdInput.value) || runtime.value?.id
  if (!id) return
  try {
    runtime.value = await getRuntime(id)
  } catch {
    // 已 toast
  }
}

async function operate(action: 'start' | 'pause' | 'resume' | 'cancel') {
  const id = runtime.value?.id
  if (id == null) {
    uni.showToast({ title: '请先创建/加载 runtime', icon: 'none' })
    return
  }
  operating.value = true
  try {
    if (action === 'start') await startRuntime(id)
    else if (action === 'pause') await pauseRuntime(id)
    else if (action === 'resume') await resumeRuntime(id)
    else await cancelRuntime(id)
    await refreshRuntime()
  } catch {
    // 已 toast
  } finally {
    operating.value = false
  }
}

async function handleTrace() {
  const id = runtime.value?.id
  if (id == null) {
    uni.showToast({ title: '请先创建/加载 runtime', icon: 'none' })
    return
  }
  if (activeTrace.value) {
    activeTrace.value = false
    traces.value = []
    return
  }
  traceLoading.value = true
  activeTrace.value = true
  traces.value = []
  try {
    traces.value = await replayTrace(id)
    if (!traces.value.length) {
      uni.showToast({ title: '暂无 Trace 记录', icon: 'none' })
    }
  } catch {
    // 已 toast
  } finally {
    traceLoading.value = false
  }
}

const STEP_TEXT: Record<string, string> = {
  thought: '思考',
  action: '调用工具',
  observation: '观察结果',
  resume: '恢复执行',
  error: '错误',
}

function stepClass(type: string): string {
  if (type === 'error') return 'is-error'
  if (type === 'thought') return 'is-thought'
  if (type === 'action') return 'is-action'
  if (type === 'observation') return 'is-obs'
  return 'is-resume'
}

function prettyJson(text: string | null): string {
  if (!text) return ''
  try {
    const obj = JSON.parse(text)
    return JSON.stringify(obj, null, 2)
  } catch {
    return text
  }
}
</script>

<template>
  <view class="ag-page">
    <NavBar title="Agent 任务编排" show-back />

    <!-- Runtime 管理 -->
    <view class="ag-card">
      <view class="ag-card__title">Runtime 管理</view>
      <view class="ag-row">
        <input
          v-model="runtimeIdInput"
          class="ag-input"
          type="number"
          placeholder="runtime ID"
        />
        <button class="ag-btn ag-btn--ghost" @tap="handleLoad">加载</button>
      </view>
      <view class="ag-row">
        <button class="ag-btn" :loading="creating" :disabled="creating" @tap="handleCreate">新建 Runtime</button>
        <button class="ag-btn" :disabled="operating" @tap="operate('start')">启动</button>
        <button class="ag-btn ag-btn--warn" :disabled="operating || !isRunning" @tap="operate('pause')">暂停</button>
        <button class="ag-btn ag-btn--ghost" :disabled="operating || !isPaused" @tap="operate('resume')">恢复</button>
        <button class="ag-btn ag-btn--danger" :disabled="operating" @tap="operate('cancel')">取消</button>
      </view>

      <!-- Runtime 状态 -->
      <view v-if="runtime" class="ag-status">
        <view class="ag-status__line">
          <text class="ag-status__label">ID</text>
          <text class="ag-status__value">{{ runtime.id }}</text>
          <text class="ag-status__tag" :class="'st-' + runtime.status">{{ STATUS_TEXT[runtime.status] || runtime.status }}</text>
        </view>
        <view class="ag-status__line">
          <text class="ag-status__label">模型</text>
          <text class="ag-status__value">{{ runtime.modelId || '默认' }}</text>
        </view>
        <view v-if="runtime.finalAnswer" class="ag-status__block">
          <text class="ag-status__label">最终回答</text>
          <text class="ag-status__answer">{{ runtime.finalAnswer }}</text>
        </view>
        <view v-if="runtime.errorMessage" class="ag-status__block">
          <text class="ag-status__label">错误</text>
          <text class="ag-status__error">{{ runtime.errorMessage }}</text>
        </view>
      </view>
      <view v-else class="ag-hint">点击「新建 Runtime」或输入 ID 加载</view>

      <!-- Trace 时间轴 -->
      <view class="ag-row ag-row--end">
        <button class="ag-btn ag-btn--ghost" @tap="handleTrace">
          {{ activeTrace ? '收起 Trace' : '回放 Trace' }}
        </button>
      </view>
      <view v-if="traceLoading" class="ag-hint">加载中...</view>
      <view v-else-if="activeTrace && traces.length" class="ag-trace">
        <view
          v-for="step in traces"
          :key="step.id"
          class="ag-step"
        >
          <view class="ag-step__head">
            <text class="ag-step__badge" :class="stepClass(step.stepType)">{{ STEP_TEXT[step.stepType] || step.stepType }}</text>
            <text class="ag-step__meta">#{{ step.stepIndex }}</text>
            <text v-if="step.skillId" class="ag-step__skill">{{ step.skillId }}</text>
            <text v-if="step.latencyMs != null" class="ag-step__meta">{{ step.latencyMs }}ms</text>
          </view>
          <text v-if="step.input" class="ag-step__body">{{ prettyJson(step.input) }}</text>
          <text v-if="step.output" class="ag-step__body">{{ prettyJson(step.output) }}</text>
          <text v-if="step.error" class="ag-step__error">{{ step.error }}</text>
        </view>
      </view>
    </view>

    <!-- Skill 列表 -->
    <view class="ag-card">
      <view class="ag-card__title">已启用的 Skill（{{ skills.length }}）</view>
      <view v-if="!skills.length" class="ag-hint">暂无 Skill</view>
      <view v-else class="ag-skill">
        <view v-for="skill in skills" :key="skill.id" class="ag-skill__item">
          <view class="ag-skill__head">
            <text class="ag-skill__name">{{ skill.name }}</text>
            <text class="ag-skill__type">{{ skill.type }}</text>
            <text class="ag-skill__ver">v{{ skill.version }}</text>
          </view>
          <text class="ag-skill__desc">{{ skill.description }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.ag-page {
  min-height: 100vh;
  background-color: $bg-page;
  padding-bottom: $space-8;
}

.ag-card {
  margin: $space-3;
  padding: $space-4;
  background-color: $bg-card;
  border-radius: $radius-xl;
  box-shadow: $shadow-xs;
}

.ag-card__title {
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  color: $text-primary;
  margin-bottom: $space-3;
}

.ag-row {
  display: flex;
  align-items: center;
  gap: $space-2;
  flex-wrap: wrap;
  margin-bottom: $space-3;

  &--end {
    justify-content: flex-end;
    margin-bottom: 0;
  }
}

.ag-input {
  flex: 1;
  min-width: 200rpx;
  height: 68rpx;
  padding: 0 $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  color: $text-primary;
}

.ag-btn {
  margin: 0;
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 $space-3;
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

  &--warn {
    color: $color-warning;
    background-color: $color-warning-soft;
  }

  &--danger {
    color: $color-danger;
    background-color: $color-danger-soft;
  }
}

.ag-hint {
  font-size: $font-size-sm;
  color: $text-tertiary;
  padding: $space-2 0;
}

.ag-status {
  display: flex;
  flex-direction: column;
  gap: $space-2;
  padding: $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
}

.ag-status__line {
  display: flex;
  align-items: center;
  gap: $space-2;
}

.ag-status__label {
  font-size: $font-size-xs;
  color: $text-tertiary;
  width: 96rpx;
  flex-shrink: 0;
}

.ag-status__value {
  font-size: $font-size-sm;
  color: $text-primary;
}

.ag-status__tag {
  font-size: $font-size-xs;
  padding: 2rpx 14rpx;
  border-radius: $radius-full;

  &.st-pending,
  &.st-running {
    color: $color-warning;
    background-color: $color-warning-soft;
  }

  &.st-paused {
    color: $color-info;
    background-color: $color-info-soft;
  }

  &.st-done {
    color: $color-success;
    background-color: $color-success-soft;
  }

  &.st-failed,
  &.st-cancelled {
    color: $color-danger;
    background-color: $color-danger-soft;
  }
}

.ag-status__block {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.ag-status__answer {
  font-size: $font-size-sm;
  color: $text-primary;
  line-height: $line-height-snug;
  word-break: break-all;
}

.ag-status__error {
  font-size: $font-size-sm;
  color: $color-danger;
  word-break: break-all;
}

.ag-trace {
  margin-top: $space-3;
  display: flex;
  flex-direction: column;
  gap: $space-2;
}

.ag-step {
  padding: $space-2 $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-md;
}

.ag-step__head {
  display: flex;
  align-items: center;
  gap: $space-2;
}

.ag-step__badge {
  font-size: $font-size-xs;
  padding: 2rpx 12rpx;
  border-radius: $radius-full;

  &.is-thought {
    color: $color-info;
    background-color: $color-info-soft;
  }

  &.is-action {
    color: $color-primary;
    background-color: $color-primary-50;
  }

  &.is-obs {
    color: $color-success;
    background-color: $color-success-soft;
  }

  &.is-resume {
    color: $color-warning;
    background-color: $color-warning-soft;
  }

  &.is-error {
    color: $color-danger;
    background-color: $color-danger-soft;
  }
}

.ag-step__meta {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.ag-step__skill {
  font-size: $font-size-xs;
  color: $text-secondary;
}

.ag-step__body {
  display: block;
  margin-top: $space-1;
  font-size: $font-size-xs;
  font-family: $font-mono;
  color: $text-secondary;
  white-space: pre-wrap;
  word-break: break-all;
}

.ag-step__error {
  display: block;
  margin-top: $space-1;
  font-size: $font-size-xs;
  color: $color-danger;
  word-break: break-all;
}

.ag-skill {
  display: flex;
  flex-direction: column;
  gap: $space-2;
}

.ag-skill__item {
  padding: $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
}

.ag-skill__head {
  display: flex;
  align-items: center;
  gap: $space-2;
}

.ag-skill__name {
  font-size: $font-size-sm;
  font-weight: $font-weight-semibold;
  color: $text-primary;
}

.ag-skill__type {
  font-size: $font-size-xs;
  color: $color-primary-600;
  background-color: $color-primary-50;
  padding: 2rpx 12rpx;
  border-radius: $radius-full;
}

.ag-skill__ver {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.ag-skill__desc {
  display: block;
  margin-top: $space-1;
  font-size: $font-size-xs;
  color: $text-secondary;
}
</style>
