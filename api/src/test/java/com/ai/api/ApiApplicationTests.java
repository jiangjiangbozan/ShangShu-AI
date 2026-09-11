package com.ai.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 应用上下文加载测试
 *
 * <p>验证 Spring 容器能否成功启动。本测试依赖完整基础设施（MySQL/Redis/ES/RabbitMQ）， 仅在 {@code REDIS_HOST} 环境变量存在时启用，避免本地无
 * Docker 环境时构建失败。
 *
 * <p>注意：本测试使用 {@code @SpringBootTest} 默认会扫描完整 {@code @SpringBootApplication} 上下文，包括
 * RedisChatMemoryRepository、ElasticsearchClient、RabbitConnectionFactory 等 所有自动装配的
 * Bean。无完整中间件环境时会启动失败。
 *
 * @author bootstrap-project
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".*")
class ApiApplicationTests {

  @Test
  void contextLoads() {
    // 仅验证上下文能加载，无具体断言
  }
}
