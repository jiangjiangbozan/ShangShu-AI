package com.ai.api.agent.skill.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CalculatorSkill} 单元测试（add-agent-runtime）。
 *
 * @author add-agent-runtime
 */
@DisplayName("Calculator Skill 单元测试")
class CalculatorSkillTest {

  private final CalculatorSkill skill = new CalculatorSkill();

  @Test
  @DisplayName("简单加法 1+2=3")
  void add_shouldReturn3() {
    Map<String, Object> result = skill.eval(Map.of("expr", "1+2"));
    assertThat(result).containsEntry("result", 3.0);
  }

  @Test
  @DisplayName("乘除混合 2*3+4=10")
  void mixedArithmetic_shouldReturn10() {
    Map<String, Object> result = skill.eval(Map.of("expr", "2*3+4"));
    assertThat(result).containsEntry("result", 10.0);
  }

  @Test
  @DisplayName("优先级（括号） (1+2)*3=9")
  void parentheses_shouldReturn9() {
    Map<String, Object> result = skill.eval(Map.of("expr", "(1+2)*3"));
    assertThat(result).containsEntry("result", 9.0);
  }

  @Test
  @DisplayName("除法 10/4=2.5")
  void division_shouldReturn2_5() {
    Map<String, Object> result = skill.eval(Map.of("expr", "10/4"));
    assertThat(result).containsEntry("result", 2.5);
  }

  @Test
  @DisplayName("负数 -3+5=2")
  void negative_shouldReturn2() {
    Map<String, Object> result = skill.eval(Map.of("expr", "-3+5"));
    assertThat(result).containsEntry("result", 2.0);
  }

  @Test
  @DisplayName("复杂表达式 1+2*3-4/2=5")
  void complexExpr_shouldReturn5() {
    Map<String, Object> result = skill.eval(Map.of("expr", "1+2*3-4/2"));
    assertThat(result).containsEntry("result", 5.0);
  }

  @Test
  @DisplayName("除零应返回 error")
  void divideByZero_shouldReturnError() {
    Map<String, Object> result = skill.eval(Map.of("expr", "1/0"));
    assertThat(result).containsKey("error");
  }

  @Test
  @DisplayName("非法字符应返回 error")
  void invalidChar_shouldReturnError() {
    Map<String, Object> result = skill.eval(Map.of("expr", "1+abc"));
    assertThat(result).containsKey("error");
  }

  @Test
  @DisplayName("缺少括号闭合应返回 error")
  void missingParen_shouldReturnError() {
    Map<String, Object> result = skill.eval(Map.of("expr", "(1+2"));
    assertThat(result).containsKey("error");
  }

  @Test
  @DisplayName("缺少 expr 参数应返回 error")
  void missingExpr_shouldReturnError() {
    Map<String, Object> result = skill.eval(Map.of());
    assertThat(result).containsKey("error");
  }

  @Test
  @DisplayName("带空格的表达式 1 + 2 * 3 = 7")
  void withSpaces_shouldReturn7() {
    Map<String, Object> result = skill.eval(Map.of("expr", "1 + 2 * 3"));
    assertThat(result).containsEntry("result", 7.0);
  }
}
