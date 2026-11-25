<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="title">AI 应用列表</span>
        <el-button type="primary" @click="openCreate">新建应用</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" effect="plain">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="发布状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.apiKeyHash ? 'success' : 'info'" effect="light">
              {{ row.apiKeyHash ? '已发布' : '未发布' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="isEnabled(row)" @change="(v: boolean) => onToggle(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text type="success" :disabled="!!row.apiKeyHash" @click="onPublish(row)">
              发布
            </el-button>
            <el-button
              text
              type="warning"
              :disabled="!row.apiKeyHash"
              @click="router.push({ path: '/app-playground', query: { appId: row.id } })"
            >
              调试
            </el-button>
            <el-button text type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPage"
      />
    </el-card>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dlgVisible" :title="editingId ? '编辑应用' : '新建应用'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="应用名称" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="Chat 对话" value="chat" />
            <el-option label="RAG 知识问答" value="rag" />
            <el-option label="Agent 任务执行" value="agent" />
            <el-option label="Workflow 流程编排" value="workflow" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="应用描述" />
        </el-form-item>
        <el-form-item label="配置 JSON">
          <el-input
            v-model="form.configJson"
            type="textarea"
            :rows="6"
            placeholder='如 {"kbId":1,"topK":5} / {"agentConfigId":1} / {"workflowId":"..."}'
          />
          <div class="hint">{{ configHint }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发布结果（API Key 仅展示一次） -->
    <el-dialog v-model="keyDlgVisible" title="发布成功" width="520px">
      <el-alert type="warning" :closable="false" show-icon title="API Key 仅本次展示，请立即复制保存" />
      <el-input class="key-input" :model-value="publishedKey" readonly>
        <template #append>
          <el-button @click="copyKey">复制</el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button @click="keyDlgVisible = false">关闭</el-button>
        <el-button
          type="primary"
          @click="router.push(`/app-playground?key=${encodeURIComponent(publishedKey)}`)"
        >
          去调试
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  createApp,
  deleteApp,
  listApps,
  publishApp,
  updateApp,
  type AiApp,
  type AppPayload,
  type AppType,
} from '@/api/apps'

const router = useRouter()

const loading = ref(false)
const rows = ref<AiApp[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const dlgVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = ref<AppPayload>({ name: '', type: 'chat', configJson: '{}', description: '' })

const keyDlgVisible = ref(false)
const publishedKey = ref('')

const TYPE_LABELS: Record<AppType, string> = {
  chat: 'Chat 对话',
  rag: 'RAG 问答',
  agent: 'Agent',
  workflow: 'Workflow',
}

const typeLabel = (t: string) => TYPE_LABELS[t as AppType] ?? t
const typeTag = (t: string) => ({ chat: 'primary', rag: 'success', agent: 'warning', workflow: 'danger' }[t as AppType] ?? 'info')

const isEnabled = (row: AiApp) => row.enabled === 1 || row.enabled === true

const configHint = computed(
  () =>
    ({
      chat: 'Chat 类型无需额外配置',
      rag: 'RAG 类型：{"kbId": 知识库ID, "topK": 5}',
      agent: 'Agent 类型：{"agentConfigId": Agent配置ID}',
      workflow: 'Workflow 类型：{"workflowId": "工作流ID", "inputs": {}}',
    })[form.value.type] ?? '',
)

async function load() {
  loading.value = true
  try {
    const data = await listApps(page.value, size.value)
    rows.value = data.records ?? []
    total.value = data.total ?? 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { name: '', type: 'chat', configJson: '{}', description: '' }
  dlgVisible.value = true
}

function openEdit(row: AiApp) {
  editingId.value = row.id
  form.value = {
    name: row.name,
    type: row.type,
    configJson: row.configJson ?? '{}',
    description: row.description ?? '',
  }
  dlgVisible.value = true
}

function validateConfig(): boolean {
  try {
    const parsed = JSON.parse(form.value.configJson || '{}')
    return typeof parsed === 'object' && parsed !== null
  } catch {
    ElMessage.error('配置 JSON 格式不合法')
    return false
  }
}

async function save() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写应用名称')
    return
  }
  if (!validateConfig()) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateApp(editingId.value, form.value)
      ElMessage.success('已更新')
    } else {
      await createApp(form.value)
      ElMessage.success('已创建')
    }
    dlgVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onToggle(row: AiApp, value: boolean) {
  await updateApp(row.id, {
    name: row.name,
    type: row.type,
    configJson: row.configJson ?? '{}',
    description: row.description ?? '',
  })
  row.enabled = value ? 1 : 0
}

async function onPublish(row: AiApp) {
  await ElMessageBox.confirm(`确认发布应用「${row.name}」？发布后将生成新的 API Key。`, '发布确认', {
    type: 'warning',
  })
  const result = await publishApp(row.id)
  publishedKey.value = result.apiKey
  keyDlgVisible.value = true
  await load()
}

async function onDelete(row: AiApp) {
  await ElMessageBox.confirm(`确认删除应用「${row.name}」？`, '删除确认', { type: 'warning' })
  await deleteApp(row.id)
  ElMessage.success('已删除')
  await load()
}

function onPage(p: number) {
  page.value = p
  void load()
}

function copyKey() {
  void navigator.clipboard.writeText(publishedKey.value)
  ElMessage.success('已复制')
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.key-input {
  margin-top: 12px;
}

.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin-top: 4px;
}
</style>
