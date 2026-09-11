package com.ai.api.chat.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchAutoConfiguration;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@link ChatSearchService} 集成测试。
 *
 * <p>使用 Spring 自动装配的 ES 客户端（Boot 4.1 {@code ElasticsearchClientAutoConfiguration} + {@code
 * DataElasticsearchAutoConfiguration}），连接本地运行中的 Elasticsearch（默认 {@code http://localhost:9200}，需已安装
 * IK 分词插件），并使用独立测试索引 {@code chat_message_log_it} 验证「索引 → 检索」端到端链路。
 *
 * <p>完整基础设施（ES）就绪时自动执行；无 ES 环境通过 {@code ES_HOST} 门控跳过，避免阻塞普通单元测试。
 *
 * @author fix-chat-search-es-query
 */
@SpringBootTest(
    classes = {
      ChatSearchService.class,
      ChatSearchIndexInitializer.class,
      ElasticsearchRestClientAutoConfiguration.class,
      ElasticsearchClientAutoConfiguration.class,
      DataElasticsearchAutoConfiguration.class
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "ES_HOST", matches = ".*")
@DisplayName("ChatSearchService ES 集成测试")
class ChatSearchServiceIntegrationTest {

  private static final String TEST_INDEX = "chat_message_log_it";

  @Autowired private ElasticsearchOperations esOperations;
  @Autowired private ChatSearchService chatSearchService;

  @DynamicPropertySource
  static void esProps(DynamicPropertyRegistry registry) {
    registry.add("spring.elasticsearch.uris", () -> "http://" + esHost() + ":9200");
    registry.add("elasticsearch.indices.chat-message-log", () -> TEST_INDEX);
  }

  private static String esHost() {
    String env = System.getenv("ES_HOST");
    return env != null && !env.isBlank() ? env : "localhost";
  }

  @BeforeAll
  void seed() {
    long now = System.currentTimeMillis();
    indexDoc(
        "it-msg-1",
        100L,
        200L,
        "user",
        "大家好，我是姜博瓒，这是用于验证全局搜索的集成测试消息。",
        "",
        "test-model",
        now - 60000);
    indexDoc("it-msg-2", 101L, 201L, "user", "这篇文档讲解编译原理实验报告的写作要点。", "编译原理实验报告", "test-model", now);
    esOperations.indexOps(IndexCoordinates.of(TEST_INDEX)).refresh();
  }

  @AfterAll
  void cleanup() {
    IndexOperations ops = esOperations.indexOps(IndexCoordinates.of(TEST_INDEX));
    if (ops.exists()) {
      ops.delete();
    }
  }

  private void indexDoc(
      String messageId,
      long sessionId,
      long userId,
      String role,
      String content,
      String title,
      String modelId,
      long createTime) {
    Map<String, Object> source =
        Map.of(
            "messageId", messageId,
            "sessionId", sessionId,
            "userId", userId,
            "role", role,
            "content", content,
            "title", title,
            "modelId", modelId,
            "createTime", createTime);
    IndexQuery indexQuery =
        new IndexQueryBuilder()
            .withId(messageId)
            .withSource(Document.from(source).toJson())
            .build();
    esOperations.index(indexQuery, IndexCoordinates.of(TEST_INDEX));
  }

  @Test
  @Order(1)
  @DisplayName("端到端: 3 字关键词能命中 title 为空的消息（回归空 title 被 match_phrase 过滤问题）")
  void search_shortKeyword_shouldHitMessageWithEmptyTitle() {
    Map<String, Object> result =
        chatSearchService.search("姜博瓒", 1, 10, null, null, null, null, null);

    assertThat(result).isNotNull();
    assertThat(((Number) result.get("total")).longValue()).isGreaterThanOrEqualTo(1);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hits = (List<Map<String, Object>>) result.get("hits");
    assertThat(hits)
        .as("短关键词应命中 title 为空的样本消息")
        .extracting(h -> h.get("messageId"))
        .contains("it-msg-1");
  }

  @Test
  @Order(2)
  @DisplayName("端到端: 长关键词 multi_match 召回命中")
  void search_longKeyword_shouldReturnHits() {
    Map<String, Object> result =
        chatSearchService.search("编译原理实验报告", 1, 10, null, null, null, null, null);

    assertThat(result).isNotNull();
    assertThat(((Number) result.get("total")).longValue()).isGreaterThanOrEqualTo(1);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hits = (List<Map<String, Object>>) result.get("hits");
    assertThat(hits).extracting(h -> h.get("messageId")).contains("it-msg-2");
  }

  @Test
  @Order(3)
  @DisplayName("端到端: userId 过滤生效，仅返回对应用户消息")
  void search_withUserIdFilter_shouldOnlyReturnUserMessages() {
    Map<String, Object> result =
        chatSearchService.search("测试", 1, 10, 200L, null, null, null, null);

    assertThat(result).isNotNull();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hits = (List<Map<String, Object>>) result.get("hits");
    assertThat(hits).isNotEmpty();
    assertThat(hits)
        .extracting(h -> h.get("userId"))
        .allMatch(u -> "200".equals(String.valueOf(u)));
  }

  @Test
  @Order(4)
  @DisplayName("端到端: 无匹配关键词返回空结果但不抛异常")
  void search_noMatch_shouldReturnEmpty() {
    Map<String, Object> result =
        chatSearchService.search("绝对不存在的关键词xyzzy", 1, 10, null, null, null, null, null);

    assertThat(result).isNotNull();
    assertThat(((Number) result.get("total")).longValue()).isZero();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hits = (List<Map<String, Object>>) result.get("hits");
    assertThat(hits).isEmpty();
  }

  @Test
  @Order(5)
  @DisplayName("端到端: 命中结果应包含高亮片段")
  void search_hitsShouldContainHighlight() {
    Map<String, Object> result =
        chatSearchService.search("姜博瓒", 1, 10, null, null, null, null, null);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hits = (List<Map<String, Object>>) result.get("hits");
    assertThat(hits).isNotEmpty();
    assertThat(hits)
        .extracting(h -> h.get("highlight"))
        .anyMatch(h -> h != null && !String.valueOf(h).isBlank());
  }

  @Test
  @Order(6)
  @DisplayName("端到端: role 过滤为 assistant 时不应命中 user 消息")
  void search_roleAssistant_shouldExcludeUserMessages() {
    Map<String, Object> result =
        chatSearchService.search("测试", 1, 10, null, null, "assistant", null, null);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hits = (List<Map<String, Object>>) result.get("hits");
    // 样本数据均为 user 角色，assistant 过滤后不应包含 user 消息
    assertThat(hits).isEmpty();
  }

  @Test
  @Order(7)
  @DisplayName("端到端: 时间范围过滤生效")
  void search_withTimeRange_shouldFilterByCreateTime() {
    long now = System.currentTimeMillis();
    // 只取 10 秒前到当前的消息（仅命中 it-msg-2），排除更早的 it-msg-1。
    // 关键词用 it-msg-2 内容独有的"编译原理"（it-msg-1 不含该词，不受时间过滤干扰）。
    Map<String, Object> result =
        chatSearchService.search("编译原理", 1, 10, null, null, null, now - 10000, now);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hits = (List<Map<String, Object>>) result.get("hits");
    assertThat(hits).extracting(h -> h.get("messageId")).contains("it-msg-2");
  }
}
