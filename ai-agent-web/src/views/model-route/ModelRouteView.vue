<script setup lang="ts">
/**
 * 模型路由 / 降级策略配置页（A5）
 *
 * 为每个主模型配置降级模型链（按 priority 降级优先级）。执行侧已接入对话 SSE 流：
 * 主模型不可用时按配置自动路由到第一个可用降级模型。
 */
import { onMounted, ref } from 'vue'
import {
  ElCard, ElButton, ElTable, ElTableColumn, ElTag, ElInput, ElInputNumber,
  ElForm, ElFormItem, ElSelect, ElOption, ElDialog, ElMessage, ElMessageBox,
  ElPagination,
} from 'element-plus'
import { Plus, Search, Edit, Delete } from '@element-plus/icons-vue'
import {
  listModelRoutes, createModelRoute, updateModelRoute, deleteModelRoute,
  type ModelRoutePolicyResp, type ModelRoutePolicyReq,
} from '@/api/model-route'

const loading = ref(false)
const list = ref<ModelRoutePolicyResp[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

const dialogVisible = ref(false)
const dialogTitle = ref('新增策略')
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = ref<ModelRoutePolicyReq>({
  modelId: '',
  fallbackModelId: '',
  priority: 10,
  status: 'active',
  remark: '',
})

async function load() {
  loading.value = true
  try {
    const res = await listModelRoutes({
      modelId: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
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
  dialogTitle.value = '新增策略'
  form.value = { modelId: '', fallbackModelId: '', priority: 10, status: 'active', remark: '' }
  dialogVisible.value = true
}

function openEdit(row: ModelRoutePolicyResp) {
  editingId.value = row.id
  dialogTitle.value = `编辑策略 #${row.id}`
  form.value = {
    modelId: row.modelId,
    fallbackModelId: row.fallbackModelId,
    priority: row.priority,
    status: row.status,
    remark: row.remark ?? '',
  }
  dialogVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    if (editingId.value != null) {
      await updateModelRoute(editingId.value, form.value)
    } else {
      await createModelRoute(form.value)
    }
    ElMessage.success(editingId.value != null ? '策略已更新' : '策略已创建')
    dialogVisible.value = false
    await load()
  } catch {
    /* toast */
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: ModelRoutePolicyResp) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.modelId} → ${row.fallbackModelId}」降级策略？`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deleteModelRoute(row.id)
  ElMessage.success('策略已删除')
  await load()
}

async function toggleStatus(row: ModelRoutePolicyResp) {
  await updateModelRoute(row.id, {
    modelId: row.modelId,
    fallbackModelId: row.fallbackModelId,
    priority: row.priority,
    status: row.status === 'active' ? 'disabled' : 'active',
    remark: row.remark ?? '',
  })
  ElMessage.success(row.status === 'active' ? '已停用' : '已启用')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="model-route-view">
    <el-card shadow="never">
      <div class="page-head">
        <div class="head-left">
          <h2 class="page-title">模型路由 / 降级策略</h2>
          <p class="page-desc">
            为主模型配置降级模型链：对话请求时若主模型不可用，将按优先级自动路由到第一个可用的降级模型。
          </p>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增策略</el-button>
      </div>

      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="主模型 ID 搜索"
          clearable
          style="width: 220px"
          @keyup.enter="load"
          @clear="load"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button :icon="Search" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column label="主模型" min-width="180">
          <template #default="{ row }">
            <el-tag type="primary">{{ row.modelId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="降级模型" min-width="180">
          <template #default="{ row }">
            <el-tag type="warning">{{ row.fallbackModelId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="100">
          <template #default="{ row }">
            <el-tag :type="row.priority <= 1 ? 'danger' : 'info'" size="small">
              {{ row.priority }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 'active'" @change="toggleStatus(row as ModelRoutePolicyResp)" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" :icon="Edit" @click="openEdit(row as ModelRoutePolicyResp)">编辑</el-button>
            <el-button text type="danger" :icon="Delete" @click="handleDelete(row as ModelRoutePolicyResp)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="load"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" :close-on-click-modal="false">
      <el-form :model="form" label-width="120px">
        <el-form-item label="主模型 ID" required>
          <el-input v-model="form.modelId" placeholder="如 deepseek-v3 / qwen-plus" :disabled="editingId != null" />
        </el-form-item>
        <el-form-item label="降级模型 ID" required>
          <el-input v-model="form.fallbackModelId" placeholder="如 deepseek-chat / qwen-turbo" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" :max="100" style="width: 100%" />
          <div class="form-tip">数字越小优先级越高（1 最高）。同一主模型的多个降级模型按此顺序尝试。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
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
.form-tip {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.5;
  margin-top: 4px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
