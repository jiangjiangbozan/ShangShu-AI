package com.ai.api.common.config;

import com.ai.api.common.filter.TraceIdFilter;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务配置
 *
 * <p>依据《企业Java后端开发规范.md》§4.4：通过 {@link TaskDecorator} 将 MDC 中的 traceId 传递到 {@code @Async}
 * 线程，保证全链路追踪不丢失。
 *
 * @author bootstrap-project
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

  /**
   * 注册 MDC TaskDecorator。
   *
   * @return TaskDecorator
   */
  @Bean
  public TaskDecorator mdcTaskDecorator() {
    return TraceIdFilter.mdcTaskDecorator();
  }

  /**
   * 默认 @Async 线程池。
   *
   * @return Executor
   */
  @Override
  @Bean("asyncExecutor")
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(200);
    executor.setKeepAliveSeconds(60);
    executor.setThreadNamePrefix("ai-async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    // 关键：注入 MDC TaskDecorator，传递 traceId
    executor.setTaskDecorator(mdcTaskDecorator());
    executor.initialize();
    return executor;
  }
}
