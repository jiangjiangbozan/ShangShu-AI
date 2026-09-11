/**
 * 任务 Agent API 封装
 *
 * 对标 Web 端 src/api/agent.ts，接口路径完全一致。
 * 对应后端 com.ai.api.agent.controller.*。
 */
import { get, post } from '@/utils/request'
import type {
  AgentRuntimeResp,
  AgentTraceResp,
  CreateRuntimeResp,
  SkillDefinitionResp,
} from '@/types/agent'

/** 创建 runtime（无参数，默认模型） */
export function createRuntime() {
  return post<CreateRuntimeResp>('/agent/runtime', {})
}

/** 启动 runtime */
export function startRuntime(id: number) {
  return post<void>(`/agent/runtime/${id}/start`)
}

/** 暂停 runtime */
export function pauseRuntime(id: number) {
  return post<void>(`/agent/runtime/${id}/pause`)
}

/** 恢复 runtime */
export function resumeRuntime(id: number) {
  return post<void>(`/agent/runtime/${id}/resume`)
}

/** 取消 runtime */
export function cancelRuntime(id: number) {
  return post<void>(`/agent/runtime/${id}/cancel`)
}

/** 查询 runtime 详情 */
export function getRuntime(id: number) {
  return get<AgentRuntimeResp>(`/agent/runtime/${id}`)
}

/** 列出所有已启用的 Skill */
export function listSkills() {
  return get<SkillDefinitionResp[]>('/agent/skills')
}

/** 调用 Skill（调试用） */
export function invokeSkill(name: string, version: string, input: Record<string, unknown>) {
  return post<Record<string, unknown>>(`/agent/skills/${name}/invoke?version=${version}`, input)
}

/** 回放 Trace（按 step_index 升序返回时间轴） */
export function replayTrace(runtimeId: number) {
  return get<AgentTraceResp[]>(`/agent/trace/${runtimeId}/replay`)
}
