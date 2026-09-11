<script setup lang="ts">
/**
 * 智能客服工作台（add-customer-service）
 *
 * 功能：
 * 1. 客服编排：输入查询，展示意图识别 + KB 回退 + 槽位填充中间结果
 * 2. 会话工作台：分页列出客服会话，支持人工接管
 * 3. KB 命中统计：按天展示命中率折线 + 汇总卡片
 * 4. 标注回流：对会话提交正/负/中性标注
 *
 * 对应后端 CustomerServiceController / SessionController / LabelController / CsStatsController
 */
import { onMounted, ref } from 'vue'
import {
  ElCard,
  ElButton,
  ElTable,
  ElTableColumn,
  ElTag,
  ElInput,
  ElForm,
  ElFormItem,
  ElMessage,
  ElTabs,
  ElTabPane,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElIcon,
  ElRow,
  ElCol,
  ElStatistic,
  ElRadioGroup,
  ElRadio,
  ElRadioButton,
  ElSelect,
  ElOption,
} from 'element-plus'
import { ChatDotRound, Search, DataAnalysis, Service, Pointer } from '@element-plus/icons-vue'
import {
  chat,
  listSessions,
  takeoverSession,
  submitLabel,
  listLabels,
  kbHitSummary,
  type CustomerServiceResp,
  type CsSession,
  type CsLabel,
  type KbHitSummary,
} from '@/api/customer-service'

// ============ Tab 切换 ============
const activeTab = ref('chat')

// ============ 客服编排 ============
const chatQuery = ref('')
const chatLoading = ref(false)
const chatResult = ref<CustomerServiceResp | null>(null)

async function handleChat() {
  if (!chatQuery.value.trim()) {
    ElMessage.warning('请输入查询')
    return
  }
  chatLoading.value = true
  chatResult.value = null
  try {
    chatResult.value = await chat(0, chatQuery.value)
  } catch (e) {
    if (e instanceof Error && e.message) {
      ElMessage.error(e.message)
    }
  } finally {
    chatLoading.value = false
  }
}

const intentTagType = (confidence: number): TagType => {
  if (confidence >= 0.8) return 'success'
  if (confidence >= 0.6) return 'warning'
  return 'info'
}

// ============ 会话工作台 ============
const sessions = ref<CsSession[]>([])
const sessionsTotal = ref(0)
const sessionsPage = ref(1)
const sessionsSize = ref(10)
const sessionsLoading = ref(false)
const statusFilter = ref('')

async function loadSessions() {
  sessionsLoading.value = true
  try {
    const data = await listSessions({
      status: statusFilter.value || undefined,
      page: sessionsPage.value,
      size: sessionsSize.value,
    })
    const page = data as { list: CsSession[]; total: number }
    sessions.value = page.list || []
    sessionsTotal.value = page.total || 0
  } finally {
    sessionsLoading.value = false
  }
}

async function handleTakeover(sessionId: number) {
  await takeoverSession(sessionId)
  ElMessage.success('已接管会话')
  await loadSessions()
}

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const sessionStatusTag = (status: string): { type: TagType; label: string } => {
  switch (status) {
    case 'ai':
      return { type: 'primary', label: 'AI 处理' }
    case 'human':
      return { type: 'warning', label: '人工接管' }
    case 'done':
      return { type: 'success', label: '已完成' }
    case 'failed':
      return { type: 'danger', label: '失败' }
    default:
      return { type: 'info', label: status }
  }
}

// ============ KB 命中统计 ============
const summary = ref<KbHitSummary | null>(null)
const summaryDays = ref(7)

async function loadSummary() {
  summary.value = await kbHitSummary(summaryDays.value)
}

// ============ 标注回流 ============
const labelDialogVisible = ref(false)
const labelSessionId = ref<number>(0)
const labelType = ref<'positive' | 'negative' | 'neutral'>('positive')
const labelContent = ref('')
const labels = ref<CsLabel[]>([])

async function openLabelDialog(sessionId: number) {
  labelSessionId.value = sessionId
  labelType.value = 'positive'
  labelContent.value = ''
  labels.value = await listLabels(sessionId)
  labelDialogVisible.value = true
}

async function handleSubmitLabel() {
  await submitLabel(labelSessionId.value, {
    labelType: labelType.value,
    content: labelContent.value,
  })
  ElMessage.success('标注已提交')
  labelDialogVisible.value = false
}

const labelTagType = (type: string): TagType => {
  switch (type) {
    case 'positive':
      return 'success'
    case 'negative':
      return 'danger'
    default:
      return 'info'
  }
}

onMounted(() => {
  loadSessions()
  loadSummary()
})
</script>

<template>
  <div class="customer-service-view">
    <el-tabs v-model="activeTab" class="cs-tabs">
      <!-- ============ 客服编排 ============ -->
      <el-tab-pane label="客服编排" name="chat">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><Service /></el-icon>
              <span>智能客服编排</span>
            </div>
          </template>
          <el-form @submit.prevent="handleChat">
            <el-form-item>
              <el-input
                v-model="chatQuery"
                type="textarea"
                :rows="3"
                placeholder="输入用户查询，例如：北京地区 iPhone 销量查询"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :icon="Search"
                :loading="chatLoading"
                @click="handleChat"
              >
                执行编排
              </el-button>
            </el-form-item>
          </el-form>

          <div v-if="chatResult" class="chat-result">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="意图识别">
                <el-tag :type="intentTagType(chatResult.intent.confidence)">
                  {{ chatResult.intent.intent }}
                </el-tag>
                <span class="meta">置信度：{{ (chatResult.intent.confidence * 100).toFixed(2) }}%</span>
                <span class="meta" v-if="chatResult.intent.slots_needed?.length">
                  需要槽位：{{ chatResult.intent.slots_needed.join(', ') }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="KB 回退">
                <el-tag v-if="chatResult.kb_answer" type="success">命中</el-tag>
                <el-tag v-else type="info">未命中</el-tag>
                <div v-if="chatResult.kb_answer" class="kb-answer">
                  {{ chatResult.kb_answer }}
                </div>
              </el-descriptions-item>
              <el-descriptions-item label="槽位填充">
                <div v-if="Object.keys(chatResult.slots.filled_slots).length">
                  <div v-for="(val, key) in chatResult.slots.filled_slots" :key="key" class="slot-row">
                    <el-tag size="small">{{ key }}</el-tag>
                    <span>{{ val }}</span>
                  </div>
                </div>
                <div v-else class="empty">暂无已填充槽位</div>
                <div v-if="chatResult.slots.missing_slots?.length" class="missing">
                  缺失：{{ chatResult.slots.missing_slots.join(', ') }}
                </div>
                <el-tag :type="chatResult.slots.is_complete ? 'success' : 'warning'" size="small">
                  {{ chatResult.slots.is_complete ? '完整' : '不完整' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <el-empty v-else description="执行编排后展示中间结果" />
        </el-card>
      </el-tab-pane>

      <!-- ============ 会话工作台 ============ -->
      <el-tab-pane label="会话工作台" name="sessions">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><ChatDotRound /></el-icon>
              <span>客服会话列表</span>
              <el-radio-group v-model="statusFilter" size="small" @change="loadSessions" class="filter">
                <el-radio-button label="">全部</el-radio-button>
                <el-radio-button label="ai">AI 处理</el-radio-button>
                <el-radio-button label="human">人工接管</el-radio-button>
                <el-radio-button label="done">已完成</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-table :data="sessions" v-loading="sessionsLoading" stripe>
            <el-table-column prop="sessionId" label="会话 ID" width="100" />
            <el-table-column prop="status" label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="sessionStatusTag(row.status).type">
                  {{ sessionStatusTag(row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="assignedTo" label="接管人" width="120" />
            <el-table-column prop="takeoverTime" label="接管时间" width="180" />
            <el-table-column prop="updateTime" label="更新时间" width="180" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button
                  size="small"
                  type="warning"
                  :disabled="row.status !== 'ai'"
                  @click="handleTakeover(row.sessionId)"
                >
                  接管
                </el-button>
                <el-button size="small" :icon="Pointer" @click="openLabelDialog(row.sessionId)">
                  标注
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-button size="small" :disabled="sessionsPage <= 1" @click="sessionsPage--; loadSessions()">
              上一页
            </el-button>
            <span>第 {{ sessionsPage }} 页 / 共 {{ Math.ceil(sessionsTotal / sessionsSize) }} 页</span>
            <el-button
              size="small"
              :disabled="sessionsPage * sessionsSize >= sessionsTotal"
              @click="sessionsPage++; loadSessions()"
            >
              下一页
            </el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- ============ KB 命中统计 ============ -->
      <el-tab-pane label="KB 命中统计" name="stats">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><DataAnalysis /></el-icon>
              <span>KB 命中率统计</span>
              <el-select v-model="summaryDays" size="small" @change="loadSummary" class="filter">
                <el-option :value="7" label="近 7 天" />
                <el-option :value="14" label="近 14 天" />
                <el-option :value="30" label="近 30 天" />
              </el-select>
            </div>
          </template>
          <el-row :gutter="20" v-if="summary">
            <el-col :span="8">
              <el-statistic title="总查询数" :value="summary.total" />
            </el-col>
            <el-col :span="8">
              <el-statistic title="命中数" :value="summary.hit" />
            </el-col>
            <el-col :span="8">
              <el-statistic title="命中率" :value="summary.hit_rate * 100" suffix="%" />
            </el-col>
          </el-row>
          <el-table :data="summary?.by_day || []" stripe class="stats-table">
            <el-table-column prop="date" label="日期" width="120" />
            <el-table-column prop="total" label="查询数" width="100" />
            <el-table-column prop="hit" label="命中数" width="100" />
            <el-table-column label="命中率">
              <template #default="{ row }">
                {{ (row.hit_rate * 100).toFixed(2) }}%
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ 标注弹窗 ============ -->
    <el-dialog v-model="labelDialogVisible" title="会话标注" width="500">
      <el-form label-width="80">
        <el-form-item label="类型">
          <el-radio-group v-model="labelType">
            <el-radio value="positive">正面</el-radio>
            <el-radio value="negative">负面</el-radio>
            <el-radio value="neutral">中性</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="labelContent" type="textarea" :rows="3" placeholder="标注内容（可选）" />
        </el-form-item>
        <el-form-item label="历史" v-if="labels.length">
          <div class="history-labels">
            <el-tag
              v-for="label in labels"
              :key="label.id"
              :type="labelTagType(label.labelType)"
              size="small"
            >
              {{ label.labelType }}：{{ label.labelContent || '-' }}
            </el-tag>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="labelDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitLabel">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.customer-service-view {
  padding: 0;
}

.cs-tabs {
  --el-tabs-header-height: 48px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.card-header .filter {
  margin-left: auto;
}

.chat-result {
  margin-top: 16px;
}

.meta {
  margin-left: 12px;
  color: var(--text-secondary, #6b7280);
  font-size: 13px;
}

.kb-answer {
  margin-top: 8px;
  padding: 12px;
  background: var(--bg-subtle, #f9fafb);
  border-radius: 6px;
  white-space: pre-wrap;
}

.slot-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 4px 0;
}

.missing {
  color: var(--color-warning, #f59e0b);
  font-size: 13px;
  margin: 4px 0;
}

.empty {
  color: var(--text-tertiary, #9ca3af);
  font-size: 13px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.stats-table {
  margin-top: 20px;
}

.history-labels {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
