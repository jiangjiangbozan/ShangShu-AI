package com.ai.api.agent.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Agent 执行上下文（add-agent-runtime）。
 *
 * <p>统一管理三类上下文：
 *
 * <ul>
 *   <li>chatHistory：用户与 Agent 的对话消息列表
 *   <li>observations：所有 Skill 调用结果，按 skillCallId 索引
 *   <li>thoughtChain：LLM 推理中间态，按 step_index 顺序
 * </ul>
 *
 * <p>序列化为 JSON 后落 {@code agent_runtime.context_snapshot} 字段，用于断点续跑。
 *
 * @author add-agent-runtime
 */
@Data
public class AgentContext {

  /** 用户与 Agent 的对话消息（Spring AI Message 序列化形式）。 */
  private List<Map<String, Object>> chatHistory = new ArrayList<>();

  /** Skill 调用结果池，key = stepIndex 或 skillCallId。 */
  private Map<String, Object> observations = new LinkedHashMap<>();

  /** LLM 推理中间态（thought 输出）。 */
  private List<Map<String, Object>> thoughtChain = new ArrayList<>();

  /** 续跑起始 step。 */
  private int nextStepIndex = 0;

  /** 用户原始 query。 */
  private String userQuery;

  /** 关联 runtimeId。 */
  private Long runtimeId;

  /** 快照代次（单调递增，Redis/MySQL 双写恢复时取大者自愈，add-snapshot-consistency）。 */
  private long generation = 0;
}
