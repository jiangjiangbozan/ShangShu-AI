package com.ai.api.chat.search;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

/**
 * 对话历史 ES 索引初始化。
 *
 * <p>应用启动时若 {@code chat_message_log} 索引不存在则自动创建。
 *
 * <p>索引映射设计： - content 字段使用 ik_max_word（索引时最细粒度分词）+ ik_smart（搜索时粗粒度分词）双分词器， 兼顾精确匹配和模糊召回。 - title
 * 字段同理，支持搜索会话标题。 - role 字段 keyword 类型，支持按角色筛选。
 *
 * @author add-p2-cache-search-rewrite
 */
@Slf4j
@Component
public class ChatSearchIndexInitializer {

  @Value("${elasticsearch.indices.chat-message-log:chat_message_log}")
  private String indexName;

  private final ElasticsearchOperations esOperations;

  public ChatSearchIndexInitializer(ElasticsearchOperations esOperations) {
    this.esOperations = esOperations;
  }

  @PostConstruct
  public void initIndex() {
    try {
      IndexOperations ops = esOperations.indexOps(IndexCoordinates.of(indexName));
      if (ops.exists()) {
        log.info("[ChatSearchIndexInitializer][索引已存在: {}]", indexName);
        return;
      }
      // 1. settings：1 主分片 1 副本
      Map<String, Object> settings =
          Map.of(
              "number_of_shards", 1,
              "number_of_replicas", 1);

      // 2. mapping：双分词器提升中文搜索质量
      Map<String, Object> contentField =
          Map.of(
              "type", "text",
              "analyzer", "ik_max_word",
              "search_analyzer", "ik_smart");

      Map<String, Object> titleField =
          Map.of(
              "type", "text",
              "analyzer", "ik_max_word",
              "search_analyzer", "ik_smart");

      Map<String, Object> properties =
          Map.ofEntries(
              Map.entry("messageId", Map.of("type", "keyword")),
              Map.entry("sessionId", Map.of("type", "keyword")),
              Map.entry("userId", Map.of("type", "keyword")),
              Map.entry("role", Map.of("type", "keyword")),
              Map.entry("content", contentField),
              Map.entry("title", titleField),
              Map.entry("modelId", Map.of("type", "keyword")),
              Map.entry(
                  "createTime",
                  Map.of(
                      "type", "date",
                      "format", "epoch_millis||yyyy-MM-dd HH:mm:ss")));

      Document mapping = Document.from(Map.of("properties", properties));
      boolean created = ops.create(settings, mapping);
      log.info("[ChatSearchIndexInitializer][索引创建{}: {}]", created ? "成功" : "失败", indexName);
    } catch (Exception e) {
      log.error(
          "[ChatSearchIndexInitializer][索引初始化失败: index={}, error={}]", indexName, e.getMessage());
    }
  }

  public String getIndexName() {
    return indexName;
  }

  public IndexCoordinates getIndexCoordinates() {
    return IndexCoordinates.of(indexName);
  }
}
