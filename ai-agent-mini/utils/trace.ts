/**
 * TraceId 生成工具
 *
 * 与 Web 端 generateTraceId() 逻辑一致：32 位无连字符 UUID。
 * 用于请求头 X-Trace-Id 注入，便于全链路追踪。
 */

/** 生成前端 TraceId（32 位无连字符 UUID） */
export function generateTraceId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }
  // 降级方案：时间戳 + 随机字符
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 18)
}
