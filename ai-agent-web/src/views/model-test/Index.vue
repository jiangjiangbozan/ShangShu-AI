<script setup lang="ts">
/**
 * 模型测试页面 - 大模型原型页面
 *
 * 设计理念：
 * - 响应式栅格：桌面端双栏（输入 8 / 响应 16），移动端单栏堆叠
 * - 视觉层次：页面标题 → 操作区 → 结果区 → 历史区
 * - 现代交互：加载骨架屏、统计卡片、时间线历史
 * - 品牌一致性：使用 design-tokens.css 中的变量
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ElCard,
  ElForm,
  ElFormItem,
  ElSelect,
  ElOption,
  ElInput,
  ElButton,
  ElTag,
  ElEmpty,
  ElRow,
  ElCol,
  ElIcon,
  ElSkeleton,
  ElSkeletonItem,
  ElTimeline,
  ElTimelineItem,
  ElTooltip,
  ElDivider,
} from 'element-plus'
import {
  Promotion,
  Delete,
  Refresh,
  CopyDocument,
  Cpu,
  Lightning,
  DataLine,
  Clock,
  Document,
  ChatLineSquare,
  MagicStick,
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import { pingModel, type ModelPingResult } from '@/api/model'

/** 模型选项 */
const MODEL_OPTIONS = [
  { label: 'DeepSeek Chat', value: 'deepseek-chat', provider: 'deepseek', description: '通用对话模型，擅长中英文理解' },
  { label: '通义千问 Plus', value: 'qwen-plus', provider: 'qwen', description: '阿里云通义系列，中文表现优秀' },
  { label: 'Ollama Qwen2.5 7B（本地）', value: 'qwen2.5:7b', provider: 'ollama', description: '本地部署，无需联网' },
]

/** 预设提示词示例 */
const PROMPT_EXAMPLES = [
  { text: '你好，请用一句话介绍你自己', icon: ChatLineSquare },
  { text: '请用 Markdown 格式写一段关于人工智能的简介', icon: Document },
  { text: '解释一下什么是 RAG（检索增强生成）', icon: MagicStick },
  { text: '帮我写一个简单的 Python HTTP 服务器', icon: Cpu },
]

/** 表单 */
const form = reactive({
  modelId: 'deepseek-chat',
  prompt: '',
})

/** 加载中 */
const loading = ref(false)

/** 当前响应结果 */
const currentResult = ref<ModelPingResult | null>(null)

/** 历史记录 */
interface HistoryItem {
  timestamp: number
  modelId: string
  prompt: string
  result: ModelPingResult
}
const history = ref<HistoryItem[]>([])

/** 统计：总调用次数 */
const totalCount = computed(() => history.value.length)

/** 统计：平均耗时 */
const avgLatency = computed(() => {
  if (history.value.length === 0) return 0
  const total = history.value.reduce((sum, h) => sum + (h.result.latencyMs || 0), 0)
  return Math.round(total / history.value.length)
})

/** 统计：总 Token 消耗 */
const totalTokens = computed(() => {
  return history.value.reduce((sum, h) => sum + (h.result.totalTokens || 0), 0)
})

/** 渲染 Markdown */
function renderMarkdown(content: string): string {
  try {
    return marked.parse(content || '') as string
  } catch (e) {
    return content || ''
  }
}

/** 调用模型 */
async function handlePing() {
  if (!form.modelId) {
    ElMessage.warning('请选择模型')
    return
  }
  if (!form.prompt.trim()) {
    ElMessage.warning('请输入提示词')
    return
  }

  loading.value = true
  currentResult.value = null

  try {
    const result = await pingModel({
      modelId: form.modelId,
      prompt: form.prompt.trim(),
    })
    currentResult.value = result
    history.value.unshift({
      timestamp: Date.now(),
      modelId: form.modelId,
      prompt: form.prompt.trim(),
      result,
    })
    if (history.value.length > 20) {
      history.value = history.value.slice(0, 20)
    }
    saveHistory()
    ElMessage.success('调用成功')
  } catch (error) {
    console.error('[model-test][ping error]', error)
  } finally {
    loading.value = false
  }
}

function useExample(prompt: string) {
  form.prompt = prompt
}

function clearCurrent() {
  currentResult.value = null
  form.prompt = ''
}

function clearHistory() {
  history.value = []
  localStorage.removeItem('model-test-history')
  ElMessage.success('已清空历史')
}

async function copyContent(content: string) {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

function saveHistory() {
  try {
    localStorage.setItem('model-test-history', JSON.stringify(history.value))
  } catch {
    /* ignore */
  }
}

function loadHistory() {
  try {
    const saved = localStorage.getItem('model-test-history')
    if (saved) {
      history.value = JSON.parse(saved)
    }
  } catch {
    history.value = []
  }
}

/** 格式化时间 */
function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleString('zh-CN', { hour12: false })
}

/** 格式化相对时间 */
function formatRelativeTime(timestamp: number): string {
  const diff = Date.now() - timestamp
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return formatTime(timestamp)
}

onMounted(loadHistory)
</script>

<template>
  <div class="model-test-page">
    <!-- ============ 页面标题 ============ -->
    <header class="page-header">
      <div class="page-title-block">
        <div class="page-icon">
          <el-icon :size="24"><Lightning /></el-icon>
        </div>
        <div>
          <h1 class="page-title">大模型测试台</h1>
          <p class="page-subtitle">实时测试多模型调用，对比性能与生成效果</p>
        </div>
      </div>

      <!-- 统计卡片 -->
      <div class="stats-row">
        <div class="stat-card">
          <div class="stat-icon stat-icon-blue"><el-icon><DataLine /></el-icon></div>
          <div>
            <div class="stat-value">{{ totalCount }}</div>
            <div class="stat-label">总调用</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon stat-icon-green"><el-icon><Clock /></el-icon></div>
          <div>
            <div class="stat-value">{{ avgLatency }}ms</div>
            <div class="stat-label">平均耗时</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon stat-icon-purple"><el-icon><Cpu /></el-icon></div>
          <div>
            <div class="stat-value">{{ totalTokens.toLocaleString() }}</div>
            <div class="stat-label">总 Tokens</div>
          </div>
        </div>
      </div>
    </header>

    <el-row :gutter="24" class="content-grid">
      <!-- ============ 左侧：输入区 ============ -->
      <el-col :xs="24" :lg="10" :xl="9">
        <el-card shadow="never" class="input-card">
          <template #header>
            <div class="card-header">
              <div class="card-title-block">
                <el-icon class="card-icon"><Promotion /></el-icon>
                <span class="card-title">调用配置</span>
              </div>
              <el-tag type="success" effect="light" round size="small">原型</el-tag>
            </div>
          </template>

          <el-form label-position="top" class="input-form">
            <el-form-item label="选择模型">
              <el-select
                v-model="form.modelId"
                placeholder="请选择模型"
                class="model-select"
              >
                <el-option
                  v-for="opt in MODEL_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                >
                  <div class="model-option">
                    <el-icon><Cpu /></el-icon>
                    <div class="model-option-text">
                      <div class="model-option-label">{{ opt.label }}</div>
                      <div class="model-option-desc">{{ opt.description }}</div>
                    </div>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="提示词">
              <el-input
                v-model="form.prompt"
                type="textarea"
                :rows="6"
                placeholder="请输入测试提示词，如：你好，请用一句话介绍你自己"
                maxlength="2000"
                show-word-limit
                resize="vertical"
                class="prompt-input"
              />
            </el-form-item>

            <el-form-item>
              <div class="examples">
                <span class="examples-label">快速示例：</span>
                <div class="examples-list">
                  <button
                    v-for="ex in PROMPT_EXAMPLES"
                    :key="ex.text"
                    class="example-chip"
                    @click="useExample(ex.text)"
                  >
                    <el-icon><component :is="ex.icon" /></el-icon>
                    <span>{{ ex.text.length > 22 ? ex.text.slice(0, 22) + '…' : ex.text }}</span>
                  </button>
                </div>
              </div>
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="loading"
                :icon="Promotion"
                class="call-btn"
                @click="handlePing"
              >
                {{ loading ? '调用中…' : '调用模型' }}
              </el-button>
              <el-button :icon="Refresh" size="large" @click="clearCurrent">清空</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- ============ 右侧：响应区 ============ -->
      <el-col :xs="24" :lg="14" :xl="15">
        <el-card shadow="never" class="response-card">
          <template #header>
            <div class="card-header">
              <div class="card-title-block">
                <el-icon class="card-icon"><ChatLineSquare /></el-icon>
                <span class="card-title">响应结果</span>
              </div>
              <div v-if="currentResult" class="header-actions">
                <el-tooltip content="复制响应" placement="top">
                  <el-button text circle :icon="CopyDocument" @click="copyContent(currentResult.content)" />
                </el-tooltip>
              </div>
            </div>
          </template>

          <!-- 空态 -->
          <el-empty
            v-if="!currentResult && !loading"
            description="点击「调用模型」按钮开始测试"
            :image-size="120"
          >
            <template #description>
              <p class="empty-text">点击「调用模型」按钮开始测试</p>
              <p class="empty-sub">支持 DeepSeek、通义千问、Ollama 多模型对比</p>
            </template>
          </el-empty>

          <!-- 加载骨架屏 -->
          <div v-else-if="loading" class="skeleton-wrap">
            <div class="metadata-skeleton">
              <el-skeleton-item variant="text" style="width: 30%" />
              <el-skeleton-item variant="text" style="width: 25%" />
              <el-skeleton-item variant="text" style="width: 25%" />
            </div>
            <el-divider />
            <el-skeleton :rows="6" animated />
          </div>

          <!-- 结果展示 -->
          <div v-else-if="currentResult" class="result-container">
            <!-- 元数据卡片组 -->
            <div class="metadata">
              <div class="meta-item meta-model">
                <el-icon><Cpu /></el-icon>
                <span>{{ currentResult.modelId }}</span>
              </div>
              <div class="meta-item meta-latency">
                <el-icon><Clock /></el-icon>
                <span class="meta-label">耗时</span>
                <span class="meta-value">{{ currentResult.latencyMs }}ms</span>
              </div>
              <div class="meta-item meta-tokens">
                <el-icon><DataLine /></el-icon>
                <span class="meta-label">Tokens</span>
                <span class="meta-value">{{ currentResult.totalTokens }}</span>
              </div>
              <div class="meta-item meta-detail">
                <span class="meta-mini">输入 {{ currentResult.promptTokens }}</span>
                <el-divider direction="vertical" />
                <span class="meta-mini">输出 {{ currentResult.completionTokens }}</span>
              </div>
            </div>

            <el-divider />

            <!-- 提问回显 -->
            <div class="prompt-preview">
              <div class="prompt-preview-label">
                <el-icon><ChatLineSquare /></el-icon>
                <span>提问</span>
              </div>
              <div class="prompt-preview-text">{{ form.prompt }}</div>
            </div>

            <!-- 回答内容 -->
            <div class="response-block">
              <div class="response-label">
                <el-icon><MagicStick /></el-icon>
                <span>回答</span>
              </div>
              <div class="response-content markdown-body" v-html="renderMarkdown(currentResult.content)" />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ 历史记录时间线 ============ -->
    <el-card v-if="history.length > 0" shadow="never" class="history-card">
      <template #header>
        <div class="card-header">
          <div class="card-title-block">
            <el-icon class="card-icon"><DataLine /></el-icon>
            <span class="card-title">调用历史</span>
            <el-tag round size="small" type="info">{{ history.length }} 条</el-tag>
          </div>
          <el-button text size="small" :icon="Delete" @click="clearHistory">清空历史</el-button>
        </div>
      </template>

      <el-timeline class="history-timeline">
        <el-timeline-item
          v-for="(item, idx) in history"
          :key="idx"
          :timestamp="formatRelativeTime(item.timestamp)"
          placement="top"
          type="primary"
          :hollow="idx !== 0"
        >
          <div class="history-item">
            <div class="history-meta">
              <el-tag size="small" effect="light">{{ item.modelId }}</el-tag>
              <span class="history-time">{{ formatTime(item.timestamp) }}</span>
              <el-tag size="small" type="info" effect="plain">耗时 {{ item.result.latencyMs }}ms</el-tag>
              <el-tag size="small" type="warning" effect="plain">{{ item.result.totalTokens }} tokens</el-tag>
            </div>
            <div class="history-prompt">{{ item.prompt }}</div>
            <div class="history-response markdown-body" v-html="renderMarkdown(item.result.content)" />
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<style scoped>
.model-test-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

/* ============ 页面头部 ============ */
.page-header {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.page-title-block {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.page-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: var(--radius-xl);
  color: #fff;
  box-shadow: 0 8px 24px rgb(99 102 241 / 0.3);
}

.page-title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--text-primary);
  margin: 0;
}

.page-subtitle {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-top: 4px;
}

/* ============ 统计卡片 ============ */
.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-3);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  background-color: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
  transition: all var(--transition-fast);
}

.stat-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-lg);
  font-size: 18px;
  color: #fff;
}

.stat-icon-blue {
  background: linear-gradient(135deg, #3b82f6, #06b6d4);
}
.stat-icon-green {
  background: linear-gradient(135deg, #10b981, #84cc16);
}
.stat-icon-purple {
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
}

.stat-value {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  color: var(--text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

/* ============ 卡片通用 ============ */
.input-card,
.response-card,
.history-card {
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  overflow: hidden;
  transition: box-shadow var(--transition-fast);
}
.input-card:hover,
.response-card:hover,
.history-card:hover {
  box-shadow: var(--shadow-md);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  background-color: var(--bg-subtle);
  border-bottom: 1px solid var(--border-default);
}

.card-title-block {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.card-icon {
  color: var(--color-primary-500);
  font-size: 18px;
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}

/* ============ 输入区 ============ */
.input-form :deep(.el-form-item__label) {
  font-weight: var(--font-weight-medium);
  color: var(--text-secondary);
  padding-bottom: 6px;
}

.model-select {
  width: 100%;
}

.model-option {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 4px 0;
}

.model-option-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.model-option-label {
  font-size: var(--font-size-base);
  color: var(--text-primary);
}

.model-option-desc {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.prompt-input :deep(.el-textarea__inner) {
  border-radius: var(--radius-md);
  font-family: var(--font-sans);
  line-height: var(--line-height-relaxed);
}

.examples {
  width: 100%;
}

.examples-label {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-bottom: var(--space-2);
}

.examples-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.example-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-full);
  background-color: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.example-chip:hover {
  border-color: var(--color-primary-400);
  background-color: var(--color-primary-50);
  color: var(--color-primary-700);
}
.example-chip :deep(.el-icon) {
  font-size: 13px;
}

.call-btn {
  flex: 1;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border: none;
  box-shadow: 0 4px 12px rgb(99 102 241 / 0.3);
}
.call-btn:hover {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 6px 16px rgb(99 102 241 / 0.4);
}

/* ============ 响应区 ============ */
.empty-text {
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  margin: 0;
}
.empty-sub {
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
  margin-top: 4px;
}

.skeleton-wrap {
  padding: var(--space-4) 0;
}
.metadata-skeleton {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.result-container {
  display: flex;
  flex-direction: column;
}

.metadata {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: var(--radius-full);
  font-size: 13px;
  border: 1px solid var(--border-default);
}
.meta-item :deep(.el-icon) {
  font-size: 14px;
}

.meta-model {
  background-color: var(--color-primary-50);
  border-color: var(--color-primary-200);
  color: var(--color-primary-700);
  font-weight: var(--font-weight-medium);
}
.meta-latency {
  background-color: var(--color-success-soft);
  border-color: #a7f3d0;
  color: #065f46;
}
.meta-tokens {
  background-color: var(--color-warning-soft);
  border-color: #fde68a;
  color: #92400e;
}
.meta-detail {
  border: none;
  padding: 0;
  color: var(--text-tertiary);
}
.meta-mini {
  font-size: 12px;
}
.meta-label {
  color: var(--text-tertiary);
}
.meta-value {
  font-weight: var(--font-weight-semibold);
}

.prompt-preview {
  padding: var(--space-3) var(--space-4);
  background-color: var(--bg-subtle);
  border-left: 3px solid var(--color-primary-400);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-4);
}

.prompt-preview-label,
.response-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: var(--font-weight-semibold);
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: var(--space-2);
}
.prompt-preview-label :deep(.el-icon),
.response-label :deep(.el-icon) {
  color: var(--color-primary-500);
}

.prompt-preview-text {
  font-size: var(--font-size-base);
  color: var(--text-primary);
  line-height: var(--line-height-relaxed);
}

.response-block {
  margin-top: var(--space-2);
}

.response-content {
  padding: var(--space-5);
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  min-height: 120px;
  line-height: var(--line-height-loose);
  color: var(--text-primary);
}

/* ============ 历史时间线 ============ */
.history-timeline {
  padding-left: var(--space-2);
}

.history-item {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  transition: box-shadow var(--transition-fast);
}
.history-item:hover {
  box-shadow: var(--shadow-sm);
}

.history-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.history-time {
  color: var(--text-tertiary);
  font-size: 12px;
}

.history-prompt {
  padding: var(--space-2) var(--space-3);
  background-color: var(--bg-subtle);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: var(--space-2);
  line-height: var(--line-height-normal);
}

.history-response {
  padding: var(--space-3);
  background-color: var(--bg-card);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-subtle);
  font-size: 13px;
  line-height: var(--line-height-normal);
}

/* ============ Markdown 样式 ============ */
:deep(.markdown-body) {
  font-family: var(--font-sans);
  color: var(--text-primary);
  word-wrap: break-word;
}

:deep(.markdown-body p) {
  margin: var(--space-2) 0;
}

:deep(.markdown-body h1),
:deep(.markdown-body h2),
:deep(.markdown-body h3),
:deep(.markdown-body h4) {
  margin: var(--space-4) 0 var(--space-2);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}

:deep(.markdown-body h1) {
  font-size: var(--font-size-xl);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--border-default);
}
:deep(.markdown-body h2) {
  font-size: var(--font-size-lg);
}
:deep(.markdown-body h3) {
  font-size: var(--font-size-md);
}

:deep(.markdown-body code) {
  padding: 2px 6px;
  background-color: var(--color-gray-100);
  color: var(--color-primary-700);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 0.875em;
}

:deep(.markdown-body pre) {
  padding: var(--space-4);
  background-color: var(--color-gray-900);
  color: var(--color-gray-100);
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin: var(--space-3) 0;
}

:deep(.markdown-body pre code) {
  background: none;
  padding: 0;
  color: inherit;
  font-size: 13px;
}

:deep(.markdown-body ul),
:deep(.markdown-body ol) {
  padding-left: var(--space-6);
  margin: var(--space-2) 0;
}

:deep(.markdown-body ul) {
  list-style: disc;
}
:deep(.markdown-body ol) {
  list-style: decimal;
}

:deep(.markdown-body li) {
  margin: 4px 0;
}

:deep(.markdown-body blockquote) {
  margin: var(--space-3) 0;
  padding: var(--space-2) var(--space-4);
  border-left: 4px solid var(--color-primary-400);
  background-color: var(--color-primary-50);
  color: var(--text-secondary);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
}

:deep(.markdown-body table) {
  border-collapse: collapse;
  margin: var(--space-3) 0;
  width: 100%;
}

:deep(.markdown-body th),
:deep(.markdown-body td) {
  border: 1px solid var(--border-default);
  padding: var(--space-2) var(--space-3);
  text-align: left;
}

:deep(.markdown-body th) {
  background-color: var(--bg-subtle);
  font-weight: var(--font-weight-semibold);
}

:deep(.markdown-body a) {
  color: var(--color-primary-600);
}

:deep(.markdown-body img) {
  max-width: 100%;
  border-radius: var(--radius-md);
}

/* ============ 响应式 ============ */
@media (max-width: 768px) {
  .page-title {
    font-size: var(--font-size-xl);
  }
  .stats-row {
    grid-template-columns: 1fr;
  }
  .card-header {
    padding: var(--space-3) var(--space-4);
  }
  .response-content {
    padding: var(--space-3);
  }
  .metadata {
    gap: var(--space-2);
  }
  .meta-item {
    font-size: 12px;
  }
}

@media (max-width: 480px) {
  .page-icon {
    width: 40px;
    height: 40px;
  }
  .stat-card {
    padding: var(--space-3);
  }
  .stat-icon {
    width: 32px;
    height: 32px;
  }
}
</style>
