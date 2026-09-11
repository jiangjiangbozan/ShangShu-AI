package com.ai.api.agent.pause;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 暂停状态服务（add-agent-runner-pipeline）。
 *
 * <p>管理 {@link PauseState} 生命周期：创建（NORMAL / ASK_HUMAN）→ 解析（人工回复 / 恢复）→ 取消。 同一 runtime
 * 仅允许一个活跃（WAITING）暂停点，重复创建抛 {@link ErrorCode#AGENT_PAUSE_ALREADY_EXISTS}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PauseStateService {

  private final PauseStateMapper pauseStateMapper;

  /**
   * 创建暂停状态（自动关闭旧的 WAITING 记录，保证单活跃暂停点）。
   *
   * @param runtimeId runtime ID
   * @param nextStepIndex 下一步执行序号
   * @param pauseType NORMAL / ASK_HUMAN
   * @param question ask-human 问题（可空）
   * @param variables 待确认变量（可空）
   * @return 新建的 PauseState
   */
  public PauseState create(
      Long runtimeId,
      Integer nextStepIndex,
      String pauseType,
      String question,
      Map<String, Object> variables) {
    cancelActive(runtimeId);
    PauseState state = new PauseState();
    state.setRuntimeId(runtimeId);
    state.setNextStepIndex(nextStepIndex);
    state.setPauseType(pauseType);
    state.setQuestion(question);
    state.setVariables(variables);
    state.setStatus(PauseState.STATUS_WAITING);
    state.setCreateTime(LocalDateTime.now());
    state.setUpdateTime(LocalDateTime.now());
    pauseStateMapper.insert(state);
    log.info(
        "[PauseStateService][create][runtimeId({}) pauseType({}) nextStepIndex({})]",
        runtimeId,
        pauseType,
        nextStepIndex);
    return state;
  }

  /** 查询当前活跃（WAITING）暂停状态；不存在返回 null。 */
  public PauseState findActive(Long runtimeId) {
    return pauseStateMapper.selectActive(runtimeId);
  }

  /** 判断 runtime 是否处于暂停挂起状态（存在活跃 PauseState）。 */
  public boolean isPaused(Long runtimeId) {
    return findActive(runtimeId) != null;
  }

  /**
   * 解析暂停：写入人工回复，标记 RESOLVED。
   *
   * @param runtimeId runtime ID
   * @param userInput 人工回复内容
   * @return 被解析的 PauseState（不存在抛 {@link ErrorCode#AGENT_PAUSE_NOT_FOUND}）
   */
  public PauseState resolve(Long runtimeId, String userInput) {
    PauseState active = findActive(runtimeId);
    if (active == null) {
      throw new BusinessException(ErrorCode.AGENT_PAUSE_NOT_FOUND, "no active pause: " + runtimeId);
    }
    active.setReply(userInput);
    active.setStatus(PauseState.STATUS_RESOLVED);
    active.setUpdateTime(LocalDateTime.now());
    pauseStateMapper.updateById(active);
    log.info(
        "[PauseStateService][resolve][runtimeId({}) pauseType({}) reply({})]",
        runtimeId,
        active.getPauseType(),
        userInput);
    return active;
  }

  /** 取消当前活跃暂停状态（幂等，不存在不报错）。 */
  public void cancel(Long runtimeId) {
    PauseState active = findActive(runtimeId);
    if (active == null) {
      return;
    }
    active.setStatus(PauseState.STATUS_CANCELLED);
    active.setUpdateTime(LocalDateTime.now());
    pauseStateMapper.updateById(active);
    log.info("[PauseStateService][cancel][runtimeId({})]", runtimeId);
  }

  private void cancelActive(Long runtimeId) {
    PauseState active = findActive(runtimeId);
    if (active != null) {
      active.setStatus(PauseState.STATUS_CANCELLED);
      active.setUpdateTime(LocalDateTime.now());
      pauseStateMapper.updateById(active);
    }
  }
}
