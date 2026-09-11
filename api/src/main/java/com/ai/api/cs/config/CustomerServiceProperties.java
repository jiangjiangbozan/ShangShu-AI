package com.ai.api.cs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 客服模块配置（add-customer-service）。
 *
 * <p>对应 {@code application-dev.yml} 的 {@code cs.*} 配置块。
 *
 * @author add-customer-service
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cs")
public class CustomerServiceProperties {

  /** 意图识别置信度阈值，低于此值触发澄清或兜底。 */
  private double intentConfidenceThreshold = 0.6;

  /** KB 命中阈值，top1 score 高于此值跳过 LLM 直接返回。 */
  private double kbHitThreshold = 0.85;

  /** 单次会话最大澄清轮数，超出强制 fallback。 */
  private int clarifyMaxRounds = 3;

  /** 默认兜底意图。 */
  private String fallbackIntent = "small_talk";

  /** 意图规则缓存刷新间隔（毫秒），运营改 cs_intent 后最多这个时长生效。 */
  private long intentRuleRefreshMs = 30000;
}
