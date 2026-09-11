package com.ai.api.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * TraceId 链路追踪过滤器
 *
 * <p>依据《企业Java后端开发规范.md》§4.4：所有请求生成/继承 {@code X-Trace-Id}， 写入 MDC，并通过 {@link TaskDecorator} 传递到
 * {@code @Async} 与 RabbitMQ 消费者。
 *
 * <p>MDC 字段：
 *
 * <ul>
 *   <li>{@code traceId}：链路追踪 ID
 * </ul>
 *
 * @author bootstrap-project
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  /** 请求头中的 TraceId 字段名 */
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  /** MDC 中 TraceId 的字段名 */
  public static final String MDC_TRACE_ID = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // 1. 优先从请求头继承
    String traceId = request.getHeader(TRACE_ID_HEADER);
    // 2. 缺失则生成
    if (StringUtils.isBlank(traceId)) {
      traceId = generateTraceId();
    }
    // 3. 写入 MDC
    MDC.put(MDC_TRACE_ID, traceId);
    // 4. 响应头也回写，方便前端与日志关联
    response.setHeader(TRACE_ID_HEADER, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      // 5. 请求结束清理，防止内存泄露与线程复用串扰
      MDC.remove(MDC_TRACE_ID);
    }
  }

  /**
   * 生成 TraceId（32 位无连字符 UUID）。
   *
   * @return String
   */
  private String generateTraceId() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  /**
   * 提供 TaskDecorator 用于将 MDC 传递到异步线程。
   *
   * <p>在 {@code @Configuration} 中注入此 Bean 后，{@code @Async} 线程池会自动继承调用方的 MDC。
   *
   * @return TaskDecorator
   */
  public static TaskDecorator mdcTaskDecorator() {
    return runnable -> {
      // 捕获调用方 MDC
      String callerTraceId = MDC.get(MDC_TRACE_ID);
      return () -> {
        try {
          if (callerTraceId != null) {
            MDC.put(MDC_TRACE_ID, callerTraceId);
          }
          runnable.run();
        } finally {
          MDC.remove(MDC_TRACE_ID);
        }
      };
    };
  }
}
