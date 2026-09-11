<script setup lang="ts">
/**
 * 标注问答管理页面（借鉴 Dify Annotation Reply）
 *
 * 功能：
 * 1. 标注 Q&A 对列表（问题、回答、命中次数、状态、时间）
 * 2. 新增/编辑/删除标注
 * 3. 启用/禁用标注
 * 4. 关键词搜索
 * 5. 查看命中历史
 */
import { onMounted, ref } from 'vue'
import {
  ElCard, ElButton, ElTable, ElTableColumn, ElTag, ElInput,
  ElDialog, ElMessage, ElMessageBox, ElEmpty, ElPagination,
  ElSwitch, ElTooltip,
} from 'element-plus'
import { Plus, Search, Delete, Edit, View } from '@element-plus/icons-vue'
import {
  listAnnotations, createAnnotation, updateAnnotation,
  deleteAnnotation, toggleAnnotation, type AnnotationResp,
} from '@/api/annotation'

const list = ref<AnnotationResp[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

/** 新增/编辑对话框 */
const editVisible = ref(false)
const editMode = ref<'add' | 'edit'>('add')
const editId = ref<number>(0)
const editQuestion = ref('')
const editAnswer = ref('')
const editSaving = ref(false)

/** 查看详情对话框 */
const detailVisible = ref(false)
const detailItem = ref<AnnotationResp | null>(null)

async function fetchList() {
  loading.value = true
  try {
    const res = await listAnnotations(keyword.value || undefined, page.value, size.value)
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function handleSearch() {
  page.value = 1
  fetchList()
}

function openAdd() {
  editMode.value = 'add'
  editId.value = 0
  editQuestion.value = ''
  editAnswer.value = ''
  editVisible.value = true
}

function openEdit(row: AnnotationResp) {
  editMode.value = 'edit'
  editId.value = row.id
  editQuestion.value = row.question
  editAnswer.value = row.answer
  editVisible.value = true
}

async function handleSave() {
  const q = editQuestion.value.trim()
  const a = editAnswer.value.trim()
  if (!q) { ElMessage.warning('请输入问题'); return }
  if (!a) { ElMessage.warning('请输入回答'); return }
  editSaving.value = true
  try {
    if (editMode.value === 'add') {
      await createAnnotation(q, a)
      ElMessage.success('标注已创建')
    } else {
      await updateAnnotation(editId.value, q, a)
      ElMessage.success('标注已更新')
    }
    editVisible.value = false
    fetchList()
  } catch { /* ignore */ }
  finally { editSaving.value = false }
}

async function handleToggle(row: AnnotationResp) {
  const enabled = row.enabled !== 1
  try {
    await toggleAnnotation(row.id, enabled)
    ElMessage.success(enabled ? '已启用' : '已禁用')
    fetchList()
  } catch { /* ignore */ }
}

async function handleDelete(row: AnnotationResp) {
  try {
    await ElMessageBox.confirm(
      `确定删除此标注？问题：「${row.question.substring(0, 30)}${row.question.length > 30 ? '…' : ''}」`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await deleteAnnotation(row.id)
    ElMessage.success('已删除')
    fetchList()
  } catch { /* cancel */ }
}

function showDetail(row: AnnotationResp) {
  detailItem.value = row
  detailVisible.value = true
}

function truncate(text: string, max: number) {
  return text.length > max ? text.substring(0, max) + '…' : text
}

onMounted(fetchList)
</script>

<template>
  <div class="annotation-page">
    <div class="page-header">
      <div>
        <h2 style="margin: 0; font-size: 20px;">标注问答管理</h2>
        <p style="margin: 4px 0 0; color: var(--text-secondary); font-size: 13px;">
          管理 Q&A 标注对，系统在对话中命中相似问题时可直接返回标注答案，减少 LLM 调用并提升响应速度
        </p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增标注</el-button>
    </div>

    <el-card shadow="never">
      <!-- 搜索栏 -->
      <div style="display: flex; gap: 8px; margin-bottom: 16px;">
        <el-input
          v-model="keyword"
          placeholder="搜索问题或回答内容..."
          clearable
          style="max-width: 360px;"
          @keyup.enter="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>

      <el-table :data="list" v-loading="loading" stripe border style="width: 100%;">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="问题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.question }}</template>
        </el-table-column>
        <el-table-column label="回答" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ truncate(row.answer, 80) }}</template>
        </el-table-column>
        <el-table-column prop="hitCount" label="命中次数" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.hitCount > 0 ? 'success' : 'info'" size="small">{{ row.hitCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              size="small"
              @change="handleToggle(row as AnnotationResp)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-tooltip content="查看详情" placement="top">
              <el-button link size="small" :icon="View" @click="showDetail(row as AnnotationResp)" />
            </el-tooltip>
            <el-tooltip content="编辑" placement="top">
              <el-button link size="small" :icon="Edit" @click="openEdit(row as AnnotationResp)" />
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button link size="small" type="danger" :icon="Delete" @click="handleDelete(row as AnnotationResp)" />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; display: flex; justify-content: flex-end;">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { page = p; fetchList() }"
        />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="editVisible"
      :title="editMode === 'add' ? '新增标注' : '编辑标注'"
      width="640px"
      destroy-on-close
    >
      <div style="display: flex; flex-direction: column; gap: 16px;">
        <div>
          <div style="font-weight: 500; margin-bottom: 6px;">问题 / 提问</div>
          <el-input
            v-model="editQuestion"
            type="textarea"
            :rows="4"
            placeholder="输入用户可能提出的问题..."
            maxlength="2000"
            show-word-limit
          />
        </div>
        <div>
          <div style="font-weight: 500; margin-bottom: 6px;">回答 / 答案</div>
          <el-input
            v-model="editAnswer"
            type="textarea"
            :rows="6"
            placeholder="输入期望的回答内容..."
            maxlength="5000"
            show-word-limit
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog v-model="detailVisible" title="标注详情" width="640px" destroy-on-close>
      <div v-if="detailItem" style="display: flex; flex-direction: column; gap: 16px;">
        <div>
          <div style="font-weight: 500; margin-bottom: 6px; color: var(--el-color-primary);">问题</div>
          <div style="padding: 12px; background: var(--bg-subtle, #f5f7fa); border-radius: 8px; white-space: pre-wrap; word-break: break-word;">{{ detailItem.question }}</div>
        </div>
        <div>
          <div style="font-weight: 500; margin-bottom: 6px; color: var(--el-color-success);">回答</div>
          <div style="padding: 12px; background: var(--bg-subtle, #f5f7fa); border-radius: 8px; white-space: pre-wrap; word-break: break-word;">{{ detailItem.answer }}</div>
        </div>
        <div style="display: flex; gap: 24px; color: var(--text-secondary); font-size: 13px;">
          <span>命中次数：{{ detailItem.hitCount }}</span>
          <span>状态：{{ detailItem.enabled === 1 ? '启用' : '禁用' }}</span>
          <span>创建时间：{{ detailItem.createTime }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default { name: 'AnnotationView' }
</script>

<style scoped>
.annotation-page { padding: 4px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
</style>
