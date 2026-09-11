package com.ai.api.agent.controller;

import com.ai.api.agent.entity.AgentRuntime;
import com.ai.api.agent.runtime.AgentEventBus;
import com.ai.api.agent.runtime.AgentRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Agent 实时执行 SSE 端点（SSE 协议 v2）。
 *
 * <p>对应 development-plan.md §M2 补充项「SSE 协议升级（统一事件协议 v2）」：向客户端实时推送 Agent ReAct 执行过程的 {@code thought
 * / action / observation / done / error} 结构化事件， 前端 Agent 页面按事件类型做时间轴渲染。
 *
 * <p>事件协议：
 *
 * <ul>
 *   <li>{@code thought} → {"runtimeId","step","content"}：Agent 推理中间步骤
 *   <li>{@code action} → {"runtimeId","skillId","input"}：Agent 工具调用开始
 *   <li>{@code observation} → {"runtimeId","step","output"}：Agent 工具调用返回
 *   <li>{@code done} → {"runtimeId","latencyMs"}：执行完成
 *   <li>{@code error} → {"code","message"}：结构化错误
 * </ul>
 *
 * <p>鉴权：初始 REQUEST 由 JWT 过滤器鉴权（仅 runtime 归属用户可订阅），SSE 流的 ASYNC 派发已由 {@code SecurityConfig} 全局放行。
 *
 * @author add-agent-runtime
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/runtime")
@RequiredArgsConstructor
public class AgentSseController {

  private final AgentEventBus agentEventBus;
  private final AgentRuntimeService agentRuntimeService;

  /**
   * 订阅某 runtime 的实时执行事件流。
   *
   * @param runtimeId runtime ID
   * @return SSE 事件流（先重放最近事件，再实时接收）
   */
  @GetMapping(value = "/{runtimeId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<String>> stream(@PathVariable Long runtimeId) {
    Long userId = getCurrentUserId();
    AgentRuntime runtime;
    try {
      runtime = agentRuntimeService.loadRuntime(runtimeId);
    } catch (Exception e) {
      return Flux.just(
          ServerSentEvent.<String>builder()
              .event("error")
              .data("{\"code\":\"RUNTIME_NOT_FOUND\",\"message\":\"runtime not found\"}")
              .build());
    }
    if (userId != null && !userId.equals(runtime.getUserId())) {
      return Flux.just(
          ServerSentEvent.<String>builder()
              .event("error")
              .data("{\"code\":\"FORBIDDEN\",\"message\":\"无权访问该 runtime\"}")
              .build());
    }
    log.info("[AgentSseController][stream][runtimeId({}) 订阅执行流]", runtimeId);
    return agentEventBus.subscribe(runtimeId);
  }

  private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.getPrincipal()
            instanceof org.springframework.security.core.userdetails.UserDetails details) {
      // 兼容 AuthUserDetails（用户 ID 在 username 字段）
      try {
        return Long.parseLong(details.getUsername());
      } catch (NumberFormatException e) {
        return 0L;
      }
    }
    return 0L;
  }
}
