/**
 * 系统管理（RBAC）API 封装
 *
 * 对应后端 add-rbac-management 能力，覆盖三组接口：
 * - /api/v1/permissions   权限点管理（菜单/操作）
 * - /api/v1/roles         角色管理 + 分配权限
 * - /api/v1/users         系统用户管理 + 分配角色
 *
 * 复用 @/utils/request；其响应拦截器在 code === 0 时剥壳返回 data，故本文件方法返回业务数据。
 */
import { get, post, put, del } from '@/utils/request'

// ============ 权限点 ============
/** 权限点 */
export interface PermissionItem {
  permissionId: number
  parentId: number
  code: string
  name: string
  /** 1=菜单，2=操作 */
  type: number
  path?: string
  icon?: string
  sort?: number
  status?: number
}

/** 权限点新增/编辑载荷 */
export interface PermissionPayload {
  parentId?: number
  code?: string
  name?: string
  type?: number
  path?: string
  icon?: string
  sort?: number
  status?: number
}

/** 查询权限点列表（type 可空=全部） */
export function listPermissions(type?: number) {
  return get<PermissionItem[]>('/permissions', type ? { type } : undefined)
}

/** 新增权限点 */
export function createPermission(data: PermissionPayload) {
  return post<PermissionItem>('/permissions', data)
}

/** 更新权限点 */
export function updatePermission(id: number, data: PermissionPayload) {
  return put<PermissionItem>(`/permissions/${id}`, data)
}

/** 删除权限点 */
export function deletePermission(id: number) {
  return del<void>(`/permissions/${id}`)
}

// ============ 角色 ============
/** 角色 */
export interface RoleItem {
  roleId: number
  code: string
  name: string
  remark?: string
  status?: number
}

/** 角色新增/编辑载荷 */
export interface RolePayload {
  code?: string
  name?: string
  remark?: string
  status?: number
}

/** 查询角色列表 */
export function listRoles(keyword?: string) {
  return get<RoleItem[]>('/roles', keyword ? { keyword } : undefined)
}

/** 新增角色 */
export function createRole(data: RolePayload) {
  return post<RoleItem>('/roles', data)
}

/** 更新角色 */
export function updateRole(id: number, data: RolePayload) {
  return put<RoleItem>(`/roles/${id}`, data)
}

/** 删除角色 */
export function deleteRole(id: number) {
  return del<void>(`/roles/${id}`)
}

/** 查询角色已绑定权限点 ID 列表 */
export function rolePermissions(id: number) {
  return get<number[]>(`/roles/${id}/permissions`)
}

/** 覆盖式分配角色权限 */
export function assignRolePermissions(id: number, permissionIds: number[]) {
  return put<void>(`/roles/${id}/permissions`, { permissionIds })
}

// ============ 系统用户 ============
/** 系统用户（不含敏感字段） */
export interface AdminUserItem {
  userId: number
  username: string
  email: string
  status: number
  lastLoginAt?: string
}

/** 分页结果 */
export interface PageResult<T> {
  list: T[]
  total: number
}

/** 用户分页查询载荷 */
export interface UserQuery {
  page?: number
  size?: number
  keyword?: string
}

/** 管理员代建用户载荷 */
export interface CreateUserPayload {
  username: string
  email: string
  password: string
  roleIds?: number[]
}

/** 编辑用户载荷 */
export interface UpdateUserPayload {
  username?: string
  email?: string
  status?: number
  password?: string
}

/** 分页查询用户 */
export function listUsers(query: UserQuery = {}) {
  return get<PageResult<AdminUserItem>>('/users', query)
}

/** 管理员代建用户 */
export function createUser(data: CreateUserPayload) {
  return post<AdminUserItem>('/users', data)
}

/** 编辑用户 */
export function updateUser(id: number, data: UpdateUserPayload) {
  return put<AdminUserItem>(`/users/${id}`, data)
}

/** 删除用户 */
export function deleteAdminUser(id: number) {
  return del<void>(`/users/${id}`)
}

/** 查询用户已绑定角色 */
export function userRoles(id: number) {
  return get<RoleItem[]>(`/users/${id}/roles`)
}

/** 覆盖式分配用户角色 */
export function assignUserRoles(id: number, roleIds: number[]) {
  return put<void>(`/users/${id}/roles`, { roleIds })
}