/**
 * 后端统一响应格式
 *
 * 与 Web 端 ApiResult 保持一致。
 * 后端所有接口返回 Result<T> 包装，前端请求层负责剥壳取 data。
 */
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
