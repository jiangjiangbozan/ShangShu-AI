package com.ai.api.knowledge.embedding;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * 百炼（DashScope）OpenAI 兼容 Embedding 实现。
 *
 * <p>走 {@code {base-url}/compatible-mode/v1/embeddings}，与 OpenAI SDK 同一套协议， 因此无需额外的厂商 SDK；官方单次最多 20
 * 条输入、单条上限 8192 token，这里按 20 条分批并把超长文本截断。
 *
 * <p>维度通过请求体 {@code dimensions} 显式指定（768 / 512 / 256），与 ES 索引 dense_vector 的 dims 必须一致。
 *
 * @author feat-rag-rerank
 */
@Slf4j
public class DashScopeEmbeddingModel implements EmbeddingModel {

  /** 官方单次请求输入条数上限。 */
  private static final int MAX_BATCH_SIZE = 20;

  /** 单条输入字符上限，避免超长文本被服务端拒绝。 */
  private static final int MAX_INPUT_CHARS = 8000;

  private static final String EMBEDDINGS_PATH = "/compatible-mode/v1/embeddings";

  private final RestClient restClient;
  private final String model;
  private final int dimensions;

  public DashScopeEmbeddingModel(
      String baseUrl, String apiKey, String model, int dimensions, long timeoutMs) {
    Assert.hasText(apiKey, "百炼 embedding 需要配置 api-key（DASHSCOPE_API_KEY 或 RAG_EMBEDDING_API_KEY）");
    Assert.isTrue(dimensions > 0, "embedding 维度必须为正数");
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
    this.restClient =
        RestClient.builder()
            .baseUrl(stripTrailingSlash(baseUrl))
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .requestFactory(requestFactory)
            .build();
    this.model = model;
    this.dimensions = dimensions;
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    List<String> inputs = request.getInstructions();
    Assert.notEmpty(inputs, "embedding 输入不能为空");
    List<Embedding> results = new ArrayList<>(inputs.size());
    for (int from = 0; from < inputs.size(); from += MAX_BATCH_SIZE) {
      int to = Math.min(from + MAX_BATCH_SIZE, inputs.size());
      results.addAll(embedBatch(inputs.subList(from, to), from));
    }
    return new EmbeddingResponse(results);
  }

  @SuppressWarnings("unchecked")
  private List<Embedding> embedBatch(List<String> batch, int offset) {
    List<String> payload = new ArrayList<>(batch.size());
    for (String text : batch) {
      payload.add(truncate(text));
    }

    Map<String, Object> body =
        Map.of(
            "model", model,
            "input", payload,
            "dimensions", dimensions,
            "encoding_format", "float");

    Map<String, Object> response =
        restClient
            .post()
            .uri(EMBEDDINGS_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);

    if (response == null || !(response.get("data") instanceof List<?> data)) {
      throw new IllegalStateException("百炼 embedding 返回体缺少 data 字段: " + response);
    }

    List<Embedding> out = new ArrayList<>(batch.size());
    for (int i = 0; i < batch.size(); i++) {
      int globalIndex = offset + i;
      Object item = data.size() > i ? data.get(i) : null;
      if (!(item instanceof Map<?, ?> entry)) {
        throw new IllegalStateException(
            "百炼 embedding 返回条数不足: 期望 " + batch.size() + " 实际 " + data.size());
      }
      List<?> raw = (List<?>) entry.get("embedding");
      if (raw == null || raw.isEmpty()) {
        throw new IllegalStateException("百炼 embedding 第 " + globalIndex + " 条缺少向量");
      }
      float[] values = new float[raw.size()];
      for (int d = 0; d < raw.size(); d++) {
        values[d] = ((Number) raw.get(d)).floatValue();
      }
      if (values.length != dimensions) {
        throw new IllegalStateException(
            "百炼 embedding 维度与配置不一致: 期望 " + dimensions + " 实际 " + values.length);
      }
      out.add(new Embedding(values, globalIndex));
    }
    return out;
  }

  @Override
  public float[] embed(Document document) {
    Assert.notNull(document, "Document 不能为空");
    String text = document.getText();
    if (text == null || text.isBlank()) {
      // 图片/音视频等无文本内容的分片不参与语义检索，返回零向量占位
      return new float[dimensions];
    }
    return embed(text);
  }

  @Override
  public int dimensions() {
    return dimensions;
  }

  private static String truncate(String text) {
    if (text == null) {
      return "";
    }
    return text.length() <= MAX_INPUT_CHARS ? text : text.substring(0, MAX_INPUT_CHARS);
  }

  private static String stripTrailingSlash(String url) {
    String value = url == null ? "" : url.trim();
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
