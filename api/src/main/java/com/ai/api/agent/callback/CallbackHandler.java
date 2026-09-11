package com.ai.api.agent.callback;

/**
 * 自定义回调处理器抽象（add-agent-runner-pipeline）。
 *
 * <p>外部可注册自定义实现，扩展告警、监控上报、审计等能力。 处理器通过 Spring 依赖注入收集，由 {@link CallbackDispatcher} 统一派发。
 */
public interface CallbackHandler {

  /** 处理一次回调事件。实现需容忍任何异常（不向调用方传播）。 */
  void onEvent(SkillCallbackContext ctx);
}
