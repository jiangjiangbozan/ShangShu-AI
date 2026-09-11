package com.ai.api.cs.slot;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.api.cs.config.CustomerServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DialogStateMachine} 单元测试（add-customer-service）。
 *
 * @author add-customer-service
 */
@DisplayName("DialogStateMachine 单元测试")
class DialogStateMachineTest {

  private CustomerServiceProperties properties;
  private DialogStateMachine machine;

  @BeforeEach
  void setup() {
    properties = new CustomerServiceProperties();
    properties.setClarifyMaxRounds(3);
    machine = new DialogStateMachine(properties);
  }

  @Test
  @DisplayName("COLLECTING + isComplete=true → CONFIRMING")
  void collecting_complete_toConfirming() {
    String next = machine.next(DialogStateMachine.COLLECTING, true, false, false, 0);
    assertThat(next).isEqualTo(DialogStateMachine.CONFIRMING);
  }

  @Test
  @DisplayName("COLLECTING + isComplete=false → COLLECTING")
  void collecting_incomplete_stayCollecting() {
    String next = machine.next(DialogStateMachine.COLLECTING, false, false, false, 1);
    assertThat(next).isEqualTo(DialogStateMachine.COLLECTING);
  }

  @Test
  @DisplayName("COLLECTING + clarifyRounds >= max → CONFIRMING（强制进入，触发 fallback）")
  void collecting_exceedMaxRounds_toConfirming() {
    String next = machine.next(DialogStateMachine.COLLECTING, false, false, false, 3);
    assertThat(next).isEqualTo(DialogStateMachine.CONFIRMING);
  }

  @Test
  @DisplayName("CONFIRMING + userConfirmed=true → EXECUTING")
  void confirming_confirmed_toExecuting() {
    String next = machine.next(DialogStateMachine.CONFIRMING, true, true, false, 0);
    assertThat(next).isEqualTo(DialogStateMachine.EXECUTING);
  }

  @Test
  @DisplayName("CONFIRMING + userConfirmed=false → COLLECTING（返回补充信息）")
  void confirming_notConfirmed_toCollecting() {
    String next = machine.next(DialogStateMachine.CONFIRMING, true, false, false, 0);
    assertThat(next).isEqualTo(DialogStateMachine.COLLECTING);
  }

  @Test
  @DisplayName("EXECUTING + skillDone=true → DONE")
  void executing_done_toDone() {
    String next = machine.next(DialogStateMachine.EXECUTING, true, true, true, 0);
    assertThat(next).isEqualTo(DialogStateMachine.DONE);
  }

  @Test
  @DisplayName("EXECUTING + skillDone=false → EXECUTING（保持）")
  void executing_notDone_stayExecuting() {
    String next = machine.next(DialogStateMachine.EXECUTING, true, true, false, 0);
    assertThat(next).isEqualTo(DialogStateMachine.EXECUTING);
  }

  @Test
  @DisplayName("DONE 保持 DONE")
  void done_stayDone() {
    String next = machine.next(DialogStateMachine.DONE, true, true, true, 0);
    assertThat(next).isEqualTo(DialogStateMachine.DONE);
  }

  @Test
  @DisplayName("shouldFallback：clarifyRounds >= max → true")
  void shouldFallback_exceedMax_true() {
    assertThat(machine.shouldFallback(3)).isTrue();
    assertThat(machine.shouldFallback(4)).isTrue();
  }

  @Test
  @DisplayName("shouldFallback：clarifyRounds < max → false")
  void shouldFallback_belowMax_false() {
    assertThat(machine.shouldFallback(0)).isFalse();
    assertThat(machine.shouldFallback(2)).isFalse();
  }

  @Test
  @DisplayName("isValid：4 状态合法")
  void isValid_validStates() {
    assertThat(machine.isValid(DialogStateMachine.COLLECTING)).isTrue();
    assertThat(machine.isValid(DialogStateMachine.CONFIRMING)).isTrue();
    assertThat(machine.isValid(DialogStateMachine.EXECUTING)).isTrue();
    assertThat(machine.isValid(DialogStateMachine.DONE)).isTrue();
    assertThat(machine.isValid("unknown")).isFalse();
  }
}
