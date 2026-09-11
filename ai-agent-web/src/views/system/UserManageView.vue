<script setup lang="ts">
/**
 * 系统管理 - 用户管理
 *
 * 展示系统用户，支持关键字搜索、代建用户、编辑、启停用、删除与分配角色。
 * 依据 add-rbac-management 的 UserAdminController（/api/v1/users）。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ElCard, ElInput, ElButton, ElTable, ElTableColumn, ElSwitch, ElTag,
  ElDialog, ElForm, ElFormItem, ElSelect, ElOption, ElPagination,
} from 'element-plus'
import { Search, Plus, Connection, EditPen, Delete } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import {
  listUsers, createUser, updateUser, deleteAdminUser, assignUserRoles, userRoles,
  listRoles, type AdminUserItem, type RoleItem,
} from '@/api/system'

const authStore = useAuthStore()

/** 权限判断 */
const hasPerm = (code: string) => (authStore.user?.permissions ?? []).includes(code)

// ============ 查询 ============
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const users = ref<AdminUserItem[]>([])
const loading = ref(false)

async function loadUsers() {
  loading.value = true
  try {
    const res = await listUsers({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    users.value = res.list ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadUsers()
}

// ============ 角色下拉 ============
const roles = ref<RoleItem[]>([])
async function loadRoles() {
  roles.value = (await listRoles()) ?? []
}

// ============ 新增/编辑 ============
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref()
const form = reactive({
  userId: 0,
  username: '',
  email: '',
  password: '',
  status: 1,
  roleIds: [] as number[],
})

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, { userId: 0, username: '', email: '', password: '', status: 1, roleIds: [] })
  dialogVisible.value = true
}

function openEdit(row: AdminUserItem) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    userId: row.userId, username: row.username, email: row.email, password: '', status: row.status ?? 1,
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (!hasPerm('system:user:create') && !hasPerm('system:user:update')) return
  if (dialogMode.value === 'create') {
    if (!form.username || !form.email || !form.password) {
      ElMessage.warning('请完整填写用户名、邮箱与初始密码')
      return
    }
    await createUser({ username: form.username, email: form.email, password: form.password, roleIds: form.roleIds })
    ElMessage.success('创建成功')
  } else {
    const payload: Record<string, unknown> = { status: form.status }
    if (form.username) payload.username = form.username
    if (form.email) payload.email = form.email
    if (form.password) payload.password = form.password
    await updateUser(form.userId, payload)
    ElMessage.success('保存成功')
  }
  dialogVisible.value = false
  loadUsers()
}

// ============ 启停用 ============
async function toggleStatus(row: AdminUserItem) {
  await updateUser(row.userId, { status: row.status === 1 ? 0 : 1 })
  ElMessage.success(row.status === 1 ? '已禁用' : '已启用')
  loadUsers()
}

// ============ 删除 ============
async function handleDelete(row: AdminUserItem) {
  await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？删除后其角色绑定将一并解除。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteAdminUser(row.userId)
  ElMessage.success('删除成功')
  loadUsers()
}

// ============ 分配角色 ============
const assignVisible = ref(false)
const assignUserId = ref(0)
const assignUsername = ref('')
const selectedRoleIds = ref<number[]>([])

async function openAssign(row: AdminUserItem) {
  assignUserId.value = row.userId
  assignUsername.value = row.username
  const bound = (await userRoles(row.userId)) ?? []
  selectedRoleIds.value = bound.map((r) => r.roleId)
  assignVisible.value = true
}

async function submitAssign() {
  await assignUserRoles(assignUserId.value, selectedRoleIds.value)
  ElMessage.success('角色已更新')
  assignVisible.value = false
}

onMounted(() => {
  loadUsers()
  loadRoles()
})
</script>

<template>
  <div class="manage-page">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名 / 邮箱"
          :prefix-icon="Search"
          clearable
          class="keyword-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <div class="spacer" />
        <el-button v-if="hasPerm('system:user:create')" type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="content-card">
      <el-table v-loading="loading" :data="users" stripe class="manage-table">
        <el-table-column prop="userId" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              :disabled="!hasPerm('system:user:update')"
              @change="toggleStatus(row as AdminUserItem)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" min-width="170">
          <template #default="{ row }">
            <span v-if="row.lastLoginAt">{{ new Date(row.lastLoginAt).toLocaleString('zh-CN', { hour12: false }) }}</span>
            <el-tag v-else type="info" size="small">未登录</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="hasPerm('system:user:assign')" text type="primary" :icon="Connection" @click="openAssign(row as AdminUserItem)">分配角色</el-button>
            <el-button v-if="hasPerm('system:user:update')" text type="primary" :icon="EditPen" @click="openEdit(row as AdminUserItem)">编辑</el-button>
            <el-button
              v-if="hasPerm('system:user:delete')"
              text
              type="danger"
              :icon="Delete"
              @click="handleDelete(row as AdminUserItem)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </el-card>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增用户' : '编辑用户'"
      width="520px"
    >
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="邮箱" required>
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="初始密码" required>
          <el-input v-model="form.password" type="password" show-password placeholder="请输入初始密码" />
        </el-form-item>
        <el-form-item v-else label="重置密码">
          <el-input v-model="form.password" type="password" show-password placeholder="留空则不修改密码" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="初始角色">
          <el-select v-model="form.roleIds" multiple placeholder="可为新用户分配角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.roleId" :label="`${r.name} (${r.code})`" :value="r.roleId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog v-model="assignVisible" :title="`分配角色 - ${assignUsername}`" width="480px">
      <el-select v-model="selectedRoleIds" multiple placeholder="选择一个或多个角色" style="width: 100%">
        <el-option v-for="r in roles" :key="r.roleId" :label="`${r.name} (${r.code})`" :value="r.roleId" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.manage-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.toolbar-card,
.content-card {
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.keyword-input {
  width: 280px;
}
.spacer {
  flex: 1;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-4);
}
.manage-table {
  width: 100%;
}
</style>