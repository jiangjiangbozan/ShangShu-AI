<script setup lang="ts">
/**
 * Agent 任务编排页面（add-agent-runtime）
 *
 * 功能：
 * 1. Runtime 管理：创建 / 启动 / 暂停 / 恢复 / 取消 / 查询状态
 * 2. Skill 列表：列出所有已启用 Skill 元数据
 * 3. Trace 时间轴：按 stepIndex 升序展示 Thought/Action/Observation，附性能指标
 *
 * 对应后端 AgentRuntimeController / SkillController / TraceController
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  ElCard,
  ElButton,
  ElTable,
  ElTableColumn,
  ElTag,
  ElInput,
  ElForm,
  ElFormItem,
  ElDialog,
  ElMessage,
  ElTabs,
  ElTabPane,
  ElTimeline,
  ElTimelineItem,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElIcon,
  ElRow,
  ElCol,
  ElStatistic,
} from 'element-plus'
import { VideoPlay, VideoPause, Refresh, Close, View, Search, Cpu, Plus } from '@element-plus/icons-vue'
import {
  createRuntime,
  startRuntime,
  pauseRuntime,
  resumeRuntime,
  cancelRuntime,
  getRuntime,
  listSkills,
  invokeSkill,
  replayTrace,
  traceMetrics,
  streamAgentRuntime,
  type AgentRuntimeResp,
  type SkillDefinitionResp,
  type AgentTraceResp,
  type TraceMetricsResp,
} from '@/api/agent'

// ============ Runtime 管理 ============
const runtimeIdInput = ref<number | undefined>()
const currentRuntime = ref<AgentRuntimeResp | null>(null)
const loadingRuntime = ref(false)

const statusTagType = (status: string) => {
  switch (status) {
    case 'running':
      return 'success'
    case 'paused':
      return 'warning'
    case 'failed':
      return 'danger'
    case 'cancelled':
      return 'info'
    case 'done':
      return 'primary'
    default:
      return 'info'
  }
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    pending: '待启动',
    running: '运行中',
    paused: '已暂停',
    done: '已完成',
    failed: '失败',
    cancelled: '已取消',
  }
  return map[status] ?? status
}

/** 创建 runtime */
async function handleCreate() {
  try {
    const data = await createRuntime({})
    const id = (data as { runtimeId?: number })?.runtimeId
    if (id) {
      runtimeIdInput.value = id
      ElMessage.success(`已创建 runtime #${id}`)
      await loadRuntime()
    }
  } catch (e) {
    // 错误已由 request 拦截器统一提示
  }
}

/** 加载当前 runtime 详情 */
async function loadRuntime() {
  if (!runtimeIdInput.value) return
  loadingRuntime.value = true
  try {
    currentRuntime.value = await getRuntime(runtimeIdInput.value)
  } finally {
    loadingRuntime.value = false
  }
}

/** 启动 runtime */
async function handleStart() {
  if (!runtimeIdInput.value) return
  await startRuntime(runtimeIdInput.value)
  ElMessage.success('已派发异步任务')
  setTimeout(loadRuntime, 500)
}

/** 暂停 */
async function handlePause() {
  if (!runtimeIdInput.value) return
  await pauseRuntime(runtimeIdInput.value)
  ElMessage.success('已暂停')
  await loadRuntime()
}

/** 恢复 */
async function handleResume() {
  if (!runtimeIdInput.value) return
  await resumeRuntime(runtimeIdInput.value)
  ElMessage.success('已恢复')
  setTimeout(loadRuntime, 500)
}

/** 取消 */
async function handleCancel() {
  if (!runtimeIdInput.value) return
  await cancelRuntime(runtimeIdInput.value)
  ElMessage.success('已取消')
  await loadRuntime()
}

// ============ Skill 列表 ============
const skills = ref<SkillDefinitionResp[]>([])
const loadingSkills = ref(false)
const invokeDialogVisible = ref(false)
const invokingSkill = ref<SkillDefinitionResp | null>(null)
const invokeInput = ref('')

async function loadSkills() {
  loadingSkills.value = true
  try {
    skills.value = await listSkills()
  } finally {
    loadingSkills.value = false
  }
}

/** 打开 Skill 调试对话框 */
function openInvokeDialog(skill: SkillDefinitionResp) {
  invokingSkill.value = skill
  invokeInput.value = '{\n  "expr": "1+2*3"\n}'
  invokeDialogVisible.value = true
}

/** 调用 Skill */
async function handleInvoke() {
  if (!invokingSkill.value) return
  let input: Record<string, unknown>
  try {
    input = JSON.parse(invokeInput.value)
  } catch {
    ElMessage.error('输入 JSON 格式错误')
    return
  }
  const result = await invokeSkill(invokingSkill.value.name, 'latest', input)
  ElMessage.success('调用成功，结果见控制台')
  console.log('[Skill invoke result]', result)
}

// ============ Trace 时间轴 ============
const traceRuntimeId = ref<number | undefined>()
const traces = ref<AgentTraceResp[]>([])
const metrics = ref<TraceMetricsResp | null>(null)
const loadingTrace = ref(false)

async function loadTrace() {
  if (!traceRuntimeId.value) return
  loadingTrace.value = true
  try {
    const [list, m] = await Promise.all([
      replayTrace(traceRuntimeId.value),
      traceMetrics(traceRuntimeId.value),
    ])
    traces.value = list
    metrics.value = m
  } finally {
    loadingTrace.value = false
  }
}

/** 实时事件追加的 Trace（带 live 标记） */
interface LiveTrace extends AgentTraceResp {
  live?: boolean
}

// ============ 实时执行流（SSE 协议 v2） ============
const streamController = ref<AbortController | null>(null)
const liveStatus = ref('')
let liveSeq = 0

/** 连接实时执行流：实时渲染 thought/action/observation 结构化事件 */
function connectStream() {
  if (!traceRuntimeId.value) {
    ElMessage.warning('请先输入 runtime ID')
    return
  }
  disconnectStream()
  liveSeq = 0
  liveStatus.value = '连接中…'
  streamController.value = streamAgentRuntime(traceRuntimeId.value, {
    onThought(data) {
      liveStatus.value = `推理 Step ${data.step}`
      pushLiveTrace({
        stepType: 'thought',
        stepIndex: data.step,
        output: data.content,
      })
    },
    onAction(data) {
      liveStatus.value = `调用 Skill ${data.skillId}`
      pushLiveTrace({
        stepType: 'action',
        stepIndex: liveSeq,
        skillId: data.skillId,
        input: JSON.stringify(data.input, null, 2),
      })
    },
    onObservation(data) {
      liveStatus.value = `Observation Step ${data.step}`
      pushLiveTrace({
        stepType: 'observation',
        stepIndex: data.step,
        output: JSON.stringify(data.output, null, 2),
      })
    },
    onDone(data) {
      liveStatus.value = `已完成（${data.latencyMs}ms）`
      disconnectStream()
    },
    onError(data) {
      liveStatus.value = `错误: ${data.message}`
      pushLiveTrace({
        stepType: 'error',
        stepIndex: liveSeq,
        error: `${data.code}: ${data.message}`,
      })
      disconnectStream()
    },
    onClose() {
      if (!liveStatus.value || liveStatus.value === '连接中…') {
        liveStatus.value = '连接已关闭'
      }
    },
  })
  if (streamController.value) {
    liveStatus.value = '实时监听中…'
  }
}

/** 将实时事件追加为 timeline 条目（负数 id 区分历史 trace） */
function pushLiveTrace(partial: Partial<AgentTraceResp>) {
  traces.value.push({
    id: -Date.now() - liveSeq,
    runtimeId: traceRuntimeId.value ?? 0,
    stepIndex: 0,
    stepType: 'thought',
    skillId: null,
    input: null,
    output: null,
    tokenIn: null,
    tokenOut: null,
    latencyMs: null,
    parentStepId: null,
    error: null,
    dlqMessageId: null,
    createTime: new Date().toLocaleString(),
    ...partial,
    live: true,
  } as LiveTrace)
  liveSeq++
}

/** 关闭实时执行流 */
function disconnectStream() {
  streamController.value?.abort()
  streamController.value = null
}

onUnmounted(() => disconnectStream())

const traceTagType = (stepType: string) => {
  switch (stepType) {
    case 'thought':
      return 'primary'
    case 'action':
      return 'success'
    case 'observation':
      return 'info'
    case 'resume':
      return 'warning'
    case 'error':
      return 'danger'
    default:
      return 'info'
  }
}

const traceTimestamp = (t: string) => {
  try {
    return new Date(t).toLocaleString()
  } catch {
    return t
  }
}

/** 截断字符串 */
function truncate(s: string | null, max = 200) {
  if (!s) return ''
  return s.length > max ? s.substring(0, max) + '...' : s
}

// ============ Tab ============
const activeTab = ref('runtime')

onMounted(() => {
  loadSkills()
})
</script>

<template>
  <div class="agent-view">
    <el-tabs v-model="activeTab" class="agent-tabs">
      <!-- ============ Runtime 管理 ============ -->
      <el-tab-pane label="Runtime 管理" name="runtime">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><Cpu /></el-icon>
              <span>Agent Runtime 生命周期管理</span>
            </div>
          </template>

          <el-form inline>
            <el-form-item label="Runtime ID">
              <el-input
                v-model.number="runtimeIdInput"
                placeholder="输入 runtime ID"
                style="width: 200px"
                clearable
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" :loading="loadingRuntime" @click="loadRuntime">
                查询
              </el-button>
              <el-button type="success" :icon="Plus" @click="handleCreate">新建 Runtime</el-button>
            </el-form-item>
          </el-form>

          <el-descriptions
            v-if="currentRuntime"
            :column="3"
            border
            class="runtime-desc"
          >
            <el-descriptions-item label="ID">{{ currentRuntime.id }}</el-descriptions-item>
            <el-descriptions-item label="Session ID">
              {{ currentRuntime.sessionId }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(currentRuntime.status)">
                {{ statusLabel(currentRuntime.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Model ID">
              {{ currentRuntime.modelId || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Step Index">
              {{ currentRuntime.nextStepIndex }}
            </el-descriptions-item>
            <el-descriptions-item label="心跳时间">
              {{ currentRuntime.lastHeartbeat }}
            </el-descriptions-item>
            <el-descriptions-item label="最终答案" :span="3">
              <pre class="answer-pre">{{ currentRuntime.finalAnswer || '-' }}</pre>
            </el-descriptions-item>
            <el-descriptions-item v-if="currentRuntime.errorMessage" label="错误" :span="3">
              <pre class="error-pre">{{ currentRuntime.errorMessage }}</pre>
            </el-descriptions-item>
          </el-descriptions>

          <div v-else class="empty-tip">
            <el-empty description="输入或创建 Runtime" />
          </div>

          <div v-if="currentRuntime" class="action-bar">
            <el-button
              type="success"
              :icon="VideoPlay"
              :disabled="currentRuntime.status !== 'pending' && currentRuntime.status !== 'paused'"
              @click="currentRuntime.status === 'paused' ? handleResume() : handleStart()"
            >
              {{ currentRuntime.status === 'paused' ? '恢复' : '启动' }}
            </el-button>
            <el-button
              type="warning"
              :icon="VideoPause"
              :disabled="currentRuntime.status !== 'running'"
              @click="handlePause"
            >
              暂停
            </el-button>
            <el-button
              :icon="Refresh"
              :disabled="currentRuntime.status !== 'paused'"
              @click="handleResume"
            >
              恢复
            </el-button>
            <el-button
              type="danger"
              :icon="Close"
              :disabled="['done', 'failed', 'cancelled'].includes(currentRuntime.status)"
              @click="handleCancel"
            >
              取消
            </el-button>
            <el-button :icon="Refresh" @click="loadRuntime">刷新</el-button>
            <el-button
              type="primary"
              :icon="VideoPlay"
              @click="traceRuntimeId = currentRuntime.id; activeTab = 'trace'; loadTrace()"
            >
              查看 Trace
            </el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- ============ Skill 列表 ============ -->
      <el-tab-pane label="Skill 列表" name="skills">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><Cpu /></el-icon>
              <span>已注册 Skill 元数据</span>
              <el-button text :icon="Refresh" @click="loadSkills">刷新</el-button>
            </div>
          </template>

          <el-table :data="skills" v-loading="loadingSkills" stripe>
            <el-table-column prop="name" label="Skill 名" width="160" />
            <el-table-column prop="version" label="版本" width="90" />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag :type="row.type === 'local' ? 'success' : row.type === 'kb' ? 'warning' : 'info'">
                  {{ row.type }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="200" />
            <el-table-column prop="permissionTag" label="权限" width="100" />
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'">
                  {{ row.enabled === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button
                  text
                  type="primary"
                  @click="openInvokeDialog(row as SkillDefinitionResp)"
                >
                  调用
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ============ Trace 时间轴 ============ -->
      <el-tab-pane label="Trace 时间轴" name="trace">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><VideoPlay /></el-icon>
              <span>全链路 Trace 回放</span>
            </div>
          </template>

          <el-form inline>
            <el-form-item label="Runtime ID">
              <el-input
                v-model.number="traceRuntimeId"
                placeholder="输入 runtime ID"
                style="width: 200px"
                clearable
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" :loading="loadingTrace" @click="loadTrace">
                查询
              </el-button>
              <el-button
                type="success"
                :icon="streamController ? Close : VideoPlay"
                @click="streamController ? disconnectStream() : connectStream()"
              >
                {{ streamController ? '停止实时' : '实时执行' }}
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 实时执行状态 -->
          <div v-if="liveStatus" class="live-status" :class="{ 'is-live': streamController }">
            <span class="live-dot" />{{ liveStatus }}
          </div>

          <!-- 性能指标 -->
          <el-row v-if="metrics" :gutter="16" class="metrics-row">
            <el-col :span="6">
              <el-statistic title="总延迟 (ms)" :value="metrics.total_latency_ms" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="输入 Token" :value="metrics.total_token_in" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="输出 Token" :value="metrics.total_token_out" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="步骤数" :value="metrics.step_count" />
            </el-col>
          </el-row>

          <el-timeline v-if="traces.length" class="trace-timeline">
            <el-timeline-item
              v-for="t in traces"
              :key="t.id"
              :timestamp="traceTimestamp(t.createTime)"
              :type="traceTagType(t.stepType)"
              placement="top"
            >
              <el-card shadow="hover" class="trace-card">
                <div class="trace-header">
                  <el-tag :type="traceTagType(t.stepType)" size="small">
                    {{ t.stepType }}
                  </el-tag>
                  <span class="trace-step">Step {{ t.stepIndex }}</span>
                  <span v-if="t.skillId" class="trace-skill">skill: {{ t.skillId }}</span>
                  <span v-if="t.latencyMs" class="trace-latency">{{ t.latencyMs }}ms</span>
                  <span v-if="t.tokenIn || t.tokenOut" class="trace-token">
                    tokens: {{ t.tokenIn || 0 }} / {{ t.tokenOut || 0 }}
                  </span>
                </div>
                <div v-if="t.input" class="trace-section">
                  <div class="section-label">输入</div>
                  <pre class="trace-pre">{{ truncate(t.input) }}</pre>
                </div>
                <div v-if="t.output" class="trace-section">
                  <div class="section-label">输出</div>
                  <pre class="trace-pre">{{ truncate(t.output) }}</pre>
                </div>
                <div v-if="t.error" class="trace-section error">
                  <div class="section-label">错误</div>
                  <pre class="trace-pre">{{ t.error }}</pre>
                </div>
              </el-card>
            </el-timeline-item>
          </el-timeline>

          <el-empty v-else description="输入 Runtime ID 查询 Trace" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ Skill 调用对话框 ============ -->
    <el-dialog v-model="invokeDialogVisible" title="调用 Skill" width="640px">
      <div v-if="invokingSkill" class="invoke-meta">
        <el-tag>{{ invokingSkill.name }}#{{ invokingSkill.version }}</el-tag>
        <span class="invoke-desc">{{ invokingSkill.description }}</span>
      </div>
      <el-form>
        <el-form-item label="输入 JSON">
          <el-input
            v-model="invokeInput"
            type="textarea"
            :rows="10"
            placeholder='{"expr":"1+2*3"}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invokeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleInvoke">调用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.agent-view {
  padding: 16px 0;
}

.agent-tabs {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: 16px;
  box-shadow: var(--shadow-sm);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: var(--font-weight-semibold);
}

.card-header .el-button {
  margin-left: auto;
}

.runtime-desc {
  margin-top: 16px;
}

.answer-pre,
.error-pre {
  margin: 0;
  padding: 8px;
  background: var(--bg-subtle);
  border-radius: var(--radius-md);
  font-family: 'Cascadia Code', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}

.error-pre {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.action-bar {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.empty-tip {
  padding: 40px 0;
}

.metrics-row {
  margin: 16px 0;
  padding: 16px;
  background: var(--bg-subtle);
  border-radius: var(--radius-md);
}

.trace-timeline {
  margin-top: 16px;
}

.trace-card {
  margin-bottom: 0;
}

.trace-header {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--text-secondary);
  flex-wrap: wrap;
}

.trace-step {
  font-weight: var(--font-weight-medium);
}

.trace-skill {
  color: var(--el-color-primary);
}

.trace-live {
  font-size: 12px;
  color: var(--el-color-success);
  font-weight: var(--font-weight-semibold);
}

.live-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 8px 0;
  padding: 6px 12px;
  border-radius: var(--radius-md);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 13px;
}

.live-status.is-live {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: live-pulse 1.2s ease-in-out infinite;
}

@keyframes live-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

.trace-latency,
.trace-token {
  color: var(--text-tertiary);
  font-family: 'Cascadia Code', monospace;
}

.trace-section {
  margin-top: 8px;
}

.section-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 4px;
}

.trace-pre {
  margin: 0;
  padding: 6px 8px;
  background: var(--bg-subtle);
  border-radius: var(--radius-sm);
  font-family: 'Cascadia Code', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}

.trace-section.error .trace-pre {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.invoke-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.invoke-desc {
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
