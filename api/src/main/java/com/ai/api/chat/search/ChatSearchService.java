package com.ai.api.chat.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchPhraseQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

/**
 * 对话历史全局检索 Service。
 *
 * <p>搜索策略：
 *
 * <ul>
 *   <li>multi_match cross_fields：同时搜索 content 和 title，跨字段分词匹配，提升召回率
 *   <li>短查询（≤3 字）追加 match_phrase 至 should 子句做加分排序（不放入 must，避免空 title 字段导致过滤掉全部命中）
 *   <li>should 子句：用户角色加权，搜索时通常记得自己问的问题
 *   <li>ES 原生高亮：content + title 字段高亮
 *   <li>支持 role 角色筛选（user/assistant）
 *   <li>索引双分词器 ik_max_word（索引时）/ ik_smart（搜索时）
 *   <li>使用 NativeQuery（co.elastic.clients API）避免 StringQuery 双层 query 信封问题
 * </ul>
 */
@Slf4j
@Service
public class ChatSearchService {

  private final ElasticsearchOperations esOperations;
  private final ChatSearchIndexInitializer indexInitializer;

  private static final int SNIPPET_MAX_LENGTH = 200;
  private static final int HIGHLIGHT_FRAGMENT_SIZE = 200;
  private static final int HIGHLIGHT_NUM_FRAGMENTS = 3;

  public ChatSearchService(
      ElasticsearchOperations esOperations, ChatSearchIndexInitializer indexInitializer) {
    this.esOperations = esOperations;
    this.indexInitializer = indexInitializer;
  }

  /**
   * 全局检索对话历史。
   *
   * @param query 关键词（必填）
   * @param page 页码（从 1 开始）
   * @param size 每页条数
   * @param userId 限定用户 ID（可选）
   * @param sessionId 限定会话 ID（可选）
   * @param role 限定角色 user/assistant（可选）
   * @param startTime 起始时间（毫秒时间戳，含，可选）
   * @param endTime 截止时间（毫秒时间戳，含，可选）
   * @return 搜索结果（含 total、hits、hasMore）
   */
  public Map<String, Object> search(
      String query,
      int page,
      int size,
      Long userId,
      Long sessionId,
      String role,
      Long startTime,
      Long endTime) {

    int esSize = size + 1;
    Pageable pageable =
        PageRequest.of(Math.max(0, page - 1), esSize, Sort.by(Sort.Direction.DESC, "createTime"));

    NativeQuery nativeQuery = buildNativeQuery(query, userId, sessionId, role, startTime, endTime);
    nativeQuery.setPageable(pageable);

    try {
      SearchHits<Document> hits =
          esOperations.search(nativeQuery, Document.class, indexInitializer.getIndexCoordinates());

      long total = hits.getTotalHits();
      List<Map<String, Object>> results = new ArrayList<>();
      for (SearchHit<Document> hit : hits) {
        Document doc = hit.getContent();
        Map<String, Object> item = new HashMap<>();
        item.put("messageId", doc.get("messageId"));
        item.put("sessionId", doc.get("sessionId"));
        item.put("userId", doc.get("userId"));
        item.put("role", doc.get("role"));

        String content =
            doc.get("content") instanceof String s ? s : String.valueOf(doc.get("content"));
        item.put("content", content);

        String title = doc.get("title") instanceof String s ? s : null;
        item.put("title", title);

        item.put("modelId", doc.get("modelId"));
        item.put("createTime", doc.get("createTime"));
        float score = hit.getScore();
        item.put("score", Float.isNaN(score) ? 0.0f : score);

        List<String> highlights = hit.getHighlightField("content");
        List<String> titleHighlights = hit.getHighlightField("title");
        if (titleHighlights != null && !titleHighlights.isEmpty()) {
          item.put("titleHighlight", titleHighlights.getFirst());
        }
        if (highlights != null && !highlights.isEmpty()) {
          item.put("highlight", String.join(" ... ", highlights));
        } else {
          item.put("highlight", snippetAroundKeyword(content, query, SNIPPET_MAX_LENGTH));
        }

        results.add(item);
      }

      boolean hasMore = results.size() > size;
      if (hasMore) {
        results.remove(results.size() - 1);
      }

      Map<String, Object> response = new HashMap<>();
      response.put("total", total);
      response.put("page", page);
      response.put("size", size);
      response.put("hasMore", hasMore);
      response.put("hits", results);
      return response;
    } catch (Exception e) {
      log.error("[ChatSearchService][检索失败: query={}, error={}]", query, e.getMessage(), e);
      throw new IllegalStateException("对话历史检索失败: " + e.getMessage(), e);
    }
  }

  /** 使用 NativeQuery + co.elastic.clients API 构建查询，避免 StringQuery 双层 query 信封问题。 */
  NativeQuery buildNativeQuery(
      String query, Long userId, Long sessionId, String role, Long startTime, Long endTime) {

    // ---- must: multi_match cross_fields ----
    List<Query> mustClauses = new ArrayList<>();
    MultiMatchQuery multiMatch =
        MultiMatchQuery.of(
            m ->
                m.query(query)
                    .type(
                        co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.CrossFields)
                    .fields("content", "title")
                    .analyzer("ik_smart")
                    .minimumShouldMatch("70%"));
    mustClauses.add(multiMatch._toQuery());

    // ---- should: 角色加权 + 短查询 match_phrase boost ----
    // 注意：match_phrase 不能放入 must——历史消息 title 多为空字符串，
    // match_phrase 对空 title 必然不匹配，会导致 must 失败、整条命中被过滤（曾造成搜索返回 0）。
    // 因此短查询的短语精确匹配仅作 should 加分，提升命中排序而不强制过滤。
    List<Query> shouldClauses = new ArrayList<>();
    TermQuery userRoleBoost = TermQuery.of(t -> t.field("role").value("user").boost(1.2f));
    shouldClauses.add(userRoleBoost._toQuery());

    if (query.length() <= 3) {
      MatchPhraseQuery contentPhrase =
          MatchPhraseQuery.of(mp -> mp.query(query).field("content").boost(2.0f));
      shouldClauses.add(contentPhrase._toQuery());

      MatchPhraseQuery titlePhrase =
          MatchPhraseQuery.of(mp -> mp.query(query).field("title").boost(1.5f));
      shouldClauses.add(titlePhrase._toQuery());
    }

    // ---- filter: 精确过滤 ----
    List<Query> filterClauses = new ArrayList<>();
    if (userId != null) {
      filterClauses.add(
          TermQuery.of(t -> t.field("userId").value(String.valueOf(userId)))._toQuery());
    }
    if (sessionId != null) {
      filterClauses.add(
          TermQuery.of(t -> t.field("sessionId").value(String.valueOf(sessionId)))._toQuery());
    }
    if (role != null && !role.isBlank()) {
      filterClauses.add(TermQuery.of(t -> t.field("role").value(role))._toQuery());
    }
    if (startTime != null || endTime != null) {
      // ES Java Client 9.x: RangeQuery 变体模式，日期字段走 untyped 分支
      Query rangeQuery =
          co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery.of(
                  r ->
                      r.untyped(
                          u -> {
                            u.field("createTime");
                            if (startTime != null) {
                              u.gte(co.elastic.clients.json.JsonData.of(startTime.toString()));
                            }
                            if (endTime != null) {
                              u.lte(co.elastic.clients.json.JsonData.of(endTime.toString()));
                            }
                            return u;
                          }))
              ._toQuery();
      filterClauses.add(rangeQuery);
    }

    // ---- 组装 bool 查询 ----
    BoolQuery boolQuery =
        BoolQuery.of(
            b -> {
              b.must(mustClauses);
              b.should(shouldClauses);
              if (!filterClauses.isEmpty()) {
                b.filter(filterClauses);
              }
              return b;
            });

    // ---- 高亮配置（SDE 6.1 highlight API） ----
    HighlightParameters highlightParams =
        HighlightParameters.builder().withPreTags("<em>").withPostTags("</em>").build();

    HighlightField contentField =
        new HighlightField(
            "content",
            HighlightFieldParameters.builder()
                .withFragmentSize(HIGHLIGHT_FRAGMENT_SIZE)
                .withNumberOfFragments(HIGHLIGHT_NUM_FRAGMENTS)
                .build());

    HighlightField titleField =
        new HighlightField(
            "title",
            HighlightFieldParameters.builder()
                .withFragmentSize(100)
                .withNumberOfFragments(1)
                .build());

    Highlight highlight = new Highlight(highlightParams, List.of(contentField, titleField));

    // ---- 构建 NativeQuery ----
    NativeQuery nativeQuery = NativeQuery.builder().withQuery(boolQuery._toQuery()).build();
    nativeQuery.setHighlightQuery(new HighlightQuery(highlight, Document.class));
    return nativeQuery;
  }

  private String snippetAroundKeyword(String content, String keyword, int maxLen) {
    if (content == null || content.isEmpty()) return "";
    if (keyword == null || keyword.isEmpty()) return truncate(content, maxLen);

    String[] terms = keyword.trim().split("\\s+");
    int idx = -1;
    for (String term : terms) {
      idx = content.indexOf(term);
      if (idx >= 0) break;
    }
    if (idx < 0) return truncate(content, maxLen);

    int start = Math.max(0, idx - maxLen / 3);
    int end = Math.min(content.length(), start + maxLen);
    String snippet = content.substring(start, end);
    if (start > 0) snippet = "..." + snippet;
    if (end < content.length()) snippet = snippet + "...";
    return snippet;
  }

  private String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
  }
}
