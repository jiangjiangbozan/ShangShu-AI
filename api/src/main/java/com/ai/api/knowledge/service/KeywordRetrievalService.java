package com.ai.api.knowledge.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * BM25 关键词检索 Service（add-multi-retriever）。
 *
 * <p>与向量检索组成"多检索器"混合召回（对齐 Dify RetrievalMethod.HYBRID_SEARCH 的"向量 + 全文并发"思想）：
 *
 * <ul>
 *   <li>semantic 路：ES dense_vector kNN 语义召回（{@code vectorStore.similaritySearch}）
 *   <li>fulltext 路：本类对 {@code content} 字段执行 ES {@code match} 查询，走 BM25 相关性评分， 擅长专有名词 / 精确词 /
 *       编号等字面命中，与向量路互补
 * </ul>
 *
 * <p>两路结果统一交回 {@link RagRetrievalService} 用 RRF（Reciprocal Rank Fusion）按排名融合， 不依赖各自的原始分数，天然规避"BM25
 * 分数与余弦相似度量纲不同、不能直接相加"的问题。
 *
 * <p>降级：ES 调用失败时返回空列表，RRF 融合跳过该路，不影响其他路。
 *
 * @author add-multi-retriever
 */
@Slf4j
@Service
public class KeywordRetrievalService {

  private final ElasticsearchClient esClient;
  private final String indexName;

  public KeywordRetrievalService(
      ElasticsearchClient esClient,
      @Value("${spring.ai.vectorstore.elasticsearch.index-name:ai_agent_knowledge}")
          String indexName) {
    this.esClient = esClient;
    this.indexName = indexName;
  }

  /**
   * BM25 全文检索。
   *
   * @param query 检索问句
   * @param topK 返回条数
   * @param kbId 知识库 ID，为 null 时不做知识库过滤（与向量路行为一致）
   * @return 命中的 chunk 列表，ES {@code _id} 作为 {@code Document.id}（与向量路 vectorId 一致，便于 RRF 按 id 聚合）
   */
  public List<Document> search(String query, int topK, Long kbId) {
    if (query == null || query.isBlank() || topK <= 0) {
      return List.of();
    }
    try {
      SearchResponse<Map> response =
          esClient.search(
              s -> {
                s.index(indexName).size(topK);
                s.query(
                    q ->
                        q.bool(
                            b -> {
                              b.must(m -> m.match(t -> t.field("content").query(query)));
                              if (kbId != null) {
                                // ES 动态映射下字符串字段带 .keyword 子字段，term 精确过滤
                                b.filter(
                                    f ->
                                        f.term(
                                            t ->
                                                t.field("metadata.knowledgeBaseId.keyword")
                                                    .value(String.valueOf(kbId))));
                              }
                              return b;
                            }));
                return s;
              },
              Map.class);
      List<Document> docs = new ArrayList<>();
      for (Hit<Map> hit : response.hits().hits()) {
        if (hit.source() == null) {
          continue;
        }
        Object contentObj = hit.source().get("content");
        if (contentObj == null) {
          continue;
        }
        Map<String, Object> metadata = new HashMap<>();
        Object metadataObj = hit.source().get("metadata");
        if (metadataObj instanceof Map<?, ?> m) {
          m.forEach((k, v) -> metadata.put(String.valueOf(k), v));
        }
        if (hit.score() != null) {
          // BM25 原始分仅作日志/排查，不参与 RRF（RRF 只看排名）
          metadata.put("bm25Score", hit.score());
        }
        docs.add(
            Document.builder()
                .id(hit.id())
                .text(String.valueOf(contentObj))
                .metadata(metadata)
                .build());
      }
      log.debug(
          "[KeywordRetrievalService][BM25 检索完成: index={}, query={}, hits={}]",
          indexName,
          query,
          docs.size());
      return docs;
    } catch (IOException e) {
      log.warn(
          "[KeywordRetrievalService][BM25 全文检索失败，跳过该路: query={}, error={}]", query, e.getMessage());
      return List.of();
    }
  }
}
