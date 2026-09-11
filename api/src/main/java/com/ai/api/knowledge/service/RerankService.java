package com.ai.api.knowledge.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 混合检索精排 Service（add-3rd-4th-retrieval，对齐 Dify rerank_model 配置化思路）。
 *
 * <p>RRF 融合只解决"多路排名融合"，不改变"候选是否真的适合回答"；精排在融合后按 query 与候选文本的相关性二次打分，把真正能回答问题的片段顶到前面。
 *
 * <p>接入方式：HTTP 调用兼容 OpenAI 生态的 {@code /rerank} 接口（SiliconFlow / Jina / Cohere 均为此协议），请求体：
 *
 * <pre>{@code {"model":"...","query":"...","documents":["..."],"top_n":N}}</pre>
 *
 * 响应：{@code {"results":[{"index":0,"relevance_score":0.9},...]}}。
 *
 * <p>配置化（{@code rag.rerank.*}）对齐 Dify 的 rerank_model 语义：
 *
 * <ul>
 *   <li>{@code enabled}：总开关，关闭时跳过精排，直接返回 RRF 顺序
 *   <li>{@code base-url}：rerank 服务地址（不含 /rerank 后缀）
 *   <li>{@code api-key}：Bearer 鉴权密钥
 *   <li>{@code model}：精排模型名
 *   <li>{@code top-n}：精排后返回给上层的前 N 条
 *   <li>{@code candidate-n}：送入精排的候选上限（先取 RRF 前 candidate-n 条，控制成本）
 * </ul>
 *
 * <p>降级：开关关闭 / 未配置地址 / 调用失败 / 解析失败 → 原序返回，不阻断检索主链路。
 *
 * @author add-3rd-4th-retrieval
 */
@Slf4j
@Service
public class RerankService {

  /** 单条候选送入精排的文本长度上限，控制请求体大小。 */
  private static final int MAX_CANDIDATE_CHARS = 2000;

  private final RestClient restClient;

  @Value("${rag.rerank.enabled:false}")
  private boolean enabled;

  @Value("${rag.rerank.base-url:}")
  private String baseUrl;

  @Value("${rag.rerank.api-key:}")
  private String apiKey;

  @Value("${rag.rerank.model:bge-reranker-v2-m3}")
  private String model;

  /** 精排后返回给上层的前 N 条（需与最终 topK 对齐，取 min）。 */
  @Value("${rag.rerank.top-n:5}")
  private int topN;

  /** 送入精排的候选上限（先取 RRF 前 candidate-n 条，控制成本与延迟）。 */
  @Value("${rag.rerank.candidate-n:10}")
  private int candidateN;

  public RerankService(@Value("${rag.rerank.timeout-ms:3000}") long timeoutMs) {
    // 调用处使用绝对地址 {base-url}/rerank，RestClient 无需配 baseUrl
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
    factory.setReadTimeout(Duration.ofMillis(timeoutMs));
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * 对融合后的候选精排。
   *
   * @param query 用户原始问句
   * @param candidates RRF 融合后的候选（已按 RRF 分降序）
   * @return 精排后取前 N 条的文档列表；任何降级场景返回候选原序
   */
  public List<Document> rerank(String query, List<Document> candidates, int targetTopN) {
    if (!enabled || baseUrl == null || baseUrl.isBlank()) {
      return candidates;
    }
    if (query == null || query.isBlank() || candidates == null || candidates.isEmpty()) {
      return candidates;
    }
    // 1. 候选裁剪：RRF 前 candidate-n 条
    int cap = Math.max(1, Math.min(candidateN, candidates.size()));
    List<Document> subset = new ArrayList<>(candidates.subList(0, cap));
    try {
      List<String> texts = new ArrayList<>(subset.size());
      for (Document doc : subset) {
        String text = doc.getText();
        if (text != null && text.length() > MAX_CANDIDATE_CHARS) {
          text = text.substring(0, MAX_CANDIDATE_CHARS);
        }
        texts.add(text == null ? "" : text);
      }
      List<Double> scores = callRerank(query, texts);
      // 2. 按精排分数降序重排
      List<Integer> order = new ArrayList<>(subset.size());
      for (int i = 0; i < subset.size(); i++) {
        order.add(i);
      }
      order.sort(Comparator.comparingDouble((Integer i) -> scores.get(i)).reversed());
      List<Document> ranked = new ArrayList<>(subset.size());
      for (Integer i : order) {
        ranked.add(subset.get(i));
      }
      int limit = Math.min(Math.max(1, topN), Math.min(targetTopN, ranked.size()));
      List<Document> result = new ArrayList<>(ranked.subList(0, limit));
      log.debug(
          "[RerankService][精排完成: candidates={}, returned={}, model={}]",
          subset.size(),
          result.size(),
          model);
      return result;
    } catch (Exception e) {
      // 3. 降级：调用/解析失败返回候选原序
      log.warn("[RerankService][精排失败，降级返回 RRF 原序: query={}, error={}]", query, e.getMessage());
      return candidates;
    }
  }

  /** 调用 /rerank 接口，返回与入参 documents 一一对应的相关性分数。 */
  private List<Double> callRerank(String query, List<String> documents) {
    Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
    requestBody.put("model", model);
    requestBody.put("query", query);
    requestBody.put("documents", documents);
    requestBody.put("top_n", documents.size());

    Map<String, Object> body =
        restClient
            .post()
            .uri(baseUrl + "/rerank")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(requestBody)
            .retrieve()
            .body(Map.class);
    if (body == null) {
      throw new IllegalStateException("rerank 响应为空");
    }
    Object resultsObj = body.get("results");
    if (!(resultsObj instanceof List<?> results)) {
      throw new IllegalStateException("rerank 响应缺少 results 数组");
    }
    Double[] scores = new Double[documents.size()];
    for (Object item : results) {
      if (!(item instanceof Map<?, ?> m)) {
        continue;
      }
      Object indexObj = m.get("index");
      if (!(indexObj instanceof Number idx)) {
        continue;
      }
      int i = idx.intValue();
      if (i < 0 || i >= scores.length) {
        continue;
      }
      Object scoreObj = m.get("relevance_score");
      if (scoreObj == null) {
        scoreObj = m.get("score"); // 兼容部分实现用 score 字段
      }
      if (scoreObj instanceof Number n) {
        scores[i] = n.doubleValue();
      }
    }
    List<Double> result = new ArrayList<>(documents.size());
    for (Double s : scores) {
      // 缺失的索引给最低分，保证排序稳定
      result.add(s == null ? -1.0 : s);
    }
    return result;
  }
}
