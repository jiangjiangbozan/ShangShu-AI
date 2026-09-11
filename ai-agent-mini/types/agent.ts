/** 任务 Agent 类型定义 */

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

/** 创建 runtime 响应 */
export interface CreateRuntimeResp {
  runtimeId?: number
}
