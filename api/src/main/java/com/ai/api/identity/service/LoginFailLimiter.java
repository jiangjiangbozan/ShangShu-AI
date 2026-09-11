package com.ai.api.identity.service;

import com.ai.api.identity.config.AuthProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 登录失败限制器
 *
 * <p>依据 spec.md "用户登录" Scenario "账户锁定"：连续 5 次失败锁定 15 分钟。
 *
 * <p>Redis Key: {@code auth:fail:{account}}，TTL 等于锁定时长。
 *
 * <p>注意：本限流器作为 DB 字段 {@code fail_count} + {@code lock_until} 的兜底加速层， 当 Redis 不可用时由 DB 字段承担限流职责。
 *
 * @author add-user-auth
 */
@Service
public class LoginFailLimiter {

  private static final String KEY_PREFIX = "auth:fail:";

  private final StringRedisTemplate redisTemplate;
  private final AuthProperties authProperties;

  public LoginFailLimiter(StringRedisTemplate redisTemplate, AuthProperties authProperties) {
    this.redisTemplate = redisTemplate;
    this.authProperties = authProperties;
  }

  /**
   * 记录一次失败并返回当前失败次数。
   *
   * @param account 账号（用户名或邮箱）
   * @return 当前失败次数
   */
  public int recordFailure(String account) {
    String key = KEY_PREFIX + account;
    Long count = redisTemplate.opsForValue().increment(key); // key 自增 +1；key 不存在则直接置为1
    if (count != null && count == 1L) {
      // 第一次失败的时候，给这个 key 设置过期时间
      redisTemplate.expire(key, authProperties.getLoginFail().getLockDuration());
    }
    return count == null ? 0 : count.intValue();
  }

  /**
   * 清除失败计数（登录成功时调用）。
   *
   * @param account 账号
   */
  public void clearFailures(String account) {
    redisTemplate.delete(KEY_PREFIX + account);
  }

  /**
   * 判断账号是否超过最大失败次数。
   *
   * @param account 账号
   * @return 超过返回 true
   */
  public boolean isExceeded(String account) {
    String count = redisTemplate.opsForValue().get(KEY_PREFIX + account);
    if (count == null) {
      return false;
    }
    try {
      return Integer.parseInt(count) >= authProperties.getLoginFail().getMaxAttempts();
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * 获取锁定剩余时长（秒）。
   *
   * @param account 账号
   * @return 剩余秒数，未锁定返回 0
   */
  public long getRemainingLockSeconds(String account) {
    Long ttl = redisTemplate.getExpire(KEY_PREFIX + account);
    return ttl == null ? 0 : Math.max(0, ttl);
  }
}
