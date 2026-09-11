<script setup lang="ts">
/**
 * 系统管理 - 角色管理
 *
 * 支持角色 CRUD 与「分配权限」（菜单/操作分组权限树，覆盖式更新）。
 * SUPER_ADMIN 内置角色受保护，不提供删除/编辑/分配权限入口。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ElCard, ElButton, ElTable, ElTableColumn, ElTag, ElDialog, ElForm, ElFormItem,
  ElInput, ElSwitch, ElTree,
} from 'element-plus'
import { Plus, Key, EditPen, Delete } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import {
  listRoles, createRole, updateRole, deleteRole, rolePermissions, assignRolePermissions,
  listPermissions, type RoleItem,
} from '@/api/system'

const authStore = useAuthStore()
const hasPerm = (code: string) => (authStore.user?.permissions ?? []).includes(code)

const roles = ref<RoleItem[]>([])
const loading = ref(false)

async function loadRoles() {
  loading.value = true
  try {
    roles.value = (await listRoles()) ?? []
  } finally {
    loading.value = false
  }
}

const isSuper = (role: RoleItem) => role.code === 'SUPER_ADMIN'

// ============ 新增/编辑 ============
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const form = reactive({ roleId: 0, code: '', name: '', remark: '', status: 1 })

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, { roleId: 0, code: '', name: '', remark: '', status: 1 })
  dialogVisible.value = true
}
function openEdit(row: RoleItem) {
  dialogMode.value = 'edit'
  Object.assign(form, { roleId: row.roleId, code: row.code, name: row.name, remark: row.remark ?? '', status: row.status ?? 1 })
  dialogVisible.value = true
}

async function submitForm() {
  if (dialogMode.value === 'create') {
    if (!form.code || !form.name) {
      ElMessage.warning('请填写角色编码与名称')
      return
    }
    await createRole({ code: form.code, name: form.name, remark: form.remark, status: form.status })
    ElMessage.success('创建成功')
  } else {
    await updateRole(form.roleId, { name: form.name, remark: form.remark, status: form.status })
    ElMessage.success('保存成功')
  }
  dialogVisible.value = false
  loadRoles()
}

async function toggleStatus(row: RoleItem) {
  await updateRole(row.roleId, { status: row.status === 1 ? 0 : 1 })
  ElMessage.success(row.status === 1 ? '已禁用' : '已启用')
  loadRoles()
}

async function handleDelete(row: RoleItem) {
  await ElMessageBox.confirm(`确定删除角色「${row.name} (${row.code})」吗？其下用户绑定与权限绑定将被解除。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteRole(row.roleId)
  ElMessage.success('删除成功')
  loadRoles()
}

// ============ 分配权限 ============
const assignVisible = ref(false)
const assignRole = ref<RoleItem | null>(null)
const treeData = ref<TreeNode[]>([])
const checkedKeys = ref<number[]>([])
const treeRef = ref()

interface TreeNode {
  permissionId: number
  label: string
  children?: TreeNode[]
}

async function openAssign(row: RoleItem) {
  assignRole.value = row
  const perms = (await listPermissions()) ?? []
  const menus = perms.filter((p) => p.type === 1)
  treeData.value = menus.map((m) => ({
    permissionId: m.permissionId,
    label: `${m.name} (${m.code})`,
    children: perms
      .filter((p) => p.type === 2 && p.parentId === m.permissionId)
      .map((o) => ({ permissionId: o.permissionId, label: `${o.name} (${o.code})` })),
  }))
  checkedKeys.value = (await rolePermissions(row.roleId)) ?? []
  assignVisible.value = true
}

async function submitAssign() {
  if (!assignRole.value) return
  const tree = treeRef.value
  const ids = [...(tree?.getCheckedKeys(false) ?? []), ...(tree?.getHalfCheckedKeys() ?? [])]
    .map((v) => Number(v))
    .filter((v) => Number.isFinite(v) && v > 0)
  await assignRolePermissions(assignRole.value.roleId, Array.from(new Set(ids)))
  ElMessage.success('权限已更新')
  assignVisible.value = false
}

onMounted(loadRoles)
</script>

<template>
  <div class="manage-page">
    <el-card shadow="never" class="content-card">
      <div class="toolbar">
        <span class="title">角色列表</span>
        <div class="spacer" />
        <el-button v-if="hasPerm('system:role:create')" type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
      </div>

      <el-table v-loading="loading" :data="roles" stripe class="manage-table">
        <el-table-column prop="roleId" label="ID" width="80" />
        <el-table-column prop="code" label="角色编码" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="isSuper(row as RoleItem)" type="warning" effect="dark" round>{{ row.code }}</el-tag>
            <span v-else>{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="角色名称" min-width="140" />
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              :disabled="isSuper(row as RoleItem) || !hasPerm('system:role:update')"
              @change="toggleStatus(row as RoleItem)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="hasPerm('system:role:assign') && !isSuper(row as RoleItem)"
              text type="primary" :icon="Key" @click="openAssign(row as RoleItem)"
            >
              分配权限
            </el-button>
            <el-button v-if="hasPerm('system:role:update') && !isSuper(row as RoleItem)" text type="primary" :icon="EditPen" @click="openEdit(row as RoleItem)">编辑</el-button>
            <el-button v-if="hasPerm('system:role:delete') && !isSuper(row as RoleItem)" text type="danger" :icon="Delete" @click="handleDelete(row as RoleItem)">删除</el-button>
            <el-button v-if="isSuper(row as RoleItem)" text disabled>内置角色</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增角色' : '编辑角色'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色编码" required>
          <el-input v-model="form.code" :disabled="dialogMode === 'edit'" placeholder="如 USER_ADMIN" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" placeholder="角色说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限弹窗 -->
    <el-dialog
      v-model="assignVisible"
      :title="`分配权限 - ${assignRole ? assignRole.name : ''}`"
      width="520px"
    >
      <el-tree
        ref="treeRef"
        :data="treeData"
        show-checkbox
        node-key="permissionId"
        default-expand-all
        class="perm-tree"
      />
      <el-alert
        v-if="assignRole && isSuper(assignRole)"
        type="warning"
        :closable="false"
        title="超级管理员自动拥有全部权限，无需分配。"
        style="margin-top: 12px"
      />
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :disabled="assignRole ? isSuper(assignRole) : false" @click="submitAssign">保存</el-button>
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
.content-card {
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}
.title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}
.spacer {
  flex: 1;
}
.manage-table {
  width: 100%;
}
.perm-tree {
  max-height: 420px;
  overflow: auto;
  padding: var(--space-2);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}
</style>