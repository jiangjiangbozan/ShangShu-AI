package com.ai.api.knowledge.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 百炼向量化装配。
 *
 * <p>仅当 {@code rag.embedding.provider=dashscope} 时生效：注册 {@code @Primary} 的 EmbeddingModel， 让
 * VectorStore 与检索链路优先使用百炼， 同时保留自动装配的 OllamaEmbeddingModel 作为回退候选。 切回本地只需把 provider 改回 ollama，无需改代码。
 *
 * @author feat-rag-rerank
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "rag.embedding.provider", havingValue = "dashscope")
public class DashScopeEmbeddingConfig {

  @Bean
  @Primary
  public EmbeddingModel dashScopeEmbeddingModel(
      @Value("${rag.embedding.base-url:https://dashscope.aliyuncs.com}") String baseUrl,
      @Value("${rag.embedding.api-key:}") String apiKey,
      @Value("${rag.embedding.model:qwen3.7-text-embedding-flash}") String model,
      @Value("${rag.embedding.dimensions:768}") int dimensions,
      @Value("${rag.embedding.timeout-ms:15000}") long timeoutMs) {
    log.info("[embedding] 启用百炼向量化 model={} dimensions={} baseUrl={}", model, dimensions, baseUrl);
    return new DashScopeEmbeddingModel(baseUrl, apiKey, model, dimensions, timeoutMs);
  }
}
