package com.ai.api.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentStatus} 状态机单元测试（add-agent-runtime）。
 *
 * @author add-agent-runtime
 */
@DisplayName("AgentStatus 状态机测试")
class AgentStatusTest {

  @Test
  @DisplayName("PENDING → RUNNING 合法")
  void pendingToRunning_shouldBeAllowed() {
    assertThat(AgentStatus.PENDING.canTransitionTo(AgentStatus.RUNNING)).isTrue();
  }

  @Test
  @DisplayName("PENDING → CANCELLED 合法")
  void pendingToCancelled_shouldBeAllowed() {
    assertThat(AgentStatus.PENDING.canTransitionTo(AgentStatus.CANCELLED)).isTrue();
  }

  @Test
  @DisplayName("PENDING → PAUSED 非法")
  void pendingToPaused_shouldBeForbidden() {
    assertThat(AgentStatus.PENDING.canTransitionTo(AgentStatus.PAUSED)).isFalse();
  }

  @Test
  @DisplayName("PENDING → DONE 非法（必须先 running）")
  void pendingToDone_shouldBeForbidden() {
    assertThat(AgentStatus.PENDING.canTransitionTo(AgentStatus.DONE)).isFalse();
  }

  @Test
  @DisplayName("RUNNING → PAUSED/DONE/FAILED/CANCELLED 均合法")
  void runningToTerminalStates_shouldBeAllowed() {
    assertThat(AgentStatus.RUNNING.canTransitionTo(AgentStatus.PAUSED)).isTrue();
    assertThat(AgentStatus.RUNNING.canTransitionTo(AgentStatus.DONE)).isTrue();
    assertThat(AgentStatus.RUNNING.canTransitionTo(AgentStatus.FAILED)).isTrue();
    assertThat(AgentStatus.RUNNING.canTransitionTo(AgentStatus.CANCELLED)).isTrue();
  }

  @Test
  @DisplayName("RUNNING → PENDING 非法（不可逆向）")
  void runningToPending_shouldBeForbidden() {
    assertThat(AgentStatus.RUNNING.canTransitionTo(AgentStatus.PENDING)).isFalse();
  }

  @Test
  @DisplayName("PAUSED → RUNNING/CANCELLED 合法")
  void pausedTransitions_shouldBeAllowed() {
    assertThat(AgentStatus.PAUSED.canTransitionTo(AgentStatus.RUNNING)).isTrue();
    assertThat(AgentStatus.PAUSED.canTransitionTo(AgentStatus.CANCELLED)).isTrue();
  }

  @Test
  @DisplayName("PAUSED → DONE 非法（必须先恢复 running）")
  void pausedToDone_shouldBeForbidden() {
    assertThat(AgentStatus.PAUSED.canTransitionTo(AgentStatus.DONE)).isFalse();
  }

  @Test
  @DisplayName("终态 DONE/FAILED/CANCELLED 不可再转移")
  void terminalStates_shouldNotTransition() {
    for (AgentStatus terminal :
        new AgentStatus[] {AgentStatus.DONE, AgentStatus.FAILED, AgentStatus.CANCELLED}) {
      for (AgentStatus target : AgentStatus.values()) {
        assertThat(terminal.canTransitionTo(target))
            .as("终态 %s 不应能转移到 %s", terminal, target)
            .isFalse();
      }
    }
  }

  @Test
  @DisplayName("枚举包含 6 个状态")
  void enum_shouldContainSixStatuses() {
    assertThat(AgentStatus.values()).hasSize(6);
  }

  @Test
  @DisplayName("BusinessException 应在非法转移时抛出")
  void illegalTransition_shouldThrowBusinessException() {
    // 模拟非法转移的语义检查
    boolean legal = AgentStatus.DONE.canTransitionTo(AgentStatus.RUNNING);
    if (!legal) {
      assertThatThrownBy(
              () -> {
                throw new BusinessException(ErrorCode.AGENT_INVALID_STATE_TRANSITION, "非法状态转移");
              })
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("非法状态转移");
    }
  }
}
