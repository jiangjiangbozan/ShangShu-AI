/**
 * uni.request 请求封装
 *
 * 对标 Web 端 src/utils/request.ts，保持拦截器模式一致：
 * - 请求拦截：注入 X-Trace-Id、Authorization Bearer、X-CSRF-Token
 * - 响应拦截：剥壳 Result<T>（code===0 返回 data）
 * - 401 自动刷新：refreshToken → 重放原请求（_retried 标记防循环）
 * - 统一错误提示：uni.showToast
 *
 * 与 Web 端差异：
 * - 无 Axios 实例，使用 uni.request + 手动拦截器链
 * - 无 Cookie，CSRF Token 改存 Storage
 * - 无 WeakSet，改用请求选项对象上的 _retried 标记
 */
import { generateTraceId } from '@/utils/trace'
import { getStorage, setStorage } from '@/utils/storage'
import { tryRefreshToken } from '@/composables/useTokenRefresh'
import type { ApiResult } from '@/types/api'

/** CSRF Token Storage 键 */
const CSRF_TOKEN_KEY = 'ai_csrf_token'

/** 业务码常量 */
const BIZ_CODE_SUCCESS = 0
const BIZ_CODE_UNAUTHORIZED = 401

/** API 基础地址 */
const BASE_URL = 'http://localhost:8080/api/v1'

/** 请求超时时间（毫秒） */
const TIMEOUT = 30000

/** 内部扩展：请求选项上的重试标记 */
interface RequestOptionsInternal {
  /** 已尝试过刷新重放，防止循环重试 */
  _retried?: boolean
}

/**
 * 构建请求头：注入 TraceId + Authorization + CSRF
 */
function buildHeaders(customHeaders?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json;charset=utf-8',
    ...customHeaders,
  }

  // 注入 TraceId
  if (!headers['X-Trace-Id']) {
    headers['X-Trace-Id'] = generateTraceId()
  }

  // 注入 Authorization
  const accessToken = getStorage<string>('ai_auth_access_token')
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  // 注入 CSRF Token（从 Storage 读取，替代 Web 端的 Cookie 方案）
  const csrfToken = getStorage<string>(CSRF_TOKEN_KEY)
  if (csrfToken) {
    headers['X-CSRF-Token'] = csrfToken
  }

  return headers
}

/**
 * 从响应 Set-Cookie 头中提取 CSRF Token
 *
 * 后端通过 Set-Cookie 下发 csrf-token=<value>，
 * 浏览器会自动携带，但小程序需要手动提取并存入 Storage。
 */
function extractCsrfFromResponse(headers: Record<string, string> | string[] | undefined): void {
  if (!headers) return
  // uni.request 的 header 可能是对象或数组格式，统一转为字符串处理
  const headerStr = typeof headers === 'string' ? headers : JSON.stringify(headers)
  const lower = headerStr.toLowerCase()
  // 匹配 csrf-token=<value>
  const match = lower.match(/csrf-token=([^;\s,]+)/)
  if (match && match[1]) {
    setStorage(CSRF_TOKEN_KEY, match[1])
  }
}

/**
 * 构建完整 URL
 */
function buildUrl(url: string, params?: Record<string, unknown>): string {
  let fullUrl = url.startsWith('http') ? url : `${BASE_URL}${url}`
  if (params) {
    const query = Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== null)
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
      .join('&')
    if (query) fullUrl += `?${query}`
  }
  return fullUrl
}

/**
 * 显示错误提示
 */
function showError(message: string): void {
  uni.showToast({ title: message, icon: 'none', duration: 2000 })
}

/**
 * 核心请求函数
 *
 * 实现请求/响应拦截器链：
 * 1. 构建请求头（TraceId + Auth + CSRF）
 * 2. 发起 uni.request
 * 3. 剥壳 Result<T>
 * 4. 401 → 自动刷新 → 重放
 * 5. 其他错误 → 提示 + reject
 */
function request<T>(
  url: string,
  options: UniApp.RequestOptions & RequestOptionsInternal,
): Promise<T> {
  const header = buildHeaders(options.header as Record<string, string> | undefined)
  const fullUrl = buildUrl(url)

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: fullUrl,
      method: options.method || 'GET',
      data: options.data,
      header,
      timeout: options.timeout || TIMEOUT,
      success: (res) => {
        const statusCode = res.statusCode
        const result = res.data as ApiResult<T> | null

        // 从响应头提取 CSRF Token（后端通过 Set-Cookie 下发）
        extractCsrfFromResponse(res.header as Record<string, string>)

        // 非 Result 格式（如文件下载），直接返回
        if (result == null || typeof result.code !== 'number') {
          resolve(res.data as T)
          return
        }

        // 业务成功
        if (result.code === BIZ_CODE_SUCCESS) {
          resolve(result.data as T)
          return
        }

        // 业务码 401：尝试刷新重放
        if (result.code === BIZ_CODE_UNAUTHORIZED && !options._retried) {
          handleUnauthorized<T>(url, options).then(resolve, reject)
          return
        }

        // 其他业务失败
        showError(result.message || '请求失败')
        reject(new Error(result.message || `业务错误：${result.code}`))
      },
      fail: (err) => {
        showError(err.errMsg || '网络异常')
        reject(new Error(err.errMsg || '网络异常'))
      },
    })
  })
}

/**
 * 处理 401 未授权：刷新令牌 → 重放原请求
 */
async function handleUnauthorized<T>(
  url: string,
  options: UniApp.RequestOptions & RequestOptionsInternal,
): Promise<T> {
  const refreshed = await tryRefreshToken()
  if (!refreshed) {
    // 刷新失败，跳转登录页
    uni.navigateTo({ url: '/pages/login/index' })
    return Promise.reject(new Error('认证已过期'))
  }

  // 刷新成功，带新 token 重放原请求
  const retryOptions = { ...options, _retried: true } as UniApp.RequestOptions & RequestOptionsInternal
  return request<T>(url, retryOptions)
}

/* ============ 导出便捷方法 ============ */

/** GET 请求 */
export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>(url, { method: 'GET', data: params })
}

/** POST 请求 */
export function post<T>(url: string, data?: object): Promise<T> {
  return request<T>(url, { method: 'POST', data })
}

/** PUT 请求 */
export function put<T>(url: string, data?: object): Promise<T> {
  return request<T>(url, { method: 'PUT', data })
}

/** DELETE 请求 */
export function del<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>(url, { method: 'DELETE', data: params })
}

export { BASE_URL }
