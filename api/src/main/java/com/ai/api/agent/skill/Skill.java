package com.ai.api.agent.skill;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Skill 注解（add-agent-runtime）。
 *
 * <p>标注在 Spring Bean 方法上，启动时由 {@code SkillRegistry} 扫描注册到 {@code skill_definition} 表。
 *
 * <p>示例：
 *
 * <pre>{@code
 * @Component
 * public class CalculatorSkill {
 *   @Skill(name = "calculator", type = SkillType.LOCAL, description = "四则运算")
 *   public Map<String, Object> eval(@RequestBody Map<String, Object> input) { ... }
 * }
 * }</pre>
 *
 * @author add-agent-runtime
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Skill {

  /** 技能名（kebab-case，UNIQUE(name, version) 约束组成部分）。 */
  String name();

  /** 语义版本号，默认 {@code 1.0.0}。 */
  String version() default "1.0.0";

  /** Skill 类型。 */
  SkillType type();

  /** 权限标签（M6 RBAC 校验使用），默认空字符串。 */
  String permission() default "";

  /** 技能描述（供 LLM 工具选择 prompt 使用）。 */
  String description() default "";
}
