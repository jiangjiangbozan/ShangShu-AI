package com.ai.api.agent.callback;

/**
 * 回调事件类型（add-agent-runner-pipeline）。
 *
 * <ul>
 *   <li>{@link #SKILL_BEFORE_INVOKE}：Skill 调用前（入参埋点 / 审计）
 *   <li>{@link #SKILL_AFTER_INVOKE}：Skill 调用成功返回后（出参埋点 / 耗时上报）
 *   <li>{@link #SKILL_ERROR}：Skill 调用异常（异常信息上报 / 告警）
 * </ul>
 */
public enum CallbackEvent {
  SKILL_BEFORE_INVOKE,
  SKILL_AFTER_INVOKE,
  SKILL_ERROR
}
