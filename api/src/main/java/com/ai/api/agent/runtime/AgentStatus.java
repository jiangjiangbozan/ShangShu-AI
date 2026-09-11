package com.ai.api.agent.runtime;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Agent Runtime 状态枚举（add-agent-runtime）。
 *
 * <p>合法状态转移表（from → to）：
 *
 * <ul>
 *   <li>PENDING → RUNNING
 *   <li>RUNNING → PAUSED / DONE / FAILED / CANCELLED
 *   <li>PAUSED → RUNNING / CANCELLED
 *   <li>DONE / FAILED / CANCELLED → （终态，不可再转移）
 * </ul>
 *
 * @author add-agent-runtime
 */
public enum AgentStatus {
  PENDING,
  RUNNING,
  PAUSED,
  DONE,
  FAILED,
  CANCELLED;

  private static final Map<AgentStatus, Set<AgentStatus>> TRANSITIONS =
      Map.of(
          PENDING, Set.of(RUNNING, CANCELLED),
          RUNNING, Set.of(PAUSED, DONE, FAILED, CANCELLED),
          PAUSED, Set.of(RUNNING, CANCELLED),
          DONE, Collections.emptySet(),
          FAILED, Collections.emptySet(),
          CANCELLED, Collections.emptySet());

  /**
   * 校验状态转移是否合法。
   *
   * @param to 目标状态
   * @return true 表示合法
   */
  public boolean canTransitionTo(AgentStatus to) {
    return TRANSITIONS.getOrDefault(this, EnumSet.noneOf(AgentStatus.class)).contains(to);
  }
}
