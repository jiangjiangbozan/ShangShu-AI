/**
 * 认证状态管理（Pinia Composition API 风格）
 *
 * 对标 Web 端 src/stores/auth.ts，State / Getters / Actions 签名一致。
 * 持久化方式差异：localStorage → uni.setStorageSync。
 */
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import {
  login as apiLogin,
  register as apiRegister,
  logout as apiLogout,
  getCurrentUser as apiGetCurrentUser,
} from '@/api/auth'
import { getStorage, setStorage, removeStorage } from '@/utils/storage'
import { resetRefreshState } from '@/composables/useTokenRefresh'
import type { LoginReq, RegisterReq, LoginResponse, UserInfo } from '@/types/auth'

/** Storage 键名（与 Web 端保持一致，禁止随意改动） */
const ACCESS_TOKEN_KEY = 'ai_auth_access_token'
const REFRESH_TOKEN_KEY = 'ai_auth_refresh_token'
const CSRF_TOKEN_KEY = 'ai_csrf_token'

export const useAuthStore = defineStore('auth', () => {
  // ============ State ============
  /** 访问令牌 */
  const accessToken = ref<string | null>(getStorage<string>(ACCESS_TOKEN_KEY))
  /** 刷新令牌 */
  const refreshToken = ref<string | null>(getStorage<string>(REFRESH_TOKEN_KEY))
  /** CSRF 令牌 */
  const csrfToken = ref<string | null>(getStorage<string>(CSRF_TOKEN_KEY))
  /** 当前登录用户信息 */
  const user = ref<UserInfo | null>(null)
  /** 加载中 */
  const loading = ref(false)
  /** 是否已完成启动时的登录态校验 */
  const initialized = ref(false)

  // ============ Getters ============
  /** 是否已认证 */
  const isAuthenticated = computed(() => !!accessToken.value)
  /** 当前用户角色编码集合 */
  const roles = computed<string[]>(() => user.value?.roles ?? [])
  /** 当前用户权限点编码集合 */
  const permissions = computed<string[]>(() => user.value?.permissions ?? [])
  /** 是否拥有指定权限点 */
  function hasPerm(perm: string): boolean {
    return permissions.value.includes(perm)
  }
  /** 是否拥有指定角色 */
  function hasRole(role: string): boolean {
    return roles.value.includes(role)
  }

  // ============ Actions ============

  /**
   * 持久化令牌到 Storage
   */
  function setTokens(access: string, refreshVal: string, csrf?: string): void {
    accessToken.value = access
    refreshToken.value = refreshVal
    setStorage(ACCESS_TOKEN_KEY, access)
    setStorage(REFRESH_TOKEN_KEY, refreshVal)
    // CSRF Token：仅当后端明确返回时才覆盖，避免清空从响应头提取的值
    if (csrf) {
      csrfToken.value = csrf
      setStorage(CSRF_TOKEN_KEY, csrf)
    }
  }

  /** 清空本地状态与持久化存储 */
  function clearTokens(): void {
    accessToken.value = null
    refreshToken.value = null
    csrfToken.value = null
    user.value = null
    removeStorage(ACCESS_TOKEN_KEY)
    removeStorage(REFRESH_TOKEN_KEY)
    removeStorage(CSRF_TOKEN_KEY)
    resetRefreshState()
  }

  /** 登录 */
  async function login(req: LoginReq): Promise<UserInfo> {
    loading.value = true
    try {
      const resp: LoginResponse = await apiLogin(req)
      setTokens(resp.accessToken, resp.refreshToken, resp.csrfToken)
      const info = await apiGetCurrentUser()
      user.value = info
      return info
    } finally {
      loading.value = false
    }
  }

  /** 注册 */
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

  /** 刷新访问令牌 */
  async function refresh(): Promise<boolean> {
    if (!refreshToken.value) return false
    try {
      const { refresh: apiRefresh } = await import('@/api/auth')
      const resp: LoginResponse = await apiRefresh(refreshToken.value)
      setTokens(resp.accessToken, resp.refreshToken, resp.csrfToken)
      return true
    } catch {
      clearTokens()
      return false
    }
  }

  /** 注销 */
  async function logout(): Promise<void> {
    const access = accessToken.value
    const refreshVal = refreshToken.value
    try {
      if (access && refreshVal) {
        await apiLogout(access, refreshVal)
      }
    } catch {
      // 即使后端注销失败，前端也要清空本地态
    } finally {
      clearTokens()
    }
  }

  /** 获取当前登录用户信息 */
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
   * 应用启动时校验登录态（幂等）。
   * 本地有令牌时主动调 GET /users/me 校验有效性；
   * 令牌过期由请求拦截器自动 refresh 重放；彻底失效则清态。
   */
  async function initialize(): Promise<void> {
    if (initialized.value) return
    initialized.value = true
    if (!accessToken.value) return
    try {
      await fetchCurrentUser()
    } catch {
      // 认证失效已由请求拦截器统一处理
    }
  }

  return {
    // state
    accessToken,
    refreshToken,
    csrfToken,
    user,
    loading,
    initialized,
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
