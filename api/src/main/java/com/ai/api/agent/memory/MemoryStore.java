package com.ai.api.agent.memory;

import java.util.List;
import java.util.Map;

/**
 * Agent 记忆存储接口（add-agent-runtime）。
 *
 * <p>统一抽象短期记忆 / 长期记忆 / 向量记忆的访问。M4 仅实现 {@link ShortTermMemory}，V2 扩展：
 *
 * <ul>
 *   <li>{@code RedisMemoryStore}：长期跨会话记忆
 *   <li>{@code VectorMemoryStore}：向量检索记忆
 * </ul>
 *
 * @author add-agent-runtime
 */
public interface MemoryStore {

  /**
   * 追加一条消息到记忆。
   *
   * @param runtimeId runtime ID
   * @param role 角色（user / assistant / tool / system）
   * @param content 内容
   * @param metadata 元数据（如 skill_name / step_index / token_count）
   */
  void append(Long runtimeId, String role, String content, Map<String, Object> metadata);

  /**
   * 获取当前 runtime 的全部有效记忆（已应用窗口 + 压缩策略）。
   *
   * @param runtimeId runtime ID
   * @return 记忆消息列表（按时间顺序）
   */
  List<Map<String, Object>> retrieve(Long runtimeId);

  /**
   * 获取当前 token 占用估算。
   *
   * @param runtimeId runtime ID
   * @return token 数
   */
  int tokenUsage(Long runtimeId);

  /** 清空 runtime 记忆。 */
  void clear(Long runtimeId);
}
