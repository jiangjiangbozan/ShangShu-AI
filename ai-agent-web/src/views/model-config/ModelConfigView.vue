<script setup lang="ts">
/**
 * 模型配置管理页
 *
 * 表格展示所有配置，支持新增、编辑、删除、测试连接。
 */
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElIcon, ElTag, ElPopconfirm, ElMessage } from 'element-plus'
import { Plus, Edit, Delete, Connection } from '@element-plus/icons-vue'
import {
  listConfigs,
  createConfig,
  updateConfig,
  deleteConfig,
  testConnection,
  type ModelConfigResp,
  type ModelConfigReq,
} from '@/api/model-config'
import ModelConfigDialog from '@/components/model-config/ModelConfigDialog.vue'

/** 配置列表 */
const configs = ref<ModelConfigResp[]>([])
/** 加载中 */
const loading = ref(false)
/** 对话框可见 */
const dialogVisible = ref(false)
/** 编辑中的配置（null = 新增） */
const editingConfig = ref<ModelConfigResp | null>(null)
/** 测试加载中 */
const testingId = ref<number | null>(null)

/** 加载配置列表 */
async function fetchConfigs() {
  loading.value = true
  try {
    configs.value = await listConfigs()
  } finally {
    loading.value = false
  }
}

/** 新增 */
function handleAdd() {
  editingConfig.value = null
  dialogVisible.value = true
}

/** 编辑 */
function handleEdit(config: ModelConfigResp) {
  editingConfig.value = config
  dialogVisible.value = true
}

/** 提交表单 */
async function handleSubmit(req: ModelConfigReq) {
  if (editingConfig.value) {
    await updateConfig(editingConfig.value.id, req)
    ElMessage.success('配置已更新')
  } else {
    await createConfig(req)
    ElMessage.success('配置已创建')
  }
  dialogVisible.value = false
  await fetchConfigs()
}

/** 删除 */
async function handleDelete(id: number) {
  await deleteConfig(id)
  ElMessage.success('配置已删除')
  await fetchConfigs()
}

/** 测试连接 */
async function handleTest(id: number) {
  testingId.value = id
  try {
    const result = await testConnection(id)
    if (result.success) {
      ElMessage.success(`连接成功！延迟 ${result.latencyMs}ms`)
    } else {
      ElMessage.error(`连接失败: ${result.message}`)
    }
  } catch {
    ElMessage.error('连接测试失败')
  } finally {
    testingId.value = null
  }
}

onMounted(() => fetchConfigs())
</script>

<template>
  <div class="model-config-view">
    <!-- 头部 -->
    <div class="view-header">
      <div>
        <h2>模型配置管理</h2>
        <p class="text-secondary">管理大模型 API 配置，支持动态加载与连接测试</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增配置</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="configs" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="provider" label="供应商" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.provider }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="modelId" label="模型 ID" min-width="120" />
      <el-table-column prop="displayName" label="展示名称" min-width="120" />
      <el-table-column prop="apiKeyMasked" label="API Key" width="140">
        <template #default="{ row }">
          <code>{{ row.apiKeyMasked || '未配置' }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="baseUrl" label="Base URL" min-width="180" show-overflow-tooltip />
      <el-table-column prop="enabled" label="状态" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
            {{ row.enabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="scope">
          <el-button text size="small" :icon="Edit" @click="handleEdit(scope.row as ModelConfigResp)">编辑</el-button>
          <el-button
            text
            size="small"
            :icon="Connection"
            :loading="testingId === scope.row.id"
            @click="handleTest(scope.row.id)"
          >
            测试
          </el-button>
          <el-popconfirm
            title="确定删除此配置？"
            @confirm="handleDelete(scope.row.id)"
          >
            <template #reference>
              <el-button text size="small" type="danger" :icon="Delete">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑对话框 -->
    <ModelConfigDialog
      v-model:visible="dialogVisible"
      :config="editingConfig"
      @submit="handleSubmit"
    />
  </div>
</template>

<style scoped>
.model-config-view {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.view-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.view-header h2 {
  margin: 0 0 var(--space-1);
  font-size: 20px;
  font-weight: var(--font-weight-semibold);
}
.text-secondary {
  margin: 0;
  font-size: 13px;
  color: var(--text-tertiary);
}
</style>