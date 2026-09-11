/**
 * 认证相关类型定义
 *
 * 与 Web 端 api/auth.ts 导出类型完全一致，确保三端 DTO 统一。
 */

/** 登录请求 */
export interface LoginReq {
  /** 账号：邮箱或用户名 */
  account: string
  password: string
}

/** 注册请求 */
export interface RegisterReq {
  username: string
  email: string
  password: string
}

/** 登录响应 */
export interface LoginResponse {
  userId: number
  username: string
  email: string
  accessToken: string
  refreshToken: string
  expiresIn: number
  csrfToken: string
}

/** 当前用户信息 */
export interface UserInfo {
  userId: number
  username: string
  email: string
  status: number
  lastLoginAt: string
  /** 角色编码集合（RBAC，如 ['SUPER_ADMIN']） */
  roles: string[]
  /** 权限点编码集合（RBAC，如 ['system:user:delete']） */
  permissions: string[]
}
