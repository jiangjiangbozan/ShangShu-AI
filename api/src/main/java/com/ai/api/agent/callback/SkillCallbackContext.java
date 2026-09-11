package com.ai.api.agent.callback;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * 回调上下文（add-agent-runner-pipeline）。
 *
 * <p>承载单次 Skill 调用的全量信息：入参、出参、耗时、异常，供回调处理器消费。 {@code runtimeId} / {@code stepIndex} 可空（非 Agent
 * 编排场景调用时为空）。
 */
@Data
@Builder
public class SkillCallbackContext {

  /** 回调事件类型。 */
  private CallbackEvent event;

  /** 关联 runtimeId（可空）。 */
  private Long runtimeId;

  /** 当前 step 序号（可空）。 */
  private Integer stepIndex;

  /** Skill 名。 */
  private String skillName;

  /** Skill 版本。 */
  private String skillVersion;

  /** Skill 入参。 */
  private Map<String, Object> input;

  /** Skill 出参（AFTER 事件）。 */
  private Map<String, Object> output;

  /** 单次调用耗时（毫秒）。 */
  private long latencyMs;

  /** 异常信息（ERROR 事件）。 */
  private String error;
}
