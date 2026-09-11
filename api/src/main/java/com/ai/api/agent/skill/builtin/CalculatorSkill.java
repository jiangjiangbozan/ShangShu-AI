package com.ai.api.agent.skill.builtin;

import com.ai.api.agent.skill.Skill;
import com.ai.api.agent.skill.SkillType;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 计算器 Skill（add-agent-runtime）。
 *
 * <p>支持四则运算（含括号），用自实现的递归下降解析器，避免引入脚本引擎的安全风险。
 *
 * <p>示例输入：{@code {"expr":"1+2*3"}} 输出：{@code {"result":7.0}}
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component("calculatorSkill")
public class CalculatorSkill {

  @Skill(name = "calculator", type = SkillType.LOCAL, description = "四则运算（支持 + - * / 与括号）")
  public Map<String, Object> eval(Map<String, Object> input) {
    Object exprObj = input.get("expr");
    if (exprObj == null) {
      Map<String, Object> err = new HashMap<>();
      err.put("error", "expr is required");
      return err;
    }
    String expr = exprObj.toString().trim();
    try {
      double result = new Parser(expr).parse();
      Map<String, Object> ret = new HashMap<>();
      ret.put("result", result);
      return ret;
    } catch (Exception e) {
      log.warn("[CalculatorSkill][eval][expr({}) 解析失败: {}]", expr, e.getMessage());
      Map<String, Object> err = new HashMap<>();
      err.put("error", "invalid expression: " + e.getMessage());
      return err;
    }
  }

  /** 递归下降解析器（仅支持 + - * / 与括号、数字）。 */
  static class Parser {
    private final String s;
    private int i = 0;

    Parser(String s) {
      this.s = s.replaceAll("\\s+", "");
    }

    double parse() {
      double v = expr();
      if (i < s.length())
        throw new RuntimeException("unexpected char at " + i + ": " + s.charAt(i));
      return v;
    }

    private double expr() {
      double v = term();
      while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
        char op = s.charAt(i++);
        double r = term();
        v = op == '+' ? v + r : v - r;
      }
      return v;
    }

    private double term() {
      double v = factor();
      while (i < s.length() && (s.charAt(i) == '*' || s.charAt(i) == '/')) {
        char op = s.charAt(i++);
        double r = factor();
        if (op == '/' && r == 0) throw new RuntimeException("divide by zero");
        v = op == '*' ? v * r : v / r;
      }
      return v;
    }

    private double factor() {
      if (i < s.length() && s.charAt(i) == '(') {
        i++;
        double v = expr();
        if (i >= s.length() || s.charAt(i) != ')') throw new RuntimeException("missing )");
        i++;
        return v;
      }
      if (i < s.length() && s.charAt(i) == '-') {
        i++;
        return -factor();
      }
      int start = i;
      while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
      if (start == i) throw new RuntimeException("expected number at " + i);
      return Double.parseDouble(s.substring(start, i));
    }
  }
}
