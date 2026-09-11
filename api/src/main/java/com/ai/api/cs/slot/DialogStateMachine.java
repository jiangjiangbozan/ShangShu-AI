package com.ai.api.cs.slot;

import com.ai.api.cs.config.CustomerServiceProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 客服对话状态机（add-customer-service）。
 *
 * <p>4 状态：COLLECTING → CONFIRMING → EXECUTING → DONE。
 *
 * <ul>
 *   <li>COLLECTING：槽位收集中
 *   <li>CONFIRMING：槽位已完整，等待用户确认
 *   <li>EXECUTING：用户已确认，调用业务 Skill
 *   <li>DONE：业务 Skill 执行完成
 * </ul>
 *
 * <p>{@code clarify_rounds} 超过 {@code clarify-max-rounds} 强制 fallback。
 *
 * @author add-customer-service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DialogStateMachine {

  private final CustomerServiceProperties properties;

  public static final String COLLECTING = "collecting";
  public static final String CONFIRMING = "confirming";
  public static final String EXECUTING = "executing";
  public static final String DONE = "done";

  /**
   * 计算下一状态。
   *
   * @param currentState 当前状态
   * @param isComplete 槽位是否已完整
   * @param userConfirmed 用户是否已确认（CONFIRMING → EXECUTING 的触发条件）
   * @param skillDone 业务 Skill 是否执行完成
   * @param clarifyRounds 已澄清轮数
   * @return 下一状态；{@code null} 表示保持原状态
   */
  public String next(
      String currentState,
      boolean isComplete,
      boolean userConfirmed,
      boolean skillDone,
      int clarifyRounds) {
    switch (currentState) {
      case COLLECTING:
        if (clarifyRounds >= properties.getClarifyMaxRounds()) {
          log.info(
              "[DialogStateMachine][clarify_rounds({}) exceed max({}) → fallback]",
              clarifyRounds,
              properties.getClarifyMaxRounds());
          return CONFIRMING; // 强制进入确认（实际触发 fallback）
        }
        return isComplete ? CONFIRMING : COLLECTING;
      case CONFIRMING:
        return userConfirmed ? EXECUTING : COLLECTING;
      case EXECUTING:
        return skillDone ? DONE : EXECUTING;
      case DONE:
        return DONE;
      default:
        return COLLECTING;
    }
  }

  /** 判断是否需要触发 fallback 意图。 */
  public boolean shouldFallback(int clarifyRounds) {
    return clarifyRounds >= properties.getClarifyMaxRounds();
  }

  /** 获取所有合法状态列表。 */
  public List<String> validStates() {
    return List.of(COLLECTING, CONFIRMING, EXECUTING, DONE);
  }

  /** 校验状态是否合法。 */
  public boolean isValid(String state) {
    return validStates().contains(state);
  }
}
