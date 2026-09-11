package com.ai.api.model.service;

import com.ai.api.data.entity.SysLlmLog;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * LLM 调用日志 Service
 *
 * <p>依据 design.md D5：通过 RabbitMQ {@code ai.llm.log} exchange 异步写入 {@code sys_llm_log}， 含
 * traceId、prompt 截断、response 截断。
 *
 * <p>异步策略：调用方完成模型调用后调用 {@link #asyncLog}，由 RabbitMQ 投递到 {@code q.llm.log} 队列，由 {@code
 * LlmLogConsumer} 消费写入 DB。
 *
 * @author bootstrap-project
 */
@Slf4j
@Service
public class LlmLogService {

  /** LLM 日志 exchange 名称 */
  public static final String LLM_LOG_EXCHANGE = "ai.llm.log";

  /** LLM 日志 routing key */
  public static final String LLM_LOG_ROUTING_KEY = "llm.log";

  /** prompt 最大长度（字符，超出截断） */
  private static final int MAX_PROMPT_LENGTH = 4000;

  /** response 最大长度（字符，超出截断） */
  private static final int MAX_RESPONSE_LENGTH = 8000;

  @Resource private AmqpTemplate amqpTemplate;

  /**
   * 异步发送 LLM 调用日志到 RabbitMQ。
   *
   * <p>使用 @Async 与 RabbitMQ 双重异步：先 @Async 写入 MQ（避免 MQ 投递阻塞 HTTP 线程）， 再由消费者写入 DB。
   *
   * @param logEntry 日志实体
   */
  @Async("asyncExecutor")
  public void asyncLog(SysLlmLog logEntry) {
    try {
      // 截断超长内容
      if (logEntry.getPrompt() != null && logEntry.getPrompt().length() > MAX_PROMPT_LENGTH) {
        logEntry.setPrompt(logEntry.getPrompt().substring(0, MAX_PROMPT_LENGTH) + "...[truncated]");
      }
      if (logEntry.getResponse() != null && logEntry.getResponse().length() > MAX_RESPONSE_LENGTH) {
        logEntry.setResponse(
            logEntry.getResponse().substring(0, MAX_RESPONSE_LENGTH) + "...[truncated]");
      }
      // 确保 traceId 从 MDC 自动获取
      if (logEntry.getTraceId() == null) {
        logEntry.setTraceId(MDC.get("traceId"));
      }
      // 由全局 JacksonJsonMessageConverter 负责 JSON 序列化
      amqpTemplate.convertAndSend(LLM_LOG_EXCHANGE, LLM_LOG_ROUTING_KEY, logEntry);
      log.debug(
          "[asyncLog][modelId({}) traceId({}) status({}) latencyMs({})]",
          logEntry.getModelId(),
          logEntry.getTraceId(),
          logEntry.getStatus(),
          logEntry.getLatencyMs());
    } catch (Exception ex) {
      // 日志失败不影响主流程，仅记录错误
      log.error(
          "[asyncLog][发送 LLM 日志到 MQ 失败 modelId({}) error({})]",
          logEntry.getModelId(),
          ex.getMessage(),
          ex);
    }
  }
}
