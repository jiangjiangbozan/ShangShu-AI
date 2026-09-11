<script setup lang="ts">
/**
 * Prompt 模板管理页面
 *
 * 功能：
 * 1. 模板列表：分页 + 关键词搜索 + 分类筛选
 * 2. 创建/编辑模板：名称/分类/内容/变量/描述/关联模型/启用状态
 * 3. 新建版本：基于现有模板创建新版本
 * 4. 删除模板
 */
import { onMounted, ref } from 'vue'
import {
  ElCard, ElButton, ElTable, ElTableColumn, ElTag, ElInput,
  ElForm, ElFormItem, ElSelect, ElOption, ElSwitch, ElDialog,
  ElMessage, ElMessageBox, ElEmpty, ElRow, ElCol, ElPagination,
} from 'element-plus'
import { Plus, Search, Edit, CopyDocument, Delete } from '@element-plus/icons-vue'
import {
  listPromptTemplates, createPromptTemplate, updatePromptTemplate,
  createNewVersion, deletePromptTemplate,
  type PromptTemplateResp, type PromptTemplateReq, type PromptCategory,
  CATEGORY_LABELS,
} from '@/api/prompt-template'

const loading = ref(false)
const list = ref<PromptTemplateResp[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const categoryFilter = ref<string>('')

// dialog
const dialogVisible = ref(false)
const dialogTitle = ref('新建模板')
const editingId = ref<number | null>(null)
const form = ref<PromptTemplateReq>({
  name: '', category: 'chat', content: '', variables: [], modelId: '', isActive: true, description: '',
})
const varInput = ref('')

const categories = Object.entries(CATEGORY_LABELS).map(([k, v]) => ({ value: k, label: v }))

async function load() {
  loading.value = true
  try {
    const res = await listPromptTemplates({
      keyword: keyword.value || undefined,
      category: categoryFilter.value || undefined,
      page: page.value,
      size: size.value,
    })
    list.value = res.list
    total.value = res.total
  } catch { /* toast */ } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  dialogTitle.value = '新建模板'
  form.value = { name: '', category: 'chat', content: '', variables: [], modelId: '', isActive: true, description: '' }
  varInput.value = ''
  dialogVisible.value = true
}

function openEdit(row: PromptTemplateResp) {
  editingId.value = row.id
  dialogTitle.value = '编辑模板'
  form.value = {
    name: row.name,
    category: row.category,
    content: row.content,
    variables: row.variables ? [...row.variables] : [],
    modelId: row.modelId || '',
    isActive: row.isActive,
    description: row.description || '',
  }
  varInput.value = (form.value.variables || []).join(', ')
  dialogVisible.value = true
}

function parseVariables() {
  form.value.variables = varInput.value
    .split(/[,，\s]+/)
    .map(v => v.trim())
    .filter(Boolean)
}

async function saveTemplate() {
  parseVariables()
  if (!form.value.name || !form.value.content) {
    ElMessage.warning('名称和内容不能为空')
    return
  }
  try {
    if (editingId.value) {
      await updatePromptTemplate(editingId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createPromptTemplate(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await load()
  } catch { /* toast */ }
}

async function handleNewVersion(row: PromptTemplateResp) {
  try {
    await ElMessageBox.confirm(`基于「${row.name}」v${row.version} 创建新版本？`, '新建版本', { type: 'info' })
    await createNewVersion(row.id)
    ElMessage.success('新版本已创建')
    await load()
  } catch { /* cancel */ }
}

async function handleDelete(row: PromptTemplateResp) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.name}」v${row.version}？`, '删除确认', { type: 'warning' })
    await deletePromptTemplate(row.id)
    ElMessage.success('已删除')
    await load()
  } catch { /* cancel */ }
}

function categoryTagType(cat: PromptCategory): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const m: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = { chat: 'primary', reasoning: 'success', planning: 'warning', tool_calling: 'danger', reflection: 'info' }
  return m[cat] || 'primary'
}

onMounted(load)
</script>

<template>
  <div class="prompt-template-view">
    <ElCard shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span style="font-size:16px;font-weight:600">Prompt 模板管理</span>
          <ElButton type="primary" :icon="Plus" @click="openCreate">新建模板</ElButton>
        </div>
      </template>

      <!-- 筛选 -->
      <ElRow :gutter="12" style="margin-bottom:16px">
        <ElCol :span="8">
          <ElInput v-model="keyword" placeholder="搜索模板名称/描述" :prefix-icon="Search" clearable @clear="load" @keyup.enter="load" />
        </ElCol>
        <ElCol :span="4">
          <ElSelect v-model="categoryFilter" placeholder="全部分类" clearable @change="load">
            <ElOption v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </ElSelect>
        </ElCol>
        <ElCol :span="2">
          <ElButton @click="load">查询</ElButton>
        </ElCol>
      </ElRow>

      <!-- 表格 -->
      <ElTable :data="list" v-loading="loading" stripe>
        <ElTableColumn prop="name" label="名称" min-width="160" />
        <ElTableColumn prop="category" label="分类" width="110">
          <template #default="{ row }">
            <ElTag :type="categoryTagType(row.category)" size="small">{{ CATEGORY_LABELS[row.category as PromptCategory] || row.category }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="version" label="版本" width="80" align="center" />
        <ElTableColumn prop="variables" label="变量" min-width="140">
          <template #default="{ row }">
            <ElTag v-for="v in (row.variables || [])" :key="v" size="small" style="margin:2px">{{ v }}</ElTag>
            <span v-if="!row.variables?.length" style="color:#999">—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="modelId" label="关联模型" width="120">
          <template #default="{ row }">{{ row.modelId || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn prop="isActive" label="状态" width="80" align="center">
          <template #default="{ row }">
            <ElTag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '启用' : '停用' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createdBy" label="创建人" width="90" />
        <ElTableColumn prop="updatedAt" label="更新时间" width="170" />
        <ElTableColumn label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" :icon="Edit" @click="openEdit(row as PromptTemplateResp)">编辑</ElButton>
            <ElButton link type="warning" :icon="CopyDocument" @click="handleNewVersion(row as PromptTemplateResp)">新版本</ElButton>
            <ElButton link type="danger" :icon="Delete" @click="handleDelete(row as PromptTemplateResp)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <ElPagination v-if="total > 0" style="margin-top:16px;justify-content:flex-end"
        :current-page="page" :page-size="size" :total="total"
        layout="total, prev, pager, next" @current-change="(p: number) => { page = p; load() }" />
    </ElCard>

    <!-- 新建/编辑弹窗 -->
    <ElDialog v-model="dialogVisible" :title="dialogTitle" width="680px" destroy-on-close>
      <ElForm label-width="90px">
        <ElFormItem label="名称" required>
          <ElInput v-model="form.name" placeholder="如：客服意图分类 Prompt" maxlength="128" />
        </ElFormItem>
        <ElFormItem label="分类">
          <ElSelect v-model="form.category" style="width:100%">
            <ElOption v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="模板内容" required>
          <ElInput v-model="form.content" type="textarea" :rows="8" placeholder="支持 {{variable}} 占位符" />
        </ElFormItem>
        <ElFormItem label="变量">
          <ElInput v-model="varInput" placeholder="逗号分隔，如：query, context, language" @blur="parseVariables" />
        </ElFormItem>
        <ElFormItem label="关联模型">
          <ElInput v-model="form.modelId" placeholder="可选，关联特定模型" />
        </ElFormItem>
        <ElFormItem label="描述">
          <ElInput v-model="form.description" type="textarea" :rows="2" placeholder="模板用途说明" maxlength="512" />
        </ElFormItem>
        <ElFormItem label="启用">
          <ElSwitch v-model="form.isActive" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveTemplate">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.prompt-template-view { padding: 20px; }
</style>
