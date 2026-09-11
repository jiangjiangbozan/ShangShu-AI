package com.ai.api.agent.runtime;

import com.ai.api.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 心跳扫描器（add-agent-runtime）。
 *
 * <p>定时扫描 {@code status=running AND last_heartbeat < now() - ttl} 的 runtime，状态转 failed。
 *
 * <p>同时在应用启动时执行一次扫描（断点续跑支持：重启后立即识别超时任务）。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatScanner {

  private final AgentRuntimeService agentRuntimeService;
  private final AgentProperties agentProperties;

  /** 应用启动后立即扫描一次（断点续跑支持）。 */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    scanOnce();
  }

  /** 定时扫描（每 60s 一次）。 */
  @Scheduled(fixedDelayString = "${agent.heartbeat-scan-interval:60000}")
  public void scanOnce() {
    try {
      int count = agentRuntimeService.scanOverdueRuntimes().size();
      if (count > 0) {
        log.info(
            "[HeartbeatScanner][scanOnce][扫描到 {} 个超时任务转 failed，ttl={}s]",
            count,
            agentProperties.getHeartbeatTtl());
      }
    } catch (Exception e) {
      log.error("[HeartbeatScanner][scanOnce][扫描异常]", e);
    }
  }
}
