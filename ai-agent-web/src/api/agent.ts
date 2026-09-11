/** Agent Runtime API 封装
 *
 * 对应后端 com.ai.api.agent.controller.*
 *
 * - AgentRuntimeController: runtime 生命周期管理
 * - SkillController: Skill 查询与调用
 * - WorkflowController: 工作流查询与执行
 * - TraceController: Trace 回放与性能聚合
 */
import { get, post } from '@/utils/request'
import { fetchEventSource } from '@microsoft/fetch-event-source'

/** AgentRuntime 响应 */
export interface AgentRuntimeResp {
  id: number
  sessionId: number
  agentConfigId: number | null
  modelId: string | null
  userId: number
  status: string // pending|running|paused|done|failed|cancelled
  finalAnswer: string | null
  errorMessage: string | null
  nextStepIndex: number
  lastHeartbeat: string
  ttlSeconds: number
  createTime: string
  updateTime: string
}

/** Skill 元数据响应 */
export interface SkillDefinitionResp {
  id: number
  name: string
  version: string
  type: string // api|local|kb|subagent|mcp
  description: string
  endpoint: string | null
  beanName: string | null
  methodName: string | null
  permissionTag: string | null
  enabled: number
  inputSchema: string | null
  outputSchema: string | null
  createTime: string
  updateTime: string
}

/** Workflow 定义响应 */
export interface WorkflowDefinitionResp {
  id: number
  workflowId: string
  name: string
  version: string
  triggerType: string
  owner: string
  enabled: number
  dag: string // JSON
  createTime: string
  updateTime: string
}

/** Trace 步骤响应 */
export interface AgentTraceResp {
  id: number
  traceId: string
  runtimeId: number
  stepIndex: number
  stepType: string // thought|action|observation|resume|error
  skillId: string | null
  input: string | null // JSON
  output: string | null // JSON
  tokenIn: number | null
  tokenOut: number | null
  latencyMs: number | null
  parentStepId: number | null
  error: string | null
  dlqMessageId: string | null
  createTime: string
}

/** 性能指标响应 */
export interface TraceMetricsResp {
  total_latency_ms: number
  total_token_in: number
  total_token_out: number
  step_count: number
}

/** ============ Runtime ============ */

/** 创建 runtime */
export function createRuntime(params: {
  sessionId?: number
  agentConfigId?: number
  modelId?: string
}) {
  return post<unknown, number>('/agent/runtime', undefined, { params })
}

/** 启动 runtime */
export function startRuntime(id: number) {
  return post(`/agent/runtime/${id}/start`)
}

/** 暂停 runtime */
export function pauseRuntime(id: number) {
  return post(`/agent/runtime/${id}/pause`)
}

/** 恢复 runtime */
export function resumeRuntime(id: number) {
  return post(`/agent/runtime/${id}/resume`)
}

/** 取消 runtime */
export function cancelRuntime(id: number) {
  return post(`/agent/runtime/${id}/cancel`)
}

/** 查询 runtime 详情 */
export function getRuntime(id: number) {
  return get<AgentRuntimeResp>(`/agent/runtime/${id}`)
}

/** ============ Skills ============ */

/** 列出所有已启用的 Skill */
export function listSkills() {
  return get<SkillDefinitionResp[]>('/agent/skills')
}

/** 查询 Skill 最新版本 */
export function getSkill(name: string) {
  return get<SkillDefinitionResp>(`/agent/skills/${name}`)
}

/** 调用 Skill（调试用） */
export function invokeSkill(name: string, version: string, input: Record<string, unknown>) {
  return post<Record<string, unknown>>(`/agent/skills/${name}/invoke`, input, {
    params: { version },
  })
}

/** Skill 健康检查 */
export function skillHealth() {
  return get<Record<string, unknown>>('/agent/skills/health')
}

/** ============ Workflow ============ */

/** 查询工作流详情 */
export function getWorkflow(workflowId: string, version = 'latest') {
  return get<WorkflowDefinitionResp>(`/agent/workflows/${workflowId}`, {
    params: { version },
  })
}

/** 执行工作流 */
export function executeWorkflow(
  workflowId: string,
  input: Record<string, unknown>,
  version = 'latest',
) {
  return post<Record<string, unknown>>(`/agent/workflows/${workflowId}/execute`, input, {
    params: { version },
  })
}

/** ============ Trace ============ */

/** 回放 Trace（按 step_index 升序返回时间轴） */
export function replayTrace(runtimeId: number) {
  return get<AgentTraceResp[]>(`/agent/trace/${runtimeId}/replay`)
}

/** 性能聚合指标 */
export function traceMetrics(runtimeId: number) {
  return get<TraceMetricsResp>(`/agent/trace/${runtimeId}/metrics`)
}

/** 查询子任务 trace */
export function listChildTraces(parentStepId: number) {
  return get<AgentTraceResp[]>(`/agent/trace/parent/${parentStepId}`)
}

/** ============ 实时执行 SSE（SSE 协议 v2） ============ */

/** v2 事件：Agent 推理中间步骤 */
export interface AgentThoughtEvent {
  runtimeId: number
  step: number
  content: string
}

/** v2 事件：Agent 工具调用开始 */
export interface AgentActionEvent {
  runtimeId: number
  skillId: string
  input: unknown
}

/** v2 事件：Agent 工具调用返回 */
export interface AgentObservationEvent {
  runtimeId: number
  step: number
  output: unknown
}

/** v2 事件：执行完成 */
export interface AgentDoneEvent {
  runtimeId: number
  latencyMs: number
}

/** v2 事件：结构化错误 */
export interface AgentErrorEvent {
  code: string
  message: string
}

/**
 * Agent 实时执行 SSE 回调。
 *
 * 对应后端 AgentSseController 推送的 thought/action/observation/done/error 五类事件。
 */
export interface AgentSseCallbacks {
  /** 推理中间步骤 */
  onThought?: (data: AgentThoughtEvent) => void
  /** 工具调用开始 */
  onAction?: (data: AgentActionEvent) => void
  /** 工具调用返回 */
  onObservation?: (data: AgentObservationEvent) => void
  /** 执行完成 */
  onDone?: (data: AgentDoneEvent) => void
  /** 结构化错误 */
  onError?: (data: AgentErrorEvent) => void
  /** 连接关闭 */
  onClose?: () => void
}

/**
 * 订阅某 runtime 的实时执行事件流（SSE 协议 v2）。
 *
 * 使用 @microsoft/fetch-event-source 支持 GET + Authorization 头。
 * 后端先重放最近事件再实时接收，客户端迟到订阅也能拿到执行过程。
 * 返回 AbortController 用于关闭流。
 *
 * @param runtimeId runtime ID
 * @param callbacks 事件回调
 */
export function streamAgentRuntime(
  runtimeId: number,
  callbacks: AgentSseCallbacks,
): AbortController {
  const controller = new AbortController()

  const accessToken = localStorage.getItem('ai_auth_access_token')
  const csrfToken = (() => {
    const key = 'csrf-token='
    const cookies = document.cookie.split('; ')
    for (const c of cookies) {
      if (c.startsWith(key)) {
        try {
          return decodeURIComponent(c.slice(key.length))
        } catch {
          return c.slice(key.length)
        }
      }
    }
    return null
  })()

  const headers: Record<string, string> = {}
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`
  }
  if (csrfToken) {
    headers['X-CSRF-Token'] = csrfToken
  }

  fetchEventSource(`/api/v1/agent/runtime/${runtimeId}/stream`, {
    method: 'GET',
    headers,
    signal: controller.signal,
    openWhenHidden: true,
    onmessage(event) {
      let data: Record<string, unknown> = {}
      try {
        data = JSON.parse(event.data)
      } catch {
        return
      }
      switch (event.event) {
        case 'thought':
          callbacks.onThought?.(data as unknown as AgentThoughtEvent)
          break
        case 'action':
          callbacks.onAction?.(data as unknown as AgentActionEvent)
          break
        case 'observation':
          callbacks.onObservation?.(data as unknown as AgentObservationEvent)
          break
        case 'done':
          callbacks.onDone?.(data as unknown as AgentDoneEvent)
          break
        case 'error':
          callbacks.onError?.(data as unknown as AgentErrorEvent)
          break
      }
    },
    onerror(err) {
      callbacks.onError?.({
        code: 'SSE_DISCONNECTED',
        message: err instanceof Error ? err.message : '连接失败',
      })
      // 不自动重连
      throw err
    },
    onclose() {
      callbacks.onClose?.()
    },
  })

  return controller
}
