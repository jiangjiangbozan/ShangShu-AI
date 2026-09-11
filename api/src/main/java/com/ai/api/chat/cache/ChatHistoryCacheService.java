package com.ai.api.chat.cache;

import com.ai.api.common.config.CacheProperties;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 会话历史 Redis 缓存服务（add-p2-cache-search-rewrite，add-dify-cursor-pagination 扩展）。
 *
 * <p>Key 规范：{@code chat:history:{sessionId}}，TTL 1h。
 *
 * <p>缓存内容：最新一屏消息 + {@code hasMore} 标记（{@link ChatHistorySnapshot}）。
 *
 * <p>Cache-Aside Pattern：
 *
 * <ul>
 *   <li>读：先查 Redis，未命中回源 MySQL 并回填
 *   <li>写：新消息写入 MySQL 后调用 {@link #evict} 失效缓存
 *   <li>异常：Redis 不可达时降级直查 MySQL，不阻塞主流程
 * </ul>
 *
 * @author add-p2-cache-search-rewrite
 */
@Slf4j
@Service
public class ChatHistoryCacheService {

  private static final String KEY_PREFIX = "chat:history:";

  private final RedisTemplate<String, Object> redisTemplate;
  private final Duration ttl;

  public ChatHistoryCacheService(
      RedisTemplate<String, Object> redisTemplate, CacheProperties cacheProperties) {
    this.redisTemplate = redisTemplate;
    this.ttl = cacheProperties.getChat().getHistoryTtl();
  }

  /**
   * 读取或回源加载会话历史。
   *
   * @param sessionId 会话 ID
   * @param loader 缓存未命中时的回源加载逻辑（返回消息快照）
   * @param <T> 消息类型
   * @return 缓存或回源的消息快照
   */
  @SuppressWarnings("unchecked")
  public <T> ChatHistorySnapshot<T> getOrLoad(
      Long sessionId, Supplier<ChatHistorySnapshot<T>> loader) {
    String key = KEY_PREFIX + sessionId;
    try {
      Object cached = redisTemplate.opsForValue().get(key);
      if (cached instanceof ChatHistorySnapshot<?> snapshot) {
        log.debug("[ChatHistoryCacheService][缓存命中: sessionId={}]", sessionId);
        return (ChatHistorySnapshot<T>) snapshot;
      }
      ChatHistorySnapshot<T> fresh = loader.get();
      redisTemplate.opsForValue().set(key, fresh, ttl);
      log.debug("[ChatHistoryCacheService][缓存未命中，回源并回填: sessionId={}]", sessionId);
      return fresh;
    } catch (Exception e) {
      log.warn(
          "[ChatHistoryCacheService][Redis 异常降级直查 MySQL: sessionId={}, error={}]",
          sessionId,
          e.getMessage());
      return loader.get();
    }
  }

  /**
   * 失效会话历史缓存。
   *
   * <p>消息写入 MySQL 成功后调用，保证下次读取触发回源回填。
   *
   * @param sessionId 会话 ID
   */
  public void evict(Long sessionId) {
    String key = KEY_PREFIX + sessionId;
    try {
      Boolean deleted = redisTemplate.delete(key);
      log.debug("[ChatHistoryCacheService][失效缓存: sessionId={}, deleted={}]", sessionId, deleted);
    } catch (Exception e) {
      log.warn(
          "[ChatHistoryCacheService][失效缓存异常（不影响主流程）: sessionId={}, error={}]",
          sessionId,
          e.getMessage());
    }
  }
}
