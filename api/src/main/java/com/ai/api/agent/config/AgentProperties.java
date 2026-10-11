package com.ai.api.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 模块配置（add-agent-runtime）。
 *
 * <p>对应 {@code application-dev.yml} 的 {@code agent.*} 配置块。
 *
 * @author add-agent-runtime
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

  /** 心跳超时阈值（秒），超时后 running 任务被扫描转 failed。 */
  private int heartbeatTtl = 300;

  /** 心跳扫描间隔（毫秒）。 */
  private long heartbeatScanInterval = 60000;

  /** ReAct 引擎最大迭代次数。 */
  private React react = new React();

  /** 短期记忆配置。 */
  private Memory memory = new Memory();

  /** MQ 队列名配置。 */
  private Mq mq = new Mq();

  /** Docker 沙箱配置。 */
  private Sandbox sandbox = new Sandbox();

  @Data
  public static class React {
    private int maxIterations = 10;

    /** 单次 Skill 调用超时（秒），超时不再抛异常，作为观察结果让引擎基于已有信息收尾（add-skill-timeout-guard）。 */
    private int skillTimeout = 30;
  }

  @Data
  public static class Memory {
    /** 滑动窗口大小（保留最近 N 轮原文）。 */
    private int windowSize = 10;
  }

  @Data
  public static class Mq {
    private String taskDispatchQueue = "agent.task.dispatch";
    private String taskDispatchExchange = "agent.task";
    private String taskDispatchRoutingKey = "dispatch";
    private String taskDlq = "agent.task.dlq";

    private String stateNotifyQueue = "agent.state.notify";
    private String stateNotifyExchange = "agent.state";

    private String traceCollectQueue = "agent.trace.collect";
    private String traceCollectExchange = "agent.trace";
    private String traceCollectRoutingKey = "collect";
    private String traceDlq = "agent.trace.dlq";
  }

  @Data
  public static class Sandbox {
    /** Docker 镜像（Python 沙箱）。 */
    private String image = "python:3.11-slim";

    /** 单次执行超时（秒）。 */
    private int timeout = 10;

    /** 是否启用沙箱（false 时 code_executor 返回 NOT_IMPLEMENTED）。 */
    private boolean enabled = true;
  }
}
