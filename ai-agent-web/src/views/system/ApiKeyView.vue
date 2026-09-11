<script setup lang="ts">
/**
 * API 密钥管理页面（参考 Dify API Key 管理）
 *
 * 功能：
 * 1. 密钥列表（名称、前缀、创建时间、最后使用时间、启用状态）
 * 2. 创建密钥（仅创建时显示明文，之后不可恢复）
 * 3. 启用/禁用密钥
 * 4. 删除密钥
 */
import { onMounted, ref } from 'vue'
import {
  ElCard, ElButton, ElTable, ElTableColumn, ElTag, ElInput,
  ElDialog, ElMessage, ElMessageBox, ElEmpty, ElIcon, ElTooltip, ElPagination,
} from 'element-plus'
import { Plus, Key, Delete, CopyDocument, Warning } from '@element-plus/icons-vue'
import {
  listApiKeys, createApiKey, toggleApiKey, deleteApiKey,
  type ApiKeyResp,
} from '@/api/api-key'

const keys = ref<ApiKeyResp[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)

/** 创建对话框 */
const showCreate = ref(false)
const createName = ref('')
const creating = ref(false)
/** 创建成功后展示明文密钥 */
const createdKey = ref<ApiKeyResp | null>(null)
const showCreatedDialog = ref(false)

async function fetchKeys() {
  loading.value = true
  try {
    const res = await listApiKeys(page.value, size.value)
    keys.value = res.list ?? []
    total.value = res.total ?? 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function handleCreate() {
  const name = createName.value.trim()
  if (!name) { ElMessage.warning('请输入密钥名称'); return }
  creating.value = true
  try {
    const apiKey = await createApiKey(name)
    createdKey.value = apiKey
    showCreate.value = false
    showCreatedDialog.value = true
    createName.value = ''
    fetchKeys()
  } catch { /* ignore */ }
  finally { creating.value = false }
}

async function handleToggle(row: ApiKeyResp) {
  const enabled = row.enabled !== 1
  try {
    await toggleApiKey(row.id, enabled)
    ElMessage.success(enabled ? '已启用' : '已禁用')
    fetchKeys()
  } catch { /* ignore */ }
}

async function handleDelete(row: ApiKeyResp) {
  try {
    await ElMessageBox.confirm(
      `确定删除密钥「${row.name}」（${row.keyPrefix}...）？删除后使用该密钥的请求将被拒绝。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await deleteApiKey(row.id)
    ElMessage.success('已删除')
    fetchKeys()
  } catch { /* cancel */ }
}

async function copyKey(key: string) {
  try {
    await navigator.clipboard.writeText(key)
    ElMessage.success('已复制到剪贴板')
  } catch { ElMessage.error('复制失败') }
}

onMounted(fetchKeys)
</script>

<template>
  <div class="api-key-page">
    <div class="page-header">
      <div>
        <h2 style="margin: 0; font-size: 20px;">API 密钥管理</h2>
        <p style="margin: 4px 0 0; color: var(--text-secondary); font-size: 13px;">
          管理平台 API 访问密钥，用于外部应用调用 OpenAPI 接口
        </p>
      </div>
      <el-button type="primary" :icon="Plus" @click="showCreate = true">创建密钥</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="keys" v-loading="loading" stripe border style="width: 100%;">
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="keyPrefix" label="密钥前缀" width="160">
          <template #default="{ row }">
            <code class="key-prefix">{{ row.keyPrefix }}•••••••</code>
          </template>
        </el-table-column>
        <el-table-column prop="lastUsedAt" label="最后使用" width="180">
          <template #default="{ row }">{{ row.lastUsedAt || '从未使用' }}</template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button text size="small" @click="handleToggle(row as ApiKeyResp)">
              {{ row.enabled === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button text size="small" type="danger" :icon="Delete" @click="handleDelete(row as ApiKeyResp)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; display: flex; justify-content: flex-end;">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { page = p; fetchKeys() }"
        />
      </div>
    </el-card>

    <!-- 创建密钥对话框 -->
    <el-dialog v-model="showCreate" title="创建 API 密钥" width="460px" destroy-on-close>
      <el-input v-model="createName" placeholder="输入密钥名称，如：生产环境、测试环境" maxlength="50" show-word-limit />
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 创建成功展示明文密钥 -->
    <el-dialog v-model="showCreatedDialog" title="密钥创建成功" width="540px" :close-on-click-modal="false" :show-close="true">
      <div class="created-key-notice">
        <el-icon style="color: #e6a23c; font-size: 20px;"><Warning /></el-icon>
        <span>请立即复制并妥善保管此密钥，关闭后将无法再次查看！</span>
      </div>
      <div class="created-key-info" v-if="createdKey">
        <div class="created-key-row">
          <span class="created-key-label">名称：</span>
          <span>{{ createdKey.name }}</span>
        </div>
        <div class="created-key-row">
          <span class="created-key-label">密钥：</span>
          <code class="created-key-value">{{ createdKey.keyPlain }}</code>
          <el-button text size="small" :icon="CopyDocument" @click="copyKey(createdKey.keyPlain || '')">复制</el-button>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="showCreatedDialog = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default { name: 'ApiKeyView' }
</script>

<style scoped>
.api-key-page { padding: 4px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.key-prefix {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  background: var(--bg-subtle, #f5f7fa);
  padding: 2px 8px;
  border-radius: 4px;
  color: var(--text-secondary);
}
.created-key-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 8px;
  color: #e6a23c;
  font-size: 14px;
  margin-bottom: 16px;
}
.created-key-info {
  padding: 12px 0;
}
.created-key-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.created-key-label {
  font-size: 13px;
  color: var(--text-secondary);
  flex-shrink: 0;
}
.created-key-value {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  background: var(--bg-subtle, #f5f7fa);
  padding: 4px 12px;
  border-radius: 4px;
  flex: 1;
  word-break: break-all;
}
</style>
