package com.ai.api.knowledge.cache;

import com.ai.api.common.config.CacheProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * RAG 检索结果缓存服务（add-p2-cache-search-rewrite）。
 *
 * <p>Key 规范：{@code rag:hits:kb:{kbId}:{queryHash}}，TTL 5min。
 *
 * <p>命中缓存时短路向量检索，降低 ES 压力；知识库内容变更时按 kbId 模式批量失效。
 *
 * @author add-p2-cache-search-rewrite
 */
@Slf4j
@Service
public class RagHitsCacheService {

  private static final String KEY_PREFIX = "rag:hits:kb:";

  private final RedisTemplate<String, Object> redisTemplate;
  private final Duration ttl;

  public RagHitsCacheService(
      RedisTemplate<String, Object> redisTemplate, CacheProperties cacheProperties) {
    this.redisTemplate = redisTemplate;
    this.ttl = cacheProperties.getRag().getHitsTtl();
  }

  /**
   * 读取或回源加载 RAG 检索结果。
   *
   * @param kbId 知识库 ID
   * @param query 用户原始 query
   * @param loader 缓存未命中时的回源加载逻辑
   * @param <T> chunk 类型
   * @return 缓存或回源的 chunk 列表
   */
  @SuppressWarnings("unchecked")
  public <T> List<T> getOrLoad(Long kbId, String query, Supplier<List<T>> loader) {
    String key = buildKey(kbId, query);
    try {
      Object cached = redisTemplate.opsForValue().get(key);
      if (cached instanceof List<?> list) {
        log.debug("[RagHitsCacheService][缓存命中: kbId={}, queryHash={}]", kbId, queryHash(query));
        return (List<T>) list;
      }
      List<T> fresh = loader.get();
      redisTemplate.opsForValue().set(key, fresh, ttl);
      log.debug(
          "[RagHitsCacheService][缓存未命中，回源并回填: kbId={}, queryHash={}]", kbId, queryHash(query));
      return fresh;
    } catch (Exception e) {
      log.warn("[RagHitsCacheService][Redis 异常降级直查 ES: kbId={}, error={}]", kbId, e.getMessage());
      return loader.get();
    }
  }

  /**
   * 按知识库 ID 批量失效缓存。
   *
   * <p>知识库内容变更（新增 / 删除文档、重新向量化）后调用。
   *
   * @param kbId 知识库 ID
   */
  public void evictByKb(Long kbId) {
    String pattern = KEY_PREFIX + kbId + ":*";
    try {
      Set<String> keys = redisTemplate.keys(pattern);
      if (keys == null || keys.isEmpty()) {
        return;
      }
      Long deleted = redisTemplate.delete(keys);
      log.info(
          "[RagHitsCacheService][批量失效: kbId={}, deleted={} of {}]", kbId, deleted, keys.size());
    } catch (Exception e) {
      log.warn("[RagHitsCacheService][批量失效异常（不影响主流程）: kbId={}, error={}]", kbId, e.getMessage());
    }
  }

  private String buildKey(Long kbId, String query) {
    return KEY_PREFIX + kbId + ":" + queryHash(query);
  }

  /**
   * 计算 query 的 SHA-256 hash（normalize 后）。
   *
   * <p>normalize：去除前后空格 + 转小写。
   */
  static String queryHash(String query) {
    String normalized = query == null ? "" : query.trim().toLowerCase();
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 是 JDK 标准，理论不会缺失
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
