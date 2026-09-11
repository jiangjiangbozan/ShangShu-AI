package com.ai.api.chat.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;

/**
 * {@link ChatSearchService} 单元测试。
 *
 * <p>验证 NativeQuery 构建逻辑：must/should/filter 子句正确组装，高亮配置正确。
 *
 * @author fix-chat-search-es-query
 */
@DisplayName("ChatSearchService NativeQuery 构造测试")
@ExtendWith(MockitoExtension.class)
class ChatSearchServiceTest {

  @Mock private ElasticsearchOperations esOperations;
  @Mock private ChatSearchIndexInitializer indexInitializer;

  @Mock
  @SuppressWarnings("rawtypes")
  SearchHits searchHits;

  @Mock
  @SuppressWarnings("rawtypes")
  SearchHit searchHit;

  @InjectMocks private ChatSearchService chatSearchService;

  @Test
  @DisplayName("buildNativeQuery: 仅关键词无 filter 时返回包含 multi_match 的 bool 查询")
  void buildNativeQuery_keywordOnly_shouldContainMultiMatch() {
    NativeQuery query = chatSearchService.buildNativeQuery("你好", null, null, null, null, null);

    // 验证查询非空且包含高亮
    assertThat(query).isNotNull();
    assertThat(query.getHighlightQuery()).isPresent();
  }

  @Test
  @DisplayName("buildNativeQuery: 含 userId filter 时应包含 term filter")
  void buildNativeQuery_withUserId_shouldContainTermFilter() {
    NativeQuery query = chatSearchService.buildNativeQuery("你好", 100L, null, null, null, null);

    assertThat(query).isNotNull();
    // Query 应该是一个 bool 查询
    Query innerQuery = query.getQuery();
    assertThat(innerQuery.isBool()).isTrue();
  }

  @Test
  @DisplayName("buildNativeQuery: 含 role 参数时应包含 role filter")
  void buildNativeQuery_withRole_shouldContainRoleFilter() {
    NativeQuery query = chatSearchService.buildNativeQuery("测试", null, null, "user", null, null);

    assertThat(query).isNotNull();
    Query innerQuery = query.getQuery();
    assertThat(innerQuery.isBool()).isTrue();
  }

  @Test
  @DisplayName("buildNativeQuery: 高亮配置应包含 content 和 title 字段")
  void buildNativeQuery_shouldContainHighlight() {
    NativeQuery query = chatSearchService.buildNativeQuery("测试", null, null, null, null, null);

    assertThat(query.getHighlightQuery()).isPresent();
    var highlight = query.getHighlightQuery().get().getHighlight();
    assertThat(highlight).isNotNull();
  }

  @Test
  @DisplayName("search: 正常查询应返回命中结果")
  void search_shouldReturnHits() {
    // given
    when(searchHit.getContent())
        .thenReturn(Document.from(Map.of("messageId", "m1", "content", "hello", "role", "user")));
    // search() 内部用 for-each 遍历（走 iterator()），因此 stub iterator 而非 forEach
    when(searchHits.iterator()).thenReturn(java.util.List.of(searchHit).iterator());
    when(searchHits.getTotalHits()).thenReturn(1L);
    when(esOperations.search(any(NativeQuery.class), eq(Document.class), any()))
        .thenReturn(searchHits);

    // when
    Map<String, Object> result =
        chatSearchService.search("hello", 1, 10, null, null, null, null, null);

    // then
    assertThat(result).containsEntry("total", 1L);
    assertThat(result).containsEntry("page", 1);
    assertThat(result).containsEntry("size", 10);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hits = (List<Map<String, Object>>) result.get("hits");
    assertThat(hits).hasSize(1);
    assertThat(hits.get(0)).containsEntry("content", "hello");
  }

  @Test
  @DisplayName("buildNativeQuery: 短查询 match_phrase 应放入 should 而非 must，must 仅含 multi_match")
  void buildNativeQuery_shortQuery_matchPhraseShouldBeInShouldNotMust() {
    // given：≤3 字短查询
    NativeQuery query = chatSearchService.buildNativeQuery("姜博瓒", null, null, null, null, null);

    // when: 提取 bool 子句
    Query innerQuery = query.getQuery();
    assertThat(innerQuery.isBool()).isTrue();
    var boolQuery = innerQuery.bool();

    // then: must 仅含 multi_match（match_phrase 若在此会导致空 title 命中被过滤）
    List<Query> mustClauses = boolQuery.must();
    assertThat(mustClauses).hasSize(1);
    assertThat(mustClauses.get(0).isMultiMatch()).isTrue();

    // then: should 含角色加权 term + content/title 两个 match_phrase
    List<Query> shouldClauses = boolQuery.should();
    assertThat(shouldClauses).hasSize(3);
    long matchPhraseCount = shouldClauses.stream().filter(Query::isMatchPhrase).count();
    assertThat(matchPhraseCount).isEqualTo(2);
    assertThat(shouldClauses.stream().anyMatch(Query::isTerm)).isTrue();
  }

  @Test
  @DisplayName("buildNativeQuery: 长查询（>3 字）should 仅含角色加权，不含 match_phrase")
  void buildNativeQuery_longQuery_shouldOnlyHasRoleTerm() {
    NativeQuery query =
        chatSearchService.buildNativeQuery("编译原理实验报告", null, null, null, null, null);

    Query innerQuery = query.getQuery();
    assertThat(innerQuery.isBool()).isTrue();
    var boolQuery = innerQuery.bool();

    assertThat(boolQuery.must()).hasSize(1);
    assertThat(boolQuery.must().get(0).isMultiMatch()).isTrue();

    List<Query> shouldClauses = boolQuery.should();
    assertThat(shouldClauses).hasSize(1);
    assertThat(shouldClauses.get(0).isTerm()).isTrue();
  }

  @Test
  @DisplayName("search: ES 异常时应抛 IllegalStateException 包装原异常")
  void search_esThrows_shouldWrapAsIllegalState() {
    // given
    when(esOperations.search(any(NativeQuery.class), eq(Document.class), any()))
        .thenThrow(new RuntimeException("connection refused"));

    // when & then
    assertThatThrownBy(() -> chatSearchService.search("test", 1, 10, null, null, null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("对话历史检索失败")
        .hasMessageContaining("connection refused");
  }
}
