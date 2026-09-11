package com.ai.api.agent.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 执行事件总线（SSE 协议 v2）。
 *
 * <p>对应 development-plan.md §M2 补充项「SSE 协议升级（统一事件协议 v2）」：
 *
 * <table>
 *   <tr><th>event</th><th>data 结构</th><th>场景</th></tr>
 *   <tr><td>{@code thought}</td><td>{"runtimeId","step","content"}</td><td>Agent 推理中间步骤</td></tr>
 *   <tr><td>{@code action}</td><td>{"runtimeId","skillId","input"}</td><td>Agent 工具调用开始</td></tr>
 *   <tr><td>{@code observation}</td><td>{"runtimeId","step","output"}</td><td>Agent 工具调用返回</td></tr>
 *   <tr><td>{@code done}</td><td>{"runtimeId","latencyMs"}</td><td>完成</td></tr>
 *   <tr><td>{@code error}</td><td>{"code","message"}</td><td>错误（结构化错误码）</td></tr>
 * </table>
 *
 * <p>实现：按 {@code runtimeId} 分桶的 Reactor {@link Sinks.Many} 内存事件流，{@link #subscribe} 采用 Replay
 * 策略——SSE 客户端迟到订阅时先重放最近事件再实时接收，避免「执行已完成但客户端看不到过程」。 Agent 执行引擎（{@code AgentRunner}）与 SSE
 * 端点通过本总线解耦，事件经 {@link Sinks} 多播。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component
public class AgentEventBus {

  /** 每个 runtime 最多重放的事件条数（覆盖完整 ReAct 循环中间态）。 */
  private static final int REPLAY_LIMIT = 200;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** runtimeId → 事件流（replay sink，多订阅者共享同一热流）。 */
  private final Map<Long, Sinks.Many<AgentSseMessage>> sinks = new ConcurrentHashMap<>();

  /** 一条 v2 协议消息：event 类型 + data JSON 载荷。 */
  public record AgentSseMessage(String event, String data) {}

  /** 发布 thought 事件：Agent 推理中间步骤。 */
  public void publishThought(Long runtimeId, int step, String content) {
    publish(
        runtimeId,
        "thought",
        json(Map.of("runtimeId", runtimeId, "step", step, "content", content)));
  }

  /** 发布 action 事件：Agent 工具调用开始。 */
  public void publishAction(Long runtimeId, String skillId, Map<String, Object> input) {
    publish(
        runtimeId,
        "action",
        json(Map.of("runtimeId", runtimeId, "skillId", skillId, "input", input)));
  }

  /** 发布 observation 事件：Agent 工具调用返回。 */
  public void publishObservation(Long runtimeId, int step, Map<String, Object> output) {
    publish(
        runtimeId,
        "observation",
        json(Map.of("runtimeId", runtimeId, "step", step, "output", output)));
  }

  /** 发布 done 事件：执行完成（带总耗时）。 */
  public void publishDone(Long runtimeId, long latencyMs) {
    publish(runtimeId, "done", json(Map.of("runtimeId", runtimeId, "latencyMs", latencyMs)));
  }

  /** 发布 error 事件：结构化错误码。 */
  public void publishError(Long runtimeId, String code, String message) {
    publish(runtimeId, "error", json(Map.of("code", code, "message", message)));
  }

  /** 订阅某 runtime 的实时执行事件（先重放最近事件，再实时接收）。 */
  public Flux<ServerSentEvent<String>> subscribe(Long runtimeId) {
    Sinks.Many<AgentSseMessage> sink =
        sinks.computeIfAbsent(runtimeId, k -> Sinks.many().replay().limit(REPLAY_LIMIT));
    return sink.asFlux()
        .map(
            msg ->
                ServerSentEvent.<String>builder()
                    .id(String.valueOf(runtimeId))
                    .event(msg.event())
                    .data(msg.data())
                    .build())
        .doFinally(
            sig -> {
              // 流关闭后若该 runtime 无活跃订阅者，移除 sink 避免内存泄漏
              if (sink.currentSubscriberCount() <= 0) {
                sinks.remove(runtimeId, sink);
              }
            });
  }

  private void publish(Long runtimeId, String event, String data) {
    Sinks.Many<AgentSseMessage> sink =
        sinks.computeIfAbsent(runtimeId, k -> Sinks.many().replay().limit(REPLAY_LIMIT));
    Sinks.EmitResult result = sink.tryEmitNext(new AgentSseMessage(event, data));
    if (result.isFailure()) {
      log.warn(
          "[AgentEventBus][publish][runtimeId({}) event({}) emit failed({})]",
          runtimeId,
          event,
          result);
    }
  }

  private String json(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      log.warn("[AgentEventBus][json][序列化失败({})]", e.getMessage());
      return "{}";
    }
  }
}
