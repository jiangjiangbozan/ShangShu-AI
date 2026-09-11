/**
 * axios 请求封装
 *
 * 依据《企业Java后端开发规范.md》与 design.md D5：
 * - 自动注入 X-Trace-Id 请求头（前端生成，后端可继承或重新生成）
 * - 统一处理后端 Result<T> 响应格式
 * - 业务错误（code !== 0）与 HTTP 错误统一弹窗提示
 *
 * 认证扩展（design D7）：
 * - 请求拦截器：注入 Authorization: Bearer <accessToken> 与 X-CSRF-Token
 * - 响应拦截器：业务码 401 或 HTTP 401 时尝试 refresh，成功后重放原请求；
 *   refresh 失败则清空认证态并跳转登录页。使用 WeakSet 防止循环重试。
 */
import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

/** 后端统一响应格式 */
export interface ApiResult<T = unknown> {
  /** 业务状态码：0=成功，非 0=失败 */
  code: number
  /** 提示信息 */
  message: string
  /** 业务数据 */
  data?: T
  /** 链路追踪 ID */
  traceId?: string
}

/** 业务码：未授权（需要刷新或重新登录） */
const BIZ_CODE_UNAUTHORIZED = 401
/** 业务码：成功 */
const BIZ_CODE_SUCCESS = 0

/** 读 Cookie 工具（与 auth store 同语义，避免循环依赖而在此内联） */
function readCookie(name: string): string | null {
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

/** 生成前端 TraceId（32 位无连字符 UUID） */
function generateTraceId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }
  // 降级方案
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 18)
}

/** 创建 axios 实例 */
const request: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8',
  },
})

/**
 * 标记已尝试刷新重放的请求 config，避免无限循环重试。
 * WeakSet 不阻止 config 被 GC，且对 AxiosRequestConfig 引用无侵入。
 */
const retriedConfigs = new WeakSet<InternalAxiosRequestConfig>()

/**
 * 获取 auth store（延迟动态导入，避免与 @/api/auth → @/utils/request
 * 之间的编译期循环依赖；store 只在拦截器运行时才被解析）。
 */
async function getAuthStore() {
  const { useAuthStore } = await import('@/stores/auth')
  return useAuthStore()
}

/** 请求拦截器：注入 TraceId、Authorization、X-CSRF-Token */
request.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    // 注入 TraceId（如果用户未手动指定）
    if (!config.headers['X-Trace-Id']) {
      config.headers['X-Trace-Id'] = generateTraceId()
    }

    // 注入 Authorization 与 CSRF（CSRF 仅对非 GET 请求附加）
    try {
      const authStore = await getAuthStore()
      if (authStore.accessToken) {
        config.headers.Authorization = `Bearer ${authStore.accessToken}`
      }
    } catch {
      // store 尚未初始化（main.ts 未挂载 pinia 之前），跳过
    }

    // CSRF：从 Cookie 读取（与 store 写入位置一致），非 GET 附加
    const method = (config.method || 'get').toLowerCase()
    if (method !== 'get') {
      const csrf = readCookie('csrf-token')
      if (csrf) {
        config.headers['X-CSRF-Token'] = csrf
      }
    }

    return config
  },
  (error: unknown) => Promise.reject(error),
)

/**
 * 重放原请求（带最新 token）。通过 request 实例重新发起，配置中的 Authorization
 * 会被请求拦截器重新注入最新 token。
 *
 * 返回类型取 {@link AxiosResponse} 仅为满足 axios 拦截器 onFulfilled 的类型契约
 * （必须返回 AxiosResponse / Promise<AxiosResponse>）；运行时该 Promise 解析出的实际是
 * 经过响应拦截器"取 data 展开"后的业务数据，由 get/post/put/del 等封装以精确泛型承接。
 */
function replayRequest(config: AxiosRequestConfig): Promise<AxiosResponse> {
  // 触发请求拦截器重新注入最新 token
  return request.request<unknown, AxiosResponse>(config)
}

/**
 * 跳转登录页（清空认证态后）
 * @param redirectPath 登录后回跳路径
 */
async function redirectToLogin(redirectPath: string): Promise<never> {
  try {
    const authStore = await getAuthStore()
    authStore.clearTokens()
  } catch {
    // ignore
  }
  // 防止已在登录页时再次跳转造成死循环
  if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
    window.location.href = `/login?redirect=${encodeURIComponent(redirectPath)}`
  }
  // 返回一个永不 resolve 的 Promise，避免后续逻辑误用 undefined
  return new Promise<never>(() => {}) as Promise<never>
}

/**
 * 跳转网络错误页面（服务不可达 / 网络断开）
 * 使用 replace 避免用户按返回回到出错页面形成循环。
 * 加入防抖：同一页面 3 秒内只跳一次。
 */
let lastErrorRedirectTime = 0
function redirectToErrorPage(statusCode: number, message: string): void {
  if (typeof window === 'undefined') return
  const now = Date.now()
  if (now - lastErrorRedirectTime < 3000) return
  lastErrorRedirectTime = now
  // 避免在错误页面死循环
  if (window.location.pathname === '/error') return
  const params = new URLSearchParams()
  if (statusCode) params.set('status', String(statusCode))
  if (message) params.set('msg', message)
  window.location.replace(`/error?${params.toString()}`)
}

/** 响应拦截器：统一处理 Result<T> + 401 自动刷新重放 */
request.interceptors.response.use(
  async (response: AxiosResponse<ApiResult>) => {
    const result = response.data

    // 非 Result 格式（如文件下载），直接返回
    if (result == null || typeof result.code !== 'number') {
      return response
    }

    // 业务成功
    if (result.code === BIZ_CODE_SUCCESS) {
      // 拦截器已在此处完成"取 data 展开"。result.data 为未知业务类型（unknown），
      // 这里仅以 AxiosResponse 作静态占位以满足 axios 拦截器契约；调用方通过 get<T> 等
      // 封装获得 Promise<T>，因此泛型仍是精确的。
      return result.data as AxiosResponse
    }

    // 业务码 401：尝试刷新 token 并重放原请求
    if (
      result.code === BIZ_CODE_UNAUTHORIZED &&
      response.config &&
      !retriedConfigs.has(response.config)
    ) {
      retriedConfigs.add(response.config)
      try {
        const authStore = await getAuthStore()
        const ok = await authStore.refresh()
        if (ok) {
          return replayRequest(response.config)
        }
        return await redirectToLogin(
          typeof window !== 'undefined' ? window.location.pathname + window.location.search : '/',
        )
      } catch {
        return await redirectToLogin(
          typeof window !== 'undefined' ? window.location.pathname + window.location.search : '/',
        )
      }
    }

    // 其他业务失败：弹窗提示
    ElMessage.error(result.message || '请求失败')
    return Promise.reject(new Error(result.message || `业务错误：${result.code}`))
  },
  async (error: AxiosError<ApiResult>) => {
    // HTTP 错误
    if (error.response) {
      const status = error.response.status
      const statusText = error.response.statusText
      const result = error.response.data
      const message = result?.message || `HTTP ${status} ${statusText}`

      // HTTP 401：未授权，尝试刷新并重放
      if (
        status === 401 &&
        error.config &&
        !retriedConfigs.has(error.config)
      ) {
        retriedConfigs.add(error.config)
        try {
          const authStore = await getAuthStore()
          const ok = await authStore.refresh()
          if (ok) {
            return replayRequest(error.config)
          }
          return await redirectToLogin(
            typeof window !== 'undefined' ? window.location.pathname + window.location.search : '/',
          )
        } catch {
          return await redirectToLogin(
            typeof window !== 'undefined' ? window.location.pathname + window.location.search : '/',
          )
        }
      }

      if (status === 401 || status === 403) {
        ElMessageBox.alert(message, '权限不足', { type: 'warning' })
      } else if (status === 502 || status === 503) {
        // 后端服务不可达，跳转到网络错误页面
        redirectToErrorPage(status, message)
      } else if (status >= 500) {
        ElMessage.error(`服务器错误：${message}`)
      } else {
        ElMessage.error(`请求错误：${message}`)
      }
    } else if (error.code === 'ECONNABORTED') {
      redirectToErrorPage(0, '请求超时，请稍后重试')
    } else if (!error.response) {
      // 网络断开、服务未启动等
      redirectToErrorPage(0, '网络连接异常，请检查网络或稍后重试')
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

/** 封装 GET 请求 */
export function get<T = unknown>(url: string, params?: object, config?: AxiosRequestConfig) {
  return request.get<unknown, T>(url, { ...config, params })
}

/** 封装 POST 请求 */
export function post<T = unknown, R = T>(
  url: string,
  data?: object,
  config?: AxiosRequestConfig,
) {
  return request.post<unknown, R>(url, data, config)
}

/** 封装 PUT 请求 */
export function put<T = unknown, R = T>(
  url: string,
  data?: object,
  config?: AxiosRequestConfig,
) {
  return request.put<unknown, R>(url, data, config)
}

/** 封装 DELETE 请求 */
export function del<T = unknown>(url: string, params?: object, config?: AxiosRequestConfig) {
  return request.delete<unknown, T>(url, { ...config, params })
}

export default request
