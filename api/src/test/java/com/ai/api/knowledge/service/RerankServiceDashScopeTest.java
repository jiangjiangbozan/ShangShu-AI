package com.ai.api.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 百炼（DashScope）rerank 协议接入的真实调用验证。
 *
 * <p>需要真实 DashScope Key（环境变量 {@code DASHSCOPE_API_KEY}），未设置时自动跳过，避免 CI 无谓调用与计费。
 *
 * <p>验证三件事：协议能被正确识别、精排顺序符合语义、相关性分数被写回 metadata（供 trace 与客服兜底读取）。
 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = "sk-.*")
class RerankServiceDashScopeTest {

  @Test
  void rerankShouldSortByRelevanceAndWriteBackScore() {
    RerankService service = new RerankService(8000);
    ReflectionTestUtils.setField(service, "enabled", true);
    ReflectionTestUtils.setField(service, "protocol", "dashscope");
    ReflectionTestUtils.setField(service, "baseUrl", "https://dashscope.aliyuncs.com");
    ReflectionTestUtils.setField(service, "apiKey", System.getenv("DASHSCOPE_API_KEY"));
    ReflectionTestUtils.setField(service, "model", "qwen3.7-text-rerank");
    ReflectionTestUtils.setField(service, "topN", 3);
    ReflectionTestUtils.setField(service, "candidateN", 10);
    ReflectionTestUtils.setField(service, "maxCandidateChars", 1200);
    ReflectionTestUtils.setField(service, "instruct", "");

    List<Document> candidates =
        List.of(
            new Document("a", "Spring Boot 是一个 Java 后端框架", Map.of()),
            new Document("b", "尚枢 AI 支持可视化工作流编排，拖拽节点连线即可搭建流程", Map.of()),
            new Document("c", "RAG 检索包含向量、关键词、父文档、知识图谱四路召回", Map.of()));

    RerankService.RerankOutcome outcome = service.rerankWithStatus("工作流编排怎么用", candidates, 3);

    assertThat(outcome.status()).isEqualTo(RerankService.RerankOutcome.STATUS_SUCCESS);
    assertThat(outcome.documents()).hasSize(3);
    assertThat(outcome.documents().get(0).getId()).isEqualTo("b");

    double first = scoreOf(outcome.documents().get(0));
    double second = scoreOf(outcome.documents().get(1));
    assertThat(first).isGreaterThan(second);
  }

  private static double scoreOf(Document document) {
    Object raw = document.getMetadata().get(RerankService.METADATA_RERANK_SCORE);
    assertThat(raw).as("rerankScore 应被写回 metadata").isInstanceOf(Number.class);
    return ((Number) raw).doubleValue();
  }
}
