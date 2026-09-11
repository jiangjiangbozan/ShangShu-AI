package com.ai.api.agent.runtime;

import com.ai.api.agent.config.AgentMqConfig;
import com.ai.api.agent.entity.AgentRuntime;
import com.ai.api.agent.runner.AgentRunner;
import com.ai.api.agent.runner.StrategyFactory;
import com.ai.api.common.lock.DistributedLockService;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Agent 异步任务消费者（add-agent-runtime，add-agent-runner-pipeline 适配）。
 *
 * <p>消费 {@code agent.task.dispatch} 队列消息，按 runtime 配置的执行策略经 {@link StrategyFactory} 选择 Runner（COT /
 * FC）同步执行。异常进入死信队列并标记 runtime 为 failed。
 *
 * <p>at-least-once 语义下的幂等保障（add-mq-at-least-once）：
 *
 * <ul>
 *   <li>终态跳过：DONE/FAILED/CANCELLED 的 runtime 收到重投消息直接放行（配合 AUTO ack 自动确认）， 防止"已完成的任务被重复执行"
 *   <li>Redisson RLock 防双跑：不指定 leaseTime 启用 watchdog 自动续期，进程崩溃后约 30s 锁自动释放， 避免"setIfAbsent 无 TTL
 *       崩溃后锁泄漏 → 后续重投全被跳过"的死锁
 * </ul>
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskConsumer {

  private static final String LOCK_KEY_PREFIX = "agent:lock:";
  private static final long LOCK_WAIT_SECONDS = 10;

  private final StrategyFactory strategyFactory;
  private final AgentRuntimeService agentRuntimeService;
  private final DistributedLockService distributedLockService;

  @RabbitListener(queues = AgentMqConfig.TASK_QUEUE)
  public void onTask(Map<String, Object> msg) {
    // JacksonConfig 将 Long 序列化为字符串（防前端精度丢失），此处兼容数字/字符串两种形态
    Long runtimeId = Long.parseLong(String.valueOf(msg.get("runtimeId")));
    log.info("[AgentTaskConsumer][onTask][runtimeId({})]", runtimeId);

    // 幂等跳过：终态（DONE/FAILED/CANCELLED）说明任务已处理完毕，
    // 重投消息（进程崩溃未 ack → 重新投递）直接放行，避免重复执行
    if (agentRuntimeService.isTerminal(runtimeId)) {
      log.info("[AgentTaskConsumer][onTask][runtimeId({}) 已是终态，跳过重投消息]", runtimeId);
      return;
    }

    // Redisson RLock 防双跑：多实例同时拉到同一消息只一个执行。
    // 不指定 leaseTime → watchdog 自动续期；进程崩溃后约 30s 锁自动释放，不会锁泄漏
    Optional<Object> executed =
        distributedLockService.tryLock(
            LOCK_KEY_PREFIX + runtimeId,
            LOCK_WAIT_SECONDS,
            () -> {
              try {
                AgentRunner runner = resolveRunner(runtimeId);
                runner.run(runtimeId);
              } catch (RuntimeException e) {
                log.error("[AgentTaskConsumer][onTask][runtimeId({}) 执行失败]", runtimeId, e);
                // 标记 failed；dlq 由 RabbitMQ DLX 自动处理，这里通过异常重投触发
                agentRuntimeService.fail(runtimeId, e.getMessage(), null);
                throw e;
              }
              return null;
            });
    if (executed.isEmpty()) {
      log.warn("[AgentTaskConsumer][onTask][runtimeId({}) 已被其他实例锁定，跳过]", runtimeId);
    }
  }

  /** 按 runtime 的 strategy 字段选择执行器（旧数据无 strategy 时默认 COT）。 */
  private AgentRunner resolveRunner(Long runtimeId) {
    AgentRuntime runtime = agentRuntimeService.loadRuntime(runtimeId);
    String strategy = runtime.getStrategy();
    return strategy == null || strategy.isBlank()
        ? strategyFactory.getRunner("COT")
        : strategyFactory.getRunner(strategy);
  }
}
