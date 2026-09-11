package com.ai.api.agent.skill;

/**
 * Skill 类型枚举（add-agent-runtime）。
 *
 * @author add-agent-runtime
 */
public enum SkillType {
  /** HTTP/gRPC 调用外部 API。 */
  API,
  /** 本地方法路由 internal://Bean#method。 */
  LOCAL,
  /** RAG 检索（复用 RagRetrievalService）。 */
  KB,
  /** 子 Agent 调用（M5 完善，本 change 仅占位）。 */
  SUBAGENT,
  /** MCP 协议外部工具（接口预留，本 change 不实现）。 */
  MCP
}
