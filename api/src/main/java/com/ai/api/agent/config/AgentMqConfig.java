package com.ai.api.agent.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 模块 RabbitMQ 队列声明（add-agent-runtime）。
 *
 * <p>声明 3 个业务队列 + 2 个 DLX：
 *
 * <ul>
 *   <li>{@code agent.task.dispatch} + DLX {@code agent.task.dlq}：异步任务调度
 *   <li>{@code agent.state.notify}（fanout）：状态变更通知（前端 SSE 网关订阅）
 *   <li>{@code agent.trace.collect} + DLX {@code agent.trace.dlq}：Trace 异步落库
 * </ul>
 *
 * @author add-agent-runtime
 */
@Configuration
public class AgentMqConfig {

  // ===== 任务调度队列 =====
  public static final String TASK_QUEUE = "agent.task.dispatch";
  public static final String TASK_EXCHANGE = "agent.task";
  public static final String TASK_ROUTING_KEY = "dispatch";
  public static final String TASK_DLQ = "agent.task.dlq";

  // ===== 状态变更通知队列 =====
  public static final String STATE_QUEUE = "agent.state.notify";
  public static final String STATE_EXCHANGE = "agent.state";

  // ===== Trace 异步落库队列 =====
  public static final String TRACE_QUEUE = "agent.trace.collect";
  public static final String TRACE_EXCHANGE = "agent.trace";
  public static final String TRACE_ROUTING_KEY = "collect";
  public static final String TRACE_DLQ = "agent.trace.dlq";

  // ===== 任务调度队列声明 =====

  @Bean
  public Queue agentTaskDlq() {
    return QueueBuilder.durable(TASK_DLQ).build();
  }

  @Bean
  public Queue agentTaskQueue() {
    return QueueBuilder.durable(TASK_QUEUE)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", TASK_DLQ)
        .build();
  }

  @Bean
  public TopicExchange agentTaskExchange() {
    return new TopicExchange(TASK_EXCHANGE, true, false);
  }

  @Bean
  public Binding bindingAgentTask() {
    return BindingBuilder.bind(agentTaskQueue()).to(agentTaskExchange()).with(TASK_ROUTING_KEY);
  }

  // ===== 状态变更通知队列（fanout） =====

  @Bean
  public Queue agentStateQueue() {
    return QueueBuilder.durable(STATE_QUEUE).build();
  }

  @Bean
  public FanoutExchange agentStateExchange() {
    return new FanoutExchange(STATE_EXCHANGE, true, false);
  }

  @Bean
  public Binding bindingAgentState() {
    return BindingBuilder.bind(agentStateQueue()).to(agentStateExchange());
  }

  // ===== Trace 异步落库队列 =====

  @Bean
  public Queue agentTraceDlq() {
    return QueueBuilder.durable(TRACE_DLQ).build();
  }

  @Bean
  public Queue agentTraceQueue() {
    return QueueBuilder.durable(TRACE_QUEUE)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", TRACE_DLQ)
        .build();
  }

  @Bean
  public TopicExchange agentTraceExchange() {
    return new TopicExchange(TRACE_EXCHANGE, true, false);
  }

  @Bean
  public Binding bindingAgentTrace() {
    return BindingBuilder.bind(agentTraceQueue()).to(agentTraceExchange()).with(TRACE_ROUTING_KEY);
  }
}
