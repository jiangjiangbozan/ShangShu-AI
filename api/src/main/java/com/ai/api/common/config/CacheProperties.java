package com.ai.api.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务缓存配置属性（add-p2-cache-search-rewrite）。
 *
 * <p>对应 application.yml 中 {@code cache} 配置块。
 *
 * @author add-p2-cache-search-rewrite
 */
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

  private Chat chat = new Chat();
  private Rag rag = new Rag();

  public Chat getChat() {
    return chat;
  }

  public void setChat(Chat chat) {
    this.chat = chat;
  }

  public Rag getRag() {
    return rag;
  }

  public void setRag(Rag rag) {
    this.rag = rag;
  }

  /** 会话历史缓存配置。 */
  public static class Chat {
    private Duration historyTtl = Duration.ofHours(1);

    public Duration getHistoryTtl() {
      return historyTtl;
    }

    public void setHistoryTtl(Duration historyTtl) {
      this.historyTtl = historyTtl;
    }
  }

  /** RAG 检索结果缓存配置。 */
  public static class Rag {
    private Duration hitsTtl = Duration.ofMinutes(5);
    private int hitsMaxSize = 1000;

    public Duration getHitsTtl() {
      return hitsTtl;
    }

    public void setHitsTtl(Duration hitsTtl) {
      this.hitsTtl = hitsTtl;
    }

    public int getHitsMaxSize() {
      return hitsMaxSize;
    }

    public void setHitsMaxSize(int hitsMaxSize) {
      this.hitsMaxSize = hitsMaxSize;
    }
  }
}
