/**
 * 认证状态管理（Pinia Composition API 风格）
 *
 * 维护访问/刷新令牌、CSRF 令牌、当前用户信息：
 * - 访问/刷新令牌持久化到 localStorage（命名 key 同 design 约定）
 * - CSRF 令牌写入 Cookie（path=/，max-age=7200），便于请求拦截器
 *   通过 document.cookie 读取并附加到 X-CSRF-Token 头
 * - 认证状态初始化从 localStorage / Cookie 恢复，避免刷新即丢失登录态
 */
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import {
  login as apiLogin,
  register as apiRegister,
  refresh as apiRefresh,
  logout as apiLogout,
  getCurrentUser as apiGetCurrentUser,
  type LoginResponse,
  type UserInfo,
  type LoginReq,
  type RegisterReq,
} from '@/api/auth'

/** localStorage 键名（design 约定，禁止随意改动以兼容后端协议） */
const ACCESS_TOKEN_KEY = 'ai_auth_access_token'
const REFRESH_TOKEN_KEY = 'ai_auth_refresh_token'

/** CSRF Cookie 名 */
const CSRF_COOKIE_NAME = 'csrf-token'

/**
 * 读取 Cookie
 * @param name Cookie 名称
 */
export function getCookie(name: string): string | null {
  if (typeof document === 'undefined') return null
  const key = `${name}=`
  const cookies = document.cookie ? document.cookie.split('; ') : []
  for (const cookie of cookies) {
    if (cookie.startsWith(key)) {
      try {
        return decodeURIComponent(cookie.slice(key.length))
      } catch {
        return cookie.slice(key.length)
      }
    }
  }
  return null
}

/**
 * 写入 Cookie
 * @param name    Cookie 名称
 * @param value   Cookie 值
 * @param maxAge  最大存活时间（秒），不传则为会话级
 */
export function setCookie(name: string, value: string, maxAge?: number): void {
  if (typeof document === 'undefined') return
  const segments = [
    `${name}=${encodeURIComponent(value)}`,
    'path=/',
    'SameSite=Lax',
  ]
  if (maxAge !== undefined) {
    segments.push(`max-age=${maxAge}`)
  }
  document.cookie = segments.join('; ')
}

/**
 * 删除 Cookie（通过立即过期实现）
 * @param name Cookie 名称
 */
export function removeCookie(name: string): void {
  if (typeof document === 'undefined') return
  document.cookie = `${name}=; path=/; max-age=0; SameSite=Lax`
}

/** 初始化访问令牌 */
function initAccessToken(): string | null {
  if (typeof localStorage === 'undefined') return null
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

/** 初始化刷新令牌 */
function initRefreshToken(): string | null {
  if (typeof localStorage === 'undefined') return null
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

/** 初始化 CSRF 令牌 */
function initCsrfToken(): string | null {
  return getCookie(CSRF_COOKIE_NAME)
}

export const useAuthStore = defineStore('auth', () => {
  // ============ State ============
  /** 访问令牌 */
  const accessToken = ref<string | null>(initAccessToken())
  /** 刷新令牌 */
  const refreshToken = ref<string | null>(initRefreshToken())
  /** CSRF 令牌 */
  const csrfToken = ref<string | null>(initCsrfToken())
  /** 当前登录用户信息 */
  const user = ref<UserInfo | null>(null)
  /** 加载中（登录/注册/刷新/获取用户） */
  const loading = ref(false)
  /** 是否已完成启动时的登录态校验（幂等标记） */
  const initialized = ref(false)

  // ============ Getters ============
  /** 是否已认证（accessToken 非空即认为已登录） */
  const isAuthenticated = computed(() => !!accessToken.value)
  /** 当前用户角色编码集合（RBAC） */
  const roles = computed<string[]>(() => user.value?.roles ?? [])
  /** 当前用户权限点编码集合（RBAC） */
  const permissions = computed<string[]>(() => user.value?.permissions ?? [])
  /** 是否拥有指定权限点（如 'system:user:menu'）；空集合视为无权限 */
  function hasPerm(perm: string): boolean {
    return permissions.value.includes(perm)
  }
  /** 是否拥有指定角色编码（如 'SUPER_ADMIN'） */
  function hasRole(role: string): boolean {
    return roles.value.includes(role)
  }

  // ============ Actions ============

  /**
   * 持久化令牌到 localStorage / Cookie
   * @param access  访问令牌
   * @param refresh 刷新令牌
   * @param csrf    CSRF 令牌
   */
  function setTokens(access: string, refresh: string, csrf: string): void {
    accessToken.value = access
    refreshToken.value = refresh
    csrfToken.value = csrf

    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(ACCESS_TOKEN_KEY, access)
      localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
    }
    // CSRF 写入 Cookie：max-age 7200s（2 小时，与 design 约定一致）
    setCookie(CSRF_COOKIE_NAME, csrf, 7200)
  }

  /** 清空本地状态与持久化存储（登出 / 失效时调用） */
  function clearTokens(): void {
    accessToken.value = null
    refreshToken.value = null
    csrfToken.value = null
    user.value = null

    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(ACCESS_TOKEN_KEY)
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
    removeCookie(CSRF_COOKIE_NAME)
  }

  /**
   * 登录
   * @param req 账号 + 密码
   */
  async function login(req: LoginReq): Promise<UserInfo> {
    loading.value = true
    try {
      const resp: LoginResponse = await apiLogin(req)
      setTokens(resp.accessToken, resp.refreshToken, resp.csrfToken)
      // 登录成功后立即拉取当前用户信息
      const info = await apiGetCurrentUser()
      user.value = info
      return info
    } finally {
      loading.value = false
    }
  }

  /**
   * 注册
   * @param req 用户名 + 邮箱 + 密码
   */
  async function register(req: RegisterReq): Promise<UserInfo> {
    loading.value = true
    try {
      const resp: LoginResponse = await apiRegister(req)
      setTokens(resp.accessToken, resp.refreshToken, resp.csrfToken)
      const info = await apiGetCurrentUser()
      user.value = info
      return info
    } finally {
      loading.value = false
    }
  }

  /**
   * 刷新访问令牌
   * @returns 是否刷新成功
   */
  async function refresh(): Promise<boolean> {
    if (!refreshToken.value) return false
    try {
      const resp: LoginResponse = await apiRefresh(refreshToken.value)
      setTokens(resp.accessToken, resp.refreshToken, resp.csrfToken)
      return true
    } catch {
      clearTokens()
      return false
    }
  }

  /**
   * 注销
   */
  async function logout(): Promise<void> {
    const access = accessToken.value
    const refresh = refreshToken.value
    try {
      if (access && refresh) {
        await apiLogout(access, refresh)
      }
    } catch {
      // 即使后端注销失败，前端也要清空本地态
    } finally {
      clearTokens()
    }
  }

  /**
   * 获取当前登录用户信息
   */
  async function fetchCurrentUser(): Promise<UserInfo | null> {
    if (!accessToken.value) return null
    loading.value = true
    try {
      const info = await apiGetCurrentUser()
      user.value = info
      return info
    } finally {
      loading.value = false
    }
  }

  /**
   * 应用启动时校验登录态（幂等，仅首次调用生效）。
   *
   * <p>本地存在访问令牌时，主动向后端发起一次 {@code GET /users/me} 校验其有效性：
   * <ul>
   *   <li>令牌有效：回填 {@link user}，正常放行受保护页面；
   *   <li>访问令牌过期但刷新令牌有效：由请求拦截器自动 refresh 后重放，静默续期；
   *   <li>彻底失效：拦截器清空令牌并重定向登录页。
   * </ul>
   * 网络或服务器异常时仅捕获、保留本地令牌，避免误登出。
   */
  async function initialize(): Promise<void> {
    if (initialized.value) return
    initialized.value = true
    if (!accessToken.value) return
    try {
      await fetchCurrentUser()
    } catch {
      // 认证失效已由请求拦截器统一 clearTokens + 跳转登录页，此处不重复处理
    }
  }

  return {
    // state
    accessToken,
    refreshToken,
    csrfToken,
    user,
    loading,
    // getters
    isAuthenticated,
    roles,
    permissions,
    hasPerm,
    hasRole,
    // actions
    setTokens,
    clearTokens,
    login,
    register,
    refresh,
    logout,
    fetchCurrentUser,
    initialize,
  }
})
