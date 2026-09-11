package com.ai.api.common.lock;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/**
 * Redisson 分布式锁服务（add-p2-cache-search-rewrite）。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>知识库批量导入串行化：{@code lock:kb:import:{kbId}}
 *   <li>模型配置更新互斥：{@code lock:model:config:{modelId}}
 * </ul>
 *
 * <p>watchdog 自动续期：未指定 leaseTime 时启用 watchdog（默认 30s 续期）， 持锁线程异常时 TTL 到期自动释放，避免死锁。
 *
 * @author add-p2-cache-search-rewrite
 */
@Slf4j
@Service
public class DistributedLockService {

  private final RedissonClient redissonClient;

  public DistributedLockService(RedissonClient redissonClient) {
    this.redissonClient = redissonClient;
  }

  /**
   * 尝试获取锁后执行 {@code supplier}，获取失败返回 {@link Optional#empty()}。
   *
   * <p>不指定 leaseTime，启用 watchdog 自动续期。
   *
   * @param lockKey 锁 Key
   * @param waitTime 等待时长（单位：秒）
   * @param supplier 持锁时执行的逻辑
   * @param <T> 返回类型
   * @return 持锁时返回 supplier 结果；未获取锁返回 empty
   */
  public <T> Optional<T> tryLock(String lockKey, long waitTime, Supplier<T> supplier) {
    RLock lock = redissonClient.getLock(lockKey);
    boolean locked = false;
    try {
      locked = lock.tryLock(waitTime, TimeUnit.SECONDS);
      if (!locked) {
        log.warn("[DistributedLockService][获取锁失败: lockKey={}]", lockKey);
        return Optional.empty();
      }
      return Optional.ofNullable(supplier.get());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("[DistributedLockService][等待锁被中断: lockKey={}]", lockKey, e);
      return Optional.empty();
    } finally {
      if (locked && lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  /**
   * 尝试获取锁后执行 {@code supplier}，获取失败执行 {@code fallback}。
   *
   * @param lockKey 锁 Key
   * @param waitTime 等待时长（单位：秒）
   * @param supplier 持锁时执行的逻辑
   * @param fallback 未获取锁时的兜底逻辑
   * @param <T> 返回类型
   * @return 持锁时返回 supplier 结果；否则返回 fallback 结果
   */
  public <T> T tryLockOrElse(
      String lockKey, long waitTime, Supplier<T> supplier, Supplier<T> fallback) {
    return tryLock(lockKey, waitTime, supplier).orElseGet(fallback);
  }
}
