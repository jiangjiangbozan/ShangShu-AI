<script setup lang="ts">
/**
 * Token 限额 / 限流配置页（A2）
 *
 * 管理 sys_token_limit_config：按 scope（USER/TENANT/PUBLIC）+ targetId（0=默认）
 * 配置每分钟/每日请求次数限制与周期 Token 限额。执行侧已接入对话 SSE 流：
 * 请求前检查限额，超限返回 error 事件拒绝；回复完成后累计 Token 用量。
 */
import { onMounted, ref } from 'vue'
import {
  ElCard, ElButton, ElTable, ElTableColumn, ElTag, ElInput, ElInputNumber,
  ElForm, ElFormItem, ElSelect, ElOption, ElSwitch, ElDialog,
  ElMessage, ElMessageBox, ElPagination, ElTooltip,
} from 'element-plus'
import { Plus, Search, Edit, Delete } from '@element-plus/icons-vue'
import {
  listTokenLimits, createTokenLimit, updateTokenLimit, deleteTokenLimit,
  type TokenLimitConfigResp, type TokenLimitCreateReq, type TokenLimitUpdateReq,
} from '@/api/limit'

const SCOPE_LABELS: Record<string, string> = {
  USER: '用户',
  TENANT: '租户',
  PUBLIC: '公开 API',
}

const PERIOD_LABELS: Record<string, string> = {
  DAY: '每日',
  MONTH: '每月',
}

const loading = ref(false)
const list = ref<TokenLimitConfigResp[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const scopeFilter = ref<string>('')

const dialogVisible = ref(false)
const dialogTitle = ref('新增配置')
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = ref<TokenLimitCreateReq>({
  scope: 'USER',
  targetId: 0,
  tokenQuota: 0,
  quotaPeriod: 'DAY',
  rateLimitPerMinute: 0,
  rateLimitPerDay: 0,
  status: 'active',
  remark: '',
})

function scopeLabel(v: string) {
  return SCOPE_LABELS[v] ?? v
}

function periodLabel(v: string) {
  return PERIOD_LABELS[v] ?? v
}

async function load() {
  loading.value = true
  try {
    const res = await listTokenLimits({
      scope: scopeFilter.value || undefined,
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
  dialogTitle.value = '新增配置'
  form.value = {
    scope: 'USER',
    targetId: 0,
    tokenQuota: 0,
    quotaPeriod: 'DAY',
    rateLimitPerMinute: 0,
    rateLimitPerDay: 0,
    status: 'active',
    remark: '',
  }
  dialogVisible.value = true
}

function openEdit(row: TokenLimitConfigResp) {
  editingId.value = row.id
  dialogTitle.value = `编辑配置 #${row.id}`
  form.value = {
    tokenQuota: row.tokenQuota,
    quotaPeriod: row.quotaPeriod,
    rateLimitPerMinute: row.rateLimitPerMinute,
    rateLimitPerDay: row.rateLimitPerDay,
    status: row.status,
    remark: row.remark ?? '',
  }
  dialogVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    if (editingId.value != null) {
      await updateTokenLimit(editingId.value, form.value as TokenLimitUpdateReq)
    } else {
      await createTokenLimit(form.value)
    }
    ElMessage.success(editingId.value != null ? '配置已更新' : '配置已创建')
    dialogVisible.value = false
    await load()
  } catch {
    /* toast */
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: TokenLimitConfigResp) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${scopeLabel(row.scope)}${row.targetId === 0 ? ' 默认' : ` #${row.targetId}`}」配置？删除后该维度将不再受限流约束。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deleteTokenLimit(row.id)
  ElMessage.success('配置已删除')
  await load()
}

/** 变更状态（启用/停用） */
async function toggleStatus(row: TokenLimitConfigResp) {
  await updateTokenLimit(row.id, {
    tokenQuota: row.tokenQuota,
    quotaPeriod: row.quotaPeriod,
    rateLimitPerMinute: row.rateLimitPerMinute,
    rateLimitPerDay: row.rateLimitPerDay,
    status: row.status === 'active' ? 'disabled' : 'active',
    remark: row.remark ?? '',
  })
  ElMessage.success(row.status === 'active' ? '已停用' : '已启用')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="token-limit-view">
    <el-card shadow="never">
      <div class="page-head">
        <div class="head-left">
          <h2 class="page-title">Token 限额 / 限流配置</h2>
          <p class="page-desc">
            按作用域 + 目标维度配置请求次数与 Token 用量限制，超限时对话请求将被拒绝。0 表示不限制。
          </p>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增配置</el-button>
      </div>

      <div class="toolbar">
        <el-select
          v-model="scopeFilter"
          placeholder="作用域筛选"
          clearable
          style="width: 160px"
          @change="load"
        >
          <el-option v-for="(label, val) in SCOPE_LABELS" :key="val" :label="label" :value="val" />
        </el-select>
        <el-button :icon="Search" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column label="作用域" width="110">
          <template #default="{ row }">
            <el-tag :type="row.scope === 'PUBLIC' ? 'danger' : row.scope === 'TENANT' ? 'warning' : 'primary'">
              {{ scopeLabel(row.scope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标 ID" width="90">
          <template #default="{ row }">
            {{ row.targetId === 0 ? '默认' : `#${row.targetId}` }}
          </template>
        </el-table-column>
        <el-table-column label="Token 限额" min-width="140">
          <template #default="{ row }">
            <span v-if="row.tokenQuota > 0">
              {{ row.tokenQuota.toLocaleString() }} <el-tag size="small" type="info">{{ periodLabel(row.quotaPeriod) }}</el-tag>
            </span>
            <span v-else class="unlimited">不限</span>
          </template>
        </el-table-column>
        <el-table-column label="每分钟次数" width="110">
          <template #default="{ row }">
            <span v-if="row.rateLimitPerMinute > 0">{{ row.rateLimitPerMinute }}</span>
            <span v-else class="unlimited">不限</span>
          </template>
        </el-table-column>
        <el-table-column label="每日次数" width="110">
          <template #default="{ row }">
            <span v-if="row.rateLimitPerDay > 0">{{ row.rateLimitPerDay }}</span>
            <span v-else class="unlimited">不限</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'active'"
              @change="toggleStatus(row as TokenLimitConfigResp)"
            />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">
            <el-tooltip :content="row.remark ?? ''" :disabled="!row.remark" placement="top">
              <span class="remark-text">{{ row.remark || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" :icon="Edit" @click="openEdit(row as TokenLimitConfigResp)">编辑</el-button>
            <el-button text type="danger" :icon="Delete" @click="handleDelete(row as TokenLimitConfigResp)">删除</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false">
      <el-form :model="form" label-width="130px">
        <template v-if="editingId == null">
          <el-form-item label="作用域">
            <el-select v-model="form.scope" style="width: 100%">
              <el-option v-for="(label, val) in SCOPE_LABELS" :key="val" :label="label" :value="val" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标 ID">
            <el-input-number v-model="form.targetId" :min="0" style="width: 100%" />
            <div class="form-tip">0 表示该作用域的默认（全局）配置；非 0 表示具体的用户/租户 ID</div>
          </el-form-item>
        </template>
        <el-form-item label="Token 限额">
          <el-input-number v-model="form.tokenQuota" :min="0" :step="100000" style="width: 100%" />
          <div class="form-tip">周期内 Token 用量上限，0 表示不限制</div>
        </el-form-item>
        <el-form-item label="限额周期">
          <el-select v-model="form.quotaPeriod" style="width: 100%">
            <el-option label="每日" value="DAY" />
            <el-option label="每月" value="MONTH" />
          </el-select>
        </el-form-item>
        <el-form-item label="每分钟请求数">
          <el-input-number v-model="form.rateLimitPerMinute" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="每日请求数">
          <el-input-number v-model="form.rateLimitPerDay" :min="0" style="width: 100%" />
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
.unlimited {
  color: var(--text-tertiary);
}
.remark-text {
  display: block;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
