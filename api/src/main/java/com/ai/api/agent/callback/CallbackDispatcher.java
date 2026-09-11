package com.ai.api.agent.callback;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 统一回调派发器（add-agent-runner-pipeline）。
 *
 * <p>聚合所有 {@link CallbackHandler}，按事件类型广播。单个处理器异常不影响主流程（隔离兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackDispatcher {

  private final List<CallbackHandler> handlers;

  /** 派发回调事件到所有已注册处理器。 */
  public void dispatch(SkillCallbackContext ctx) {
    for (CallbackHandler handler : handlers) {
      try {
        handler.onEvent(ctx);
      } catch (Exception e) {
        log.warn(
            "[CallbackDispatcher][dispatch][handler({}) event({}) 处理失败]",
            handler.getClass().getSimpleName(),
            ctx.getEvent(),
            e);
      }
    }
  }
}
