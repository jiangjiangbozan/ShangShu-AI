package com.ai.api;

import com.ai.api.common.config.CacheProperties;
import com.ai.api.identity.config.AuthProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能助手 AI 开放平台 - 后端启动类
 *
 * <p>启动后访问：
 *
 * <ul>
 *   <li>Swagger UI: http://localhost:8080/swagger-ui.html
 *   <li>健康检查: http://localhost:8080/actuator/health
 *   <li>Prometheus 指标: http://localhost:8080/actuator/prometheus
 * </ul>
 *
 * @author bootstrap-project
 */
@SpringBootApplication
@MapperScan("com.ai.api.**.mapper")
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({AuthProperties.class, CacheProperties.class})
public class ApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApiApplication.class, args);
  }
}
