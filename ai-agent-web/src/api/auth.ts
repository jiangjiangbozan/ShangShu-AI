/**
 * 认证相关 API 封装
 *
 * 对应后端 AuthController / UserController：
 * - POST /api/v1/auth/register           注册
 * - POST /api/v1/auth/login              登录
 * - POST /api/v1/auth/refresh            刷新令牌
 * - POST /api/v1/auth/logout            注销
 * - POST /api/v1/auth/password/forgot   忘记密码（发送重置邮件）
 * - POST /api/v1/auth/password/reset    重置密码
 * - GET  /api/v1/users/me               当前登录用户信息
 *
 * 复用 @/utils/request 的 axios 实例；该实例响应拦截器在 code === 0 时
 * 已剥壳返回 data 字段，故本文件方法实际返回业务数据 T（与 @/api/model
 * 约定一致）。`Result<T>` 作为后端统一响应原始结构类型保留并导出，供
 * 上层（store、拦截器等）在需要时引用。
 */
import { get, post } from '@/utils/request'

/** 后端统一响应格式（原始结构；本文件方法实际返回剥壳后的 data） */
export interface Result<T = unknown> {
  /** 业务状态码：0=成功，非 0=失败 */
  code: number
  /** 提示信息 */
  message: string
  /** 业务数据 */
  data: T
  /** 链路追踪 ID */
  traceId?: string
}

/** 登录响应数据 */
export interface LoginResponse {
  /** 用户 ID */
  userId: number
  /** 用户名 */
  username: string
  /** 邮箱 */
  email: string
  /** 访问令牌 */
  accessToken: string
  /** 刷新令牌 */
  refreshToken: string
  /** 过期时间（秒） */
  expiresIn: number
  /** CSRF 令牌 */
  csrfToken: string
}

/** 当前用户信息 */
export interface UserInfo {
  /** 用户 ID */
  userId: number
  /** 用户名 */
  username: string
  /** 邮箱 */
  email: string
  /** 账户状态 */
  status: number
  /** 最后登录时间（ISO 字符串） */
  lastLoginAt: string
  /** 角色编码集合（RBAC，如 ['SUPER_ADMIN']） */
  roles: string[]
  /** 权限点编码集合（RBAC，如 ['system:user:delete']） */
  permissions: string[]
}

/** 第三方 OAuth 授权跳转响应 */
export interface OAuthAuthorizeResp {
  /** 平台标识，如 gitee */
  provider: string
  /** 第三方授权页完整地址 */
  authorizeUrl: string
}

/** 注册请求 */
export interface RegisterReq {
  username: string
  email: string
  password: string
}

/** 登录请求 */
export interface LoginReq {
  /** 账号：邮箱或用户名 */
  account: string
  password: string
}

/**
 * 注册
 * POST /api/v1/auth/register
 */
export function register(data: RegisterReq) {
  return post<LoginResponse>('/auth/register', data)
}

/**
 * 登录
 * POST /api/v1/auth/login
 */
export function login(data: LoginReq) {
  return post<LoginResponse>('/auth/login', data)
}

/**
 * 刷新令牌
 * POST /api/v1/auth/refresh
 */
export function refresh(refreshToken: string) {
  return post<LoginResponse>('/auth/refresh', { refreshToken })
}

/**
 * 注销
 * POST /api/v1/auth/logout
 */
export function logout(accessToken: string, refreshToken: string) {
  return post<void>('/auth/logout', { accessToken, refreshToken })
}

/**
 * 忘记密码（发送重置邮件）
 * POST /api/v1/auth/password/forgot
 */
export function forgotPassword(email: string) {
  return post<void>('/auth/password/forgot', { email })
}

/**
 * 重置密码
 * POST /api/v1/auth/password/reset
 */
export function resetPassword(token: string, newPassword: string) {
  return post<void>('/auth/password/reset', { token, newPassword })
}

/**
 * 获取当前登录用户信息
 * GET /api/v1/users/me
 */
export function getCurrentUser() {
  return get<UserInfo>('/users/me')
}

/**
 * 获取第三方 OAuth 授权跳转地址
 * GET /api/v1/auth/oauth/{provider}/authorize
 *
 * @param provider 平台标识，如 gitee
 */
export function getOAuthAuthorizeUrl(provider: string) {
  return get<OAuthAuthorizeResp>(`/auth/oauth/${provider}/authorize`)
}
