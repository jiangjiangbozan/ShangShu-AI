/**
 * 认证相关 API 封装
 *
 * 对标 Web 端 src/api/auth.ts，接口路径完全一致。
 * 复用 @/utils/request 的请求实例；响应拦截器在 code === 0 时
 * 已剥壳返回 data 字段，故本文件方法实际返回业务数据 T。
 */
import { get, post } from '@/utils/request'
import type { LoginReq, RegisterReq, LoginResponse, UserInfo } from '@/types/auth'

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
 * 获取当前登录用户信息
 * GET /api/v1/users/me
 */
export function getCurrentUser() {
  return get<UserInfo>('/users/me')
}
