<script setup lang="ts">
/**
 * 内容审查 Moderation 配置页（A4）
 *
 * 配置 openai moderation API 或本地敏感词两种审查方式，审查在对话消息发送前生效；
 * 支持对配置做实时测试（测试按钮）与启停切换。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElCard, ElButton, ElTable, ElTableColumn, ElTag, ElInput, ElSelect, ElOption,
  ElDialog, ElMessage, ElMessageBox, ElPagination, ElForm, ElFormItem, ElSwitch,
  ElInputNumber, ElAlert,
} from 'element-plus'
import { Search, Plus, Refresh, Check } from '@element-plus/icons-vue'
import {
  listModerationConfigs, createModerationConfig, updateModerationConfig,
  deleteModerationConfig, testModeration,
  type ModerationConfig,
} from '@/api/moderation'

const PROVIDER_LABELS: Record<string, string> = {
  openai: 'OpenAI Moderation API',
  keyword: '本地敏感词',
  disabled: '停用审查',
}

const loading = ref(false)
const list = ref<ModerationConfig[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()
const form = reactive<ModerationConfig>({
  name: '',
  provider: 'keyword',
  apiKey: '',
  apiUrl: '',
  threshold: 0.5,
  keywords: '',
  enabled: true,
  remark: '',
})

const testVisible = ref(false)
const testProvider = ref('keyword')
const testText = ref('')
const testApiKey = ref('')
const testApiUrl = ref('')
const testThreshold = ref('0.5')
const testKeywords = ref('')
const testResult = ref<{ flagged: boolean; reason: string | null } | null>(null)
const testing = ref(false)

const providerLabel = computed(() => PROVIDER_LABELS[form.provider] ?? form.provider)

function providerTag(p: string) {
  if (p === 'disabled') return 'info'
  if (p === 'openai') return 'primary'
  return 'warning'
}

async function load() {
  loading.value = true
  try {
    const res = await listModerationConfigs({ name: keyword.value || undefined, page: page.value, size: size.value })
    list.value = res.list
    total.value = res.total
  } catch {
    /* toast */
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', provider: 'keyword', apiKey: '', apiUrl: '', threshold: 0.5, keywords: '', enabled: true, remark: '' })
  dialogVisible.value = true
}

function openEdit(row: ModerationConfig) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    name: row.name,
    provider: row.provider,
    apiKey: row.apiKey ?? '',
    apiUrl: row.apiUrl ?? '',
    threshold: row.threshold ?? 0.5,
    keywords: row.keywords ?? '',
    enabled: row.enabled,
    remark: row.remark ?? '',
  })
  dialogVisible.value = true
}

async function handleSave() {
  const payload: ModerationConfig = {
    name: form.name.trim(),
    provider: form.provider,
    apiKey: form.apiKey || undefined,
    apiUrl: form.apiUrl || undefined,
    threshold: Number(form.threshold ?? 0.5),
    keywords: form.keywords || undefined,
    enabled: form.enabled,
    remark: form.remark || undefined,
  }
  if (editingId.value != null) {
    await updateModerationConfig(editingId.value, payload)
    ElMessage.success('配置已更新')
  } else {
    await createModerationConfig(payload)
    ElMessage.success('配置已创建')
  }
  dialogVisible.value = false
  await load()
}

async function handleDelete(row: ModerationConfig) {
  await ElMessageBox.confirm(`确认删除审查配置「${row.name}」？`, '删除确认', { type: 'warning' })
  await deleteModerationConfig(row.id!)
  ElMessage.success('已删除')
  await load()
}

async function handleToggle(row: ModerationConfig) {
  await updateModerationConfig(row.id!, {
    name: row.name,
    provider: row.provider,
    apiKey: row.apiKey ?? undefined,
    apiUrl: row.apiUrl ?? undefined,
    threshold: row.threshold ?? 0.5,
    keywords: row.keywords ?? undefined,
    enabled: row.enabled,
    remark: row.remark ?? undefined,
  })
  ElMessage.success(row.enabled ? '已启用' : '已停用')
}

async function handleTest() {
  if (!testText.value.trim()) {
    ElMessage.warning('请输入测试文本')
    return
  }
  testing.value = true
  try {
    testResult.value = await testModeration({
      provider: testProvider.value,
      apiKey: testApiKey.value || undefined,
      apiUrl: testApiUrl.value || undefined,
      threshold: testThreshold.value || '0.5',
      keywords: testKeywords.value || undefined,
      text: testText.value,
    })
  } finally {
    testing.value = false
  }
}

function openTest() {
  testText.value = ''
  testResult.value = null
  testProvider.value = 'keyword'
  testApiKey.value = ''
  testApiUrl.value = ''
  testThreshold.value = '0.5'
  testKeywords.value = ''
  testVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="moderation-view">
    <el-card shadow="never">
      <div class="page-head">
        <div class="head-left">
          <h2 class="page-title">内容审查</h2>
          <p class="page-desc">
            对话消息发送前执行内容审查，命中敏感内容时拒绝该次请求。支持 OpenAI Moderation API 与本地敏感词两种方式；
            多条配置时取第一条生效配置。
          </p>
        </div>
        <div>
          <el-button :icon="Check" @click="openTest">测试审查</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增配置</el-button>
        </div>
      </div>

      <div class="toolbar">
        <el-input v-model="keyword" placeholder="配置名称" clearable style="width: 200px" @keyup.enter="load" @clear="load" />
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        <el-button :icon="Refresh" @click="() => { keyword = ''; load() }">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column label="方式" width="180">
          <template #default="{ row }">
            <el-tag :type="providerTag(row.provider)" size="small">{{ PROVIDER_LABELS[row.provider] ?? row.provider }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="阈值" width="80">
          <template #default="{ row }">
            <span v-if="row.provider === 'openai'">{{ row.threshold }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="敏感词" min-width="160">
          <template #default="{ row }">
            <span v-if="row.keywords" class="mono">{{ row.keywords }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="生效" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="handleToggle(row as ModerationConfig)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row as ModerationConfig)">编辑</el-button>
            <el-button text type="danger" @click="handleDelete(row as ModerationConfig)">删除</el-button>
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

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新增审查配置' : '编辑审查配置'" width="560px">
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：默认敏感词审查" />
        </el-form-item>
        <el-form-item label="审查方式" required>
          <el-select v-model="form.provider" style="width: 100%">
            <el-option label="本地敏感词" value="keyword" />
            <el-option label="OpenAI Moderation API" value="openai" />
            <el-option label="停用审查" value="disabled" />
          </el-select>
        </el-form-item>
        <template v-if="form.provider === 'openai'">
          <el-alert type="info" :closable="false" title="将调用 OpenAI 内容审查接口，任一类别分数超过阈值即判定为敏感。" class="form-tip" />
          <el-form-item label="API Key">
            <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-..." />
          </el-form-item>
          <el-form-item label="API 地址">
            <el-input v-model="form.apiUrl" placeholder="默认 https://api.openai.com/v1/moderations" />
          </el-form-item>
          <el-form-item label="阈值 (0-1)">
            <el-input-number v-model="form.threshold" :min="0" :max="1" :step="0.05" :precision="2" />
          </el-form-item>
        </template>
        <template v-else-if="form.provider === 'keyword'">
          <el-form-item label="敏感词">
            <el-input v-model="form.keywords" type="textarea" :rows="3" placeholder="逗号分隔，如：暴力,赌博,色情" />
          </el-form-item>
        </template>
        <el-form-item label="生效">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="备注说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 测试审查 -->
    <el-dialog v-model="testVisible" title="测试内容审查" width="540px">
      <el-form label-width="90px">
        <el-form-item label="审查方式">
          <el-select v-model="testProvider" style="width: 220px">
            <el-option label="本地敏感词" value="keyword" />
            <el-option label="OpenAI Moderation API" value="openai" />
          </el-select>
        </el-form-item>
        <template v-if="testProvider === 'openai'">
          <el-form-item label="API Key"><el-input v-model="testApiKey" type="password" show-password placeholder="sk-..." /></el-form-item>
          <el-form-item label="API 地址"><el-input v-model="testApiUrl" placeholder="默认 https://api.openai.com/v1/moderations" /></el-form-item>
          <el-form-item label="阈值"><el-input v-model="testThreshold" placeholder="0.5" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="敏感词"><el-input v-model="testKeywords" placeholder="逗号分隔" /></el-form-item>
        </template>
        <el-form-item label="测试文本" required>
          <el-input v-model="testText" type="textarea" :rows="3" placeholder="输入要审查的文本" />
        </el-form-item>
        <el-form-item v-if="testResult" label="结果">
          <el-tag :type="testResult.flagged ? 'danger' : 'success'" size="large">
            {{ testResult.flagged ? '命中（拒绝）' : '通过（放行）' }}
          </el-tag>
          <span v-if="testResult.reason" class="test-reason">{{ testResult.reason }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testing" @click="handleTest">执行测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.page-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-tertiary);
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.mono {
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  font-size: 12px;
}
.form-tip {
  margin-bottom: 12px;
}
.test-reason {
  margin-left: 8px;
  font-size: 12px;
  color: #f56c6c;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
