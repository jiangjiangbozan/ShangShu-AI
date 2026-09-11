package com.ai.api.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解（A6）。标注在 Controller 方法上，由切面自动记录操作日志到 sys_operation_log。
 *
 * <p>用法：{@code @OperationLog(module = "model-config", action = "update")}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

  /** 模块（如 model-config / knowledge / agent） */
  String module();

  /** 动作（如 create / update / delete / query） */
  String action();
}
