/**
 * 工作流 API
 *
 * 注意：响应拦截器已自动解包 Result<T>.data，因此 get/post/put/del
 * 的泛型参数直接写业务数据类型，不需要再套 ApiResult<>。
 */
import request, { get, post, put, del } from '@/utils/request'

/** 工作流列表项（对齐后端 WfWorkflow 实体） */
export interface WorkflowListItem {
  id: number
  workflowId: string
  name: string
  description: string
  version: number
  published: number
  ownerId: number | null
  createdAt: string
  updatedAt: string
  /** DSL JSON 字符串 */
  graph: string
}

/** 获取工作流列表 */
export function listWorkflows() {
  return get<WorkflowListItem[]>('/workflows')
}

/** 获取工作流详情 */
export function getWorkflow(workflowId: string) {
  return get<WorkflowListItem>(`/workflows/${workflowId}`)
}

/** 创建工作流 */
export function createWorkflow(data: { name?: string; description?: string; graph?: string }) {
  return post<WorkflowListItem>('/workflows', data)
}

/** 更新工作流草稿 */
export function updateWorkflow(workflowId: string, data: { name?: string; description?: string; graph?: string }) {
  return put<WorkflowListItem>(`/workflows/${workflowId}`, data)
}

/** 发布工作流 */
export function publishWorkflow(workflowId: string) {
  return post<WorkflowListItem>(`/workflows/${workflowId}/publish`)
}

/** 删除工作流 */
export function deleteWorkflow(workflowId: string) {
  return del<void>(`/workflows/${workflowId}`)
}

/** 复制工作流 */
export function duplicateWorkflow(workflowId: string) {
  return post<WorkflowListItem>(`/workflows/${workflowId}/duplicate`)
}

/** 运行工作流（SSE 返回事件流） — 使用 fetch + ReadableStream */
export function runWorkflowSSE(
  workflowId: string,
  inputs: Record<string, unknown>,
  onEvent: (event: WorkflowEvent) => void,
  onError?: (error: Error) => void,
): AbortController {
  const controller = new AbortController()
  const baseURL = (request.defaults as any).baseURL || ''
  const token = localStorage.getItem('token') || ''

  fetch(`${baseURL}/workflows/${workflowId}/run`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : '',
    },
    body: JSON.stringify({ inputs }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      if (!response.body) return
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        let currentEvent = ''
        let currentData = ''
        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            currentData = line.slice(5).trim()
          } else if (line === '' && currentData) {
            try {
              const parsed = JSON.parse(currentData)
              onEvent(parsed as WorkflowEvent)
            } catch {
              // ignore parse errors
            }
            currentEvent = ''
            currentData = ''
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError' && onError) {
        onError(err)
      }
    })

  return controller
}

/** 运行历史 */
export function listWorkflowRuns(workflowId: string) {
  return get<WorkflowRun[]>(`/workflows/${workflowId}/runs`)
}

/** 工作流运行事件（SSE） */
export interface WorkflowEvent {
  eventType: string
  runId: string
  nodeId: string | null
  nodeType: string | null
  title: string | null
  data: Record<string, unknown>
  timestamp: number
}

/** 工作流运行记录 */
export interface WorkflowRun {
  id: number
  runId: string
  workflowId: string
  version: number
  status: string
  inputs: string | null
  outputs: string | null
  totalTokens: number
  elapsedMs: number
  triggeredBy: string
  createdAt: string
  finishedAt: string | null
  errorMessage: string | null
}
