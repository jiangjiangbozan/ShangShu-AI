<script setup lang="ts">
/**
 * 系统管理 - 权限管理
 *
 * 以树形展示菜单权限（type=1）与操作权限（type=2），支持权限点 CRUD。
 * 权限点编码（code）为权限系统唯一约束，新增时必填；编辑时不可修改编码。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ElCard, ElButton, ElTable, ElTableColumn, ElTag, ElDialog, ElForm, ElFormItem,
  ElInput, ElSelect, ElOption, ElRadioGroup, ElRadioButton, ElInputNumber, ElIcon,
} from 'element-plus'
import { Plus, Folder, EditPen, Delete } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import {
  listPermissions, createPermission, updatePermission, deletePermission,
  type PermissionItem,
} from '@/api/system'

const authStore = useAuthStore()
const hasPerm = (code: string) => (authStore.user?.permissions ?? []).includes(code)

const permissions = ref<PermissionItem[]>([])
const loading = ref(false)

async function loadPermissions() {
  loading.value = true
  try {
    permissions.value = (await listPermissions()) ?? []
  } finally {
    loading.value = false
  }
}

const menus = () => permissions.value.filter((p) => p.type === 1)
const actionsOf = (menuId: number) =>
  permissions.value.filter((p) => p.type === 2 && p.parentId === menuId)

// ============ 新增/编辑 ============
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const form = reactive({
  permissionId: 0, parentId: 0, code: '', name: '', type: 1, path: '', icon: '', sort: 0,
})

function openCreateMenu() {
  dialogMode.value = 'create'
  Object.assign(form, {
    permissionId: 0, parentId: 0, code: '', name: '', type: 1, path: '', icon: '', sort: (menus().length + 1),
  })
  dialogVisible.value = true
}
function openCreateAction(menuId: number) {
  dialogMode.value = 'create'
  Object.assign(form, {
    permissionId: 0, parentId: menuId, code: '', name: '', type: 2, path: '', icon: '', sort: (actionsOf(menuId).length + 1),
  })
  dialogVisible.value = true
}
function openEdit(row: PermissionItem) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    permissionId: row.permissionId, parentId: row.parentId ?? 0, code: row.code, name: row.name,
    type: row.type ?? 1, path: row.path ?? '', icon: row.icon ?? '', sort: row.sort ?? 0,
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.code || !form.name) {
    ElMessage.warning('请填写权限编码与名称')
    return
  }
  if (dialogMode.value === 'create') {
    await createPermission({
      parentId: form.parentId, code: form.code, name: form.name, type: form.type,
      path: form.path || undefined, icon: form.icon || undefined, sort: form.sort,
    })
    ElMessage.success('创建成功')
  } else {
    await updatePermission(form.permissionId, {
      name: form.name, path: form.path || undefined, icon: form.icon || undefined, sort: form.sort,
    })
    ElMessage.success('保存成功')
  }
  dialogVisible.value = false
  loadPermissions()
}

async function handleDelete(row: PermissionItem) {
  if (row.type === 1 && actionsOf(row.permissionId).length > 0) {
    ElMessage.warning('该菜单下存在操作权限，请先删除其下的操作权限')
    return
  }
  await ElMessageBox.confirm(`确定删除权限点「${row.name} (${row.code})」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deletePermission(row.permissionId)
  ElMessage.success('删除成功')
  loadPermissions()
}

onMounted(loadPermissions)
</script>

<template>
  <div class="manage-page">
    <el-card shadow="never" class="content-card">
      <div class="toolbar">
        <span class="title">权限点列表</span>
        <div class="spacer" />
        <el-button v-if="hasPerm('system:permission:create')" type="primary" :icon="Plus" @click="openCreateMenu">新增菜单</el-button>
      </div>

      <el-table v-loading="loading" :data="menus()" row-key="permissionId" stripe class="manage-table">
        <el-table-column prop="permissionId" label="ID" width="80" />
        <el-table-column prop="name" label="权限名称" min-width="160">
          <template #default="{ row }">
            <span class="menu-name">
              <el-icon :size="14"><Folder /></el-icon>
              {{ row.name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="权限编码" min-width="180" />
        <el-table-column prop="path" label="路由路径" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.path" size="small">{{ row.path }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'info' : 'success'" size="small">
              {{ row.status === 0 ? '禁用' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作权限" min-width="260">
          <template #default="{ row }">
            <div class="action-cells">
              <el-tag v-for="op in actionsOf(row.permissionId)" :key="op.permissionId" size="small" type="warning" effect="light" class="action-cell">
                {{ op.name }}
              </el-tag>
              <el-button
                v-if="!row.path"
                text type="primary" size="small" :icon="Plus"
                @click="openCreateAction(row.permissionId)"
              >
                添加操作
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="hasPerm('system:permission:update')" text type="primary" :icon="EditPen" @click="openEdit(row as PermissionItem)">编辑</el-button>
            <el-button v-if="hasPerm('system:permission:delete')" text type="danger" :icon="Delete" @click="handleDelete(row as PermissionItem)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增权限点' : '编辑权限点'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="权限类型">
          <el-radio-group v-model="form.type" :disabled="dialogMode === 'edit'">
            <el-radio-button :label="1">菜单</el-radio-button>
            <el-radio-button :label="2">操作</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.type === 2" label="所属菜单">
          <el-select v-model="form.parentId" placeholder="请选择所属菜单" style="width: 100%">
            <el-option v-for="m in menus()" :key="m.permissionId" :label="`${m.name} (${m.code})`" :value="m.permissionId" />
          </el-select>
        </el-form-item>
        <el-form-item label="权限编码" required>
          <el-input v-model="form.code" :disabled="dialogMode === 'edit'" placeholder="如 system:user:create" />
        </el-form-item>
        <el-form-item label="权限名称" required>
          <el-input v-model="form.name" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item v-if="form.type === 1" label="路由路径">
          <el-input v-model="form.path" placeholder="如 /system/users" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
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
.menu-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: var(--font-weight-medium);
}
.action-cells {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.action-cell {
  margin-right: 0;
}
</style>