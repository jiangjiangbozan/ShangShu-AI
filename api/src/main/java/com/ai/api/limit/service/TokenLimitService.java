package com.ai.api.limit.service;

import com.ai.api.limit.entity.TokenLimitConfig;
import com.ai.api.limit.mapper.TokenLimitConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Token 限额 / 限流（A2）。
 *
 * <p>配置由 {@code sys_token_limit_config} 表承载（按 scope+targetId 定位，targetId=0 为默认配置）。 执行侧基于 Redis 计数器：
 *
 * <ul>
 *   <li>每分钟 / 每日请求次数：请求开始时 INCR 计数，超过阈值拒绝（允许并发下轻微超限，保证低延迟）
 *   <li>周期 Token 用量：响应完成后 INCR 累计，下一次请求开始前只读检查是否超限
 * </ul>
 *
 * <p>Redis key 设计：
 *
 * <pre>
 *   limit:user:{userId}:minute:{yyyyMMddHHmm}
 *   limit:user:{userId}:day:{yyyyMMdd}
 *   limit:user:{userId}:tokens:{yyyyMMdd|yyyyMM}
 * </pre>
 */
@Slf4j
@Service
public class TokenLimitService {

  private static final String KEY_MINUTE = "limit:user:%d:minute:%s";
  private static final String KEY_DAY = "limit:user:%d:day:%s";
  private static final String KEY_TOKENS = "limit:user:%d:tokens:%s";

  private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

  private final TokenLimitConfigMapper configMapper;
  private final StringRedisTemplate redis;

  public TokenLimitService(TokenLimitConfigMapper configMapper, StringRedisTemplate redis) {
    this.configMapper = configMapper;
    this.redis = redis;
  }

  /** 限额检查结果。 */
  public record LimitResult(boolean allowed, String reason) {
    public static LimitResult allow() {
      return new LimitResult(true, null);
    }

    public static LimitResult deny(String reason) {
      return new LimitResult(false, reason);
    }
  }

  // ==================== CRUD ====================

  /** 分页查询配置。 */
  public IPage<TokenLimitConfig> list(int page, int size, String scope, Long targetId) {
    LambdaQueryWrapper<TokenLimitConfig> wrapper = new LambdaQueryWrapper<>();
    if (scope != null && !scope.isBlank()) {
      wrapper.eq(TokenLimitConfig::getScope, scope);
    }
    if (targetId != null) {
      wrapper.eq(TokenLimitConfig::getTargetId, targetId);
    }
    wrapper.orderByDesc(TokenLimitConfig::getUpdateTime);
    return configMapper.selectPage(Page.of(page, size), wrapper);
  }

  public TokenLimitConfig create(TokenLimitConfig config, String creator) {
    if (config.getScope() == null || config.getScope().isBlank()) {
      config.setScope("USER");
    }
    if (config.getTargetId() == null) {
      config.setTargetId(0L);
    }
    if (config.getTokenQuota() == null) config.setTokenQuota(0L);
    if (config.getQuotaPeriod() == null) config.setQuotaPeriod("DAY");
    if (config.getRateLimitPerMinute() == null) config.setRateLimitPerMinute(0);
    if (config.getRateLimitPerDay() == null) config.setRateLimitPerDay(0);
    if (config.getStatus() == null) config.setStatus("active");
    config.setCreator(creator);
    configMapper.insert(config);
    return config;
  }

  public TokenLimitConfig update(Long id, TokenLimitConfig config, String updater) {
    TokenLimitConfig exist = configMapper.selectById(id);
    if (exist == null) {
      throw new IllegalArgumentException("配置不存在: " + id);
    }
    config.setId(id);
    config.setUpdater(updater);
    configMapper.updateById(config);
    return configMapper.selectById(id);
  }

  public void delete(Long id) {
    TokenLimitConfig exist = configMapper.selectById(id);
    if (exist == null) {
      throw new IllegalArgumentException("配置不存在: " + id);
    }
    configMapper.deleteById(id);
  }

  // ==================== 执行侧 ====================

  /**
   * 请求开始前的限额检查：消费每分钟/每日请求次数，只读检查周期 Token 用量。
   *
   * @param userId 用户 ID（&lt;=0 视为匿名，不限制）
   */
  public LimitResult preCheck(long userId) {
    if (userId <= 0) {
      return LimitResult.allow();
    }
    TokenLimitConfig cfg = resolveConfig(userId);
    if (cfg == null) {
      return LimitResult.allow();
    }

    LocalDateTime now = LocalDateTime.now();
    int minuteLimit = nullable(cfg.getRateLimitPerMinute());
    if (minuteLimit > 0) {
      String key = String.format(KEY_MINUTE, userId, now.format(MINUTE_FMT));
      Long count = redis.opsForValue().increment(key);
      if (count != null && count == 1) {
        redis.expire(key, Duration.ofMinutes(2));
      }
      if (count != null && count > minuteLimit) {
        return LimitResult.deny("每分钟调用次数超限（>" + minuteLimit + " 次/分钟）");
      }
    }

    int dayLimit = nullable(cfg.getRateLimitPerDay());
    if (dayLimit > 0) {
      String key = String.format(KEY_DAY, userId, now.format(DAY_FMT));
      Long count = redis.opsForValue().increment(key);
      if (count != null && count == 1) {
        redis.expire(key, Duration.ofDays(2));
      }
      if (count != null && count > dayLimit) {
        return LimitResult.deny("每日调用次数超限（>" + dayLimit + " 次/日）");
      }
    }

    long quota = cfg.getTokenQuota() == null ? 0L : cfg.getTokenQuota();
    if (quota > 0) {
      String periodKey = periodKey(now, cfg.getQuotaPeriod());
      String key = String.format(KEY_TOKENS, userId, periodKey);
      String used = redis.opsForValue().get(key);
      long usedTokens = used == null ? 0L : Long.parseLong(used);
      if (usedTokens >= quota) {
        return LimitResult.deny(
            "周期 Token 限额已用尽（" + usedTokens + "/" + quota + " tokens，" + periodKey + "）");
      }
    }
    return LimitResult.allow();
  }

  /** 响应完成后累计 Token 用量（供下一次请求前检查）。 */
  public void recordUsage(long userId, int tokens) {
    if (userId <= 0 || tokens <= 0) {
      return;
    }
    try {
      TokenLimitConfig cfg = resolveConfig(userId);
      if (cfg == null || (cfg.getTokenQuota() == null || cfg.getTokenQuota() <= 0)) {
        return;
      }
      LocalDateTime now = LocalDateTime.now();
      String periodKey = periodKey(now, cfg.getQuotaPeriod());
      String key = String.format(KEY_TOKENS, userId, periodKey);
      Long count = redis.opsForValue().increment(key, tokens);
      if (count != null && count.longValue() == tokens) {
        redis.expire(key, periodTtl(cfg.getQuotaPeriod()));
      }
    } catch (Exception e) {
      log.warn("[TokenLimitService][recordUsage userId({}) failed({})]", userId, e.getMessage());
    }
  }

  // ==================== 私有 ====================

  /** 解析用户配置：优先 targetId=userId 的专属配置，回退到 targetId=0 默认配置。 */
  private TokenLimitConfig resolveConfig(long userId) {
    TokenLimitConfig own =
        configMapper.selectOne(
            new LambdaQueryWrapper<TokenLimitConfig>()
                .eq(TokenLimitConfig::getScope, "USER")
                .eq(TokenLimitConfig::getTargetId, userId)
                .last("LIMIT 1"));
    if (own != null) {
      return "active".equalsIgnoreCase(own.getStatus()) ? own : null;
    }
    TokenLimitConfig def =
        configMapper.selectOne(
            new LambdaQueryWrapper<TokenLimitConfig>()
                .eq(TokenLimitConfig::getScope, "USER")
                .eq(TokenLimitConfig::getTargetId, 0)
                .last("LIMIT 1"));
    return def != null && "active".equalsIgnoreCase(def.getStatus()) ? def : null;
  }

  private int nullable(Integer v) {
    return v == null ? 0 : v;
  }

  private String periodKey(LocalDateTime now, String period) {
    if ("MONTH".equalsIgnoreCase(period)) {
      return now.format(MONTH_FMT);
    }
    return now.format(DAY_FMT);
  }

  private Duration periodTtl(String period) {
    if ("MONTH".equalsIgnoreCase(period)) {
      LocalDate next = YearMonth.now().plusMonths(1).atDay(1);
      return Duration.between(LocalDateTime.now(), next.atStartOfDay()).plus(Duration.ofDays(1));
    }
    return Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(2).atStartOfDay());
  }
}
