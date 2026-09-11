/**
 * Token 刷新队列
 *
 * 当多个请求同时收到 401 响应时，只触发一次 refresh 调用，
 * 其余请求排队等待刷新完成后统一重放。刷新失败则统一拒绝。
 *
 * 对标 Web 端 request.ts 中的 WeakSet 防循环重试机制，
 * 但针对小程序端做了并发刷新队列增强。
 */
import { getStorage } from '@/utils/storage'
import type { LoginResponse } from '@/types/auth'
import type { ApiResult } from '@/types/api'

/** 是否正在刷新 */
let isRefreshing = false
/** 等待队列：刷新成功后重放的回调 */
type QueueCallback = (token: string) => void
type QueueReject = (reason?: unknown) => void
const pendingQueue: Array<{ resolve: QueueCallback; reject: QueueReject }> = []

/** API 基础地址（与 request.ts 共享） */
const BASE_URL = 'http://localhost:8080/api/v1'

/**
 * 将请求加入刷新等待队列
 * @returns Promise<string> 刷新成功时 resolve 新的 accessToken
 */
export function enqueueRefreshWait(): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    pendingQueue.push({ resolve, reject })
  })
}

/**
 * 尝试刷新令牌（并发安全）
 * @returns 是否刷新成功
 */
export async function tryRefreshToken(): Promise<boolean> {
  if (isRefreshing) {
    // 已有刷新请求进行中，排队等待
    await enqueueRefreshWait()
    return true
  }

  isRefreshing = true
  const refreshToken = getStorage<string>('ai_auth_refresh_token')

  if (!refreshToken) {
    flushQueue(false)
    isRefreshing = false
    return false
  }

  try {
    const result = await new Promise<ApiResult<LoginResponse> & { csrfFromHeader?: string }>((resolve, reject) => {
      uni.request({
        url: `${BASE_URL}/auth/refresh`,
        method: 'POST',
        header: { 'Content-Type': 'application/json' },
        data: { refreshToken },
        success: (res) => {
          const data = res.data as ApiResult<LoginResponse>
          if (data && data.code === 0 && data.data) {
            // 从响应头提取 CSRF Token
            const headerStr = JSON.stringify(res.header || {}).toLowerCase()
            const match = headerStr.match(/csrf-token=([^;\s,]+)/)
            const csrfFromHeader = match ? match[1] : undefined
            resolve({ ...data, csrfFromHeader })
          } else {
            reject(new Error(data?.message || '刷新失败'))
          }
        },
        fail: reject,
      })
    })

    const loginResp = result.data!
    // 持久化新令牌
    uni.setStorageSync('ai_auth_access_token', loginResp.accessToken)
    uni.setStorageSync('ai_auth_refresh_token', loginResp.refreshToken)
    // CSRF Token：优先用 body 中的，其次用响应头中提取的
    if (loginResp.csrfToken) {
      uni.setStorageSync('ai_csrf_token', loginResp.csrfToken)
    } else if (result.csrfFromHeader) {
      uni.setStorageSync('ai_csrf_token', result.csrfFromHeader)
    }

    // 通知队列中的请求使用新 token 重放
    flushQueue(true, loginResp.accessToken)
    isRefreshing = false
    return true
  } catch {
    flushQueue(false)
    // 刷新失败，清空本地认证态
    uni.removeStorageSync('ai_auth_access_token')
    uni.removeStorageSync('ai_auth_refresh_token')
    uni.removeStorageSync('ai_csrf_token')
    isRefreshing = false
    return false
  }
}

/**
 * 排空等待队列
 * @param success 是否刷新成功
 * @param token   新的 accessToken（成功时传入）
 */
function flushQueue(success: boolean, token?: string): void {
  while (pendingQueue.length > 0) {
    const item = pendingQueue.shift()!
    if (success && token) {
      item.resolve(token)
    } else {
      item.reject(new Error('Token 刷新失败'))
    }
  }
}

/** 重置刷新状态（注销时调用） */
export function resetRefreshState(): void {
  isRefreshing = false
  pendingQueue.length = 0
}
