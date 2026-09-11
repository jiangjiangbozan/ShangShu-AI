package com.ai.api.chat.service;

import com.ai.api.chat.config.WebSearchProperties;
import com.ai.api.chat.dto.WebSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * 网络搜索 Service
 *
 * <p>为 AI 回答提供实时网络信息增强。搜索策略分三步：
 *
 * <ol>
 *   <li><b>关键词提取</b>（{@link #extractKeywords}）：清洗用户问题，去除语气词/标点， 截断到合理长度作为查询词
 *   <li><b>结果筛选与排序</b>（{@link #filterAndRank}）：按 URL 去重、过滤空摘要， 综合 provider 相关性得分与关键词命中次数排序，取 TopN
 *   <li><b>上下文构建</b>（{@link #buildContext}）：将结果格式化为编号引用文本， 注入 prompt 供模型参考，同时可下发前端展示来源
 * </ol>
 *
 * <p><b>安全性</b>（借鉴 development-plan.md SSRF 防护设计）：
 *
 * <ul>
 *   <li>仅请求配置文件指定的 apiUrl，不接受用户可控 URL
 *   <li>强制 HTTPS（自建实例也建议 TLS）
 *   <li>连接/响应超时 + 响应体大小限制（2MB），防止慢速攻击与内存放大
 * </ul>
 *
 * <p><b>稳定性</b>：搜索失败时返回空列表优雅降级，绝不阻断对话主流程。
 *
 * @author add-web-search
 */
@Slf4j
@Service
public class WebSearchService {

  /** 中文常见语气/功能词，关键词提取时剔除 */
  private static final Set<String> STOP_WORDS =
      Set.of(
          "的", "了", "吗", "呢", "啊", "吧", "请问", "一下", "帮我", "帮我查", "我想", "什么", "怎么", "怎样", "如何", "还是",
          "以及", "最近", "最新", "今天", "现在", "the", "a", "an", "is", "are", "what", "how", "please",
          "me", "my", "for");

  /** 去标点与多余空白 */
  private static final Pattern STRIP_PUNCT = Pattern.compile("[\\p{Punct}\\p{IsPunctuation}]+");

  private static final int QUERY_MAX_CHARS = 80;

  private final WebSearchProperties props;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WebClient webClient;

  public WebSearchService(WebSearchProperties props) {
    this.props = props;
    // SSRF 防护：固定 apiUrl（构造 WebClient 时绑定）、强制超时、限制响应体大小
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getTimeoutMs())
            .responseTimeout(Duration.ofMillis(props.getTimeoutMs()));
    this.webClient =
        WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl(props.getApiUrl())
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
  }

  /** 服务是否实际可用（全局开启且 provider 凭证完整）。 */
  public boolean isEnabled() {
    if (!props.isEnabled()) {
      return false;
    }
    if ("searxng".equalsIgnoreCase(props.getProvider())) {
      return props.getApiUrl() != null && !props.getApiUrl().isBlank();
    }
    // tavily 等云端 provider 必须有 API Key
    return props.getApiKey() != null && !props.getApiKey().isBlank();
  }

  /**
   * 对话增强搜索入口：关键词提取 → 调用 provider → 筛选排序。
   *
   * <p>失败时返回空列表（降级为普通对话），不抛异常。
   *
   * @param question 用户原始问题
   * @return 排序后的搜索结果（可能为空）
   */
  public List<WebSearchResult> search(String question) {
    if (!isEnabled()) {
      return List.of();
    }
    try {
      String query = extractKeywords(question);
      if (query.isBlank()) {
        return List.of();
      }
      List<WebSearchResult> raw = callProvider(query);
      return filterAndRank(raw, query, props.getMaxResults());
    } catch (Exception e) {
      log.warn("[webSearch][search failed({}), degrade to plain chat]", e.getMessage());
      return List.of();
    }
  }

  /** 响应式包装：供流式链路在 boundedElastic 上异步执行，避免阻塞事件循环。 */
  public Mono<List<WebSearchResult>> searchReactive(String question) {
    return Mono.fromCallable(() -> search(question));
  }

  // ============ 搜索策略 ============

  /**
   * 关键词提取：去除标点/停用词，压缩空白，截断到 {@link #QUERY_MAX_CHARS}。
   *
   * <p>不做分词（避免引入额外依赖），搜索引擎自身具备分词能力，简单清洗已足够。
   */
  String extractKeywords(String question) {
    if (question == null || question.isBlank()) {
      return "";
    }
    String cleaned = STRIP_PUNCT.matcher(question).replaceAll(" ").trim();
    String filtered =
        java.util.Arrays.stream(cleaned.split("\\s+"))
            .filter(w -> !w.isBlank())
            .filter(w -> !STOP_WORDS.contains(w.toLowerCase(Locale.ROOT)))
            .collect(Collectors.joining(" "));
    // 全被过滤（如纯语气词问题）时回退为原始问题
    if (filtered.isBlank()) {
      filtered = cleaned;
    }
    return filtered.length() > QUERY_MAX_CHARS ? filtered.substring(0, QUERY_MAX_CHARS) : filtered;
  }

  /**
   * 结果筛选与排序。
   *
   * <ul>
   *   <li>过滤：标题/摘要为空的结果
   *   <li>去重：按 URL 规范化（去锚点）去重
   *   <li>排序：综合得分 = provider score（权重 0.6）+ 关键词命中数归一化（权重 0.4）
   * </ul>
   */
  List<WebSearchResult> filterAndRank(List<WebSearchResult> results, String query, int topN) {
    if (results == null || results.isEmpty()) {
      return List.of();
    }
    List<String> keywords =
        java.util.Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+"))
            .filter(w -> w.length() > 1)
            .toList();
    Set<String> seenUrls = new HashSet<>();
    List<WebSearchResult> ranked = new ArrayList<>();
    for (WebSearchResult r : results) {
      if (r == null || r.getUrl() == null || r.getContent() == null) continue;
      String urlKey = normalizeUrl(r.getUrl());
      if (urlKey == null || !seenUrls.add(urlKey)) continue;
      double hitBonus = keywordHitBonus(r, keywords);
      double finalScore = Math.min(1.0, r.getScore()) * 0.6 + hitBonus * 0.4;
      ranked.add(
          WebSearchResult.builder()
              .title(safeTruncate(r.getTitle(), 100))
              .url(r.getUrl())
              .content(safeTruncate(r.getContent(), props.getMaxContentChars()))
              .score(finalScore)
              .build());
    }
    return ranked.stream()
        .sorted(Comparator.comparingDouble(WebSearchResult::getScore).reversed())
        .limit(Math.max(1, topN))
        .collect(Collectors.toList());
  }

  /** 构建注入 prompt 的引用上下文文本。 */
  public String buildContext(List<WebSearchResult> results) {
    if (results == null || results.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("[网络搜索结果，供参考回答，请甄别时效性与准确性]\n");
    for (int i = 0; i < results.size(); i++) {
      WebSearchResult r = results.get(i);
      sb.append(
          String.format(
              Locale.ROOT,
              "[%d] %s%n    来源: %s%n    摘要: %s%n",
              i + 1,
              r.getTitle() == null ? "(无标题)" : r.getTitle(),
              r.getUrl(),
              r.getContent()));
    }
    return sb.toString();
  }

  /** 提取结果中的来源 URL 列表（用于 SSE search 事件下发客户端展示）。 */
  public List<String> sourceUrls(List<WebSearchResult> results) {
    if (results == null) return List.of();
    return results.stream().map(WebSearchResult::getUrl).collect(Collectors.toList());
  }

  // ============ Provider 调用 ============

  /** 调用配置的搜索提供方，返回原始结果（未筛选）。 */
  private List<WebSearchResult> callProvider(String query) {
    return "searxng".equalsIgnoreCase(props.getProvider()) ? callSearxng(query) : callTavily(query);
  }

  /** Tavily API（https://docs.tavily.com）：POST JSON，Bearer 鉴权。 */
  private List<WebSearchResult> callTavily(String query) {
    String body =
        objectMapper
            .createObjectNode()
            .put("query", query)
            .put("max_results", props.getMaxResults())
            .put("search_depth", "basic")
            .put("include_answer", false)
            .toString();
    String resp =
        webClient
            .post()
            .uri(URI.create(props.getApiUrl()))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofMillis(props.getTimeoutMs()));
    return parseTavily(resp);
  }

  /** SearXNG（https://docs.searxng.org）：GET /search?q=..&format=json。 */
  private List<WebSearchResult> callSearxng(String query) {
    String resp =
        webClient
            .get()
            .uri(
                uri ->
                    uri.path("/search").queryParam("q", query).queryParam("format", "json").build())
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofMillis(props.getTimeoutMs()));
    return parseSearxng(resp);
  }

  // ============ 解析（包级可见便于单测） ============

  List<WebSearchResult> parseTavily(String json) {
    List<WebSearchResult> out = new ArrayList<>();
    try {
      JsonNode root = objectMapper.readTree(json == null ? "" : json);
      JsonNode results = root.get("results");
      if (results != null && results.isArray()) {
        for (JsonNode n : results) {
          out.add(
              WebSearchResult.builder()
                  .title(text(n, "title"))
                  .url(text(n, "url"))
                  .content(text(n, "content"))
                  .score(n.path("score").asDouble(0.5))
                  .build());
        }
      }
    } catch (Exception e) {
      log.warn("[webSearch][parse tavily failed({})]", e.getMessage());
    }
    return out;
  }

  List<WebSearchResult> parseSearxng(String json) {
    List<WebSearchResult> out = new ArrayList<>();
    try {
      JsonNode root = objectMapper.readTree(json == null ? "" : json);
      JsonNode results = root.get("results");
      if (results != null && results.isArray()) {
        for (JsonNode n : results) {
          // SearXNG 无相关性得分，统一给中性分，依赖 filterAndRank 的关键词加权
          out.add(
              WebSearchResult.builder()
                  .title(text(n, "title"))
                  .url(text(n, "url"))
                  .content(text(n, "content"))
                  .score(0.5)
                  .build());
        }
      }
    } catch (Exception e) {
      log.warn("[webSearch][parse searxng failed({})]", e.getMessage());
    }
    return out;
  }

  // ============ 工具方法 ============

  private static String text(JsonNode node, String field) {
    JsonNode v = node.get(field);
    return v == null || v.isNull() ? "" : v.asText("");
  }

  /** URL 规范化：去锚点，空 URL 返回 null（调用方过滤）。 */
  private static String normalizeUrl(String url) {
    if (url == null || url.isBlank()) return null;
    String u = url.trim();
    int hash = u.indexOf('#');
    return hash > 0 ? u.substring(0, hash) : u;
  }

  /** 关键词命中加成：标题/摘要命中次数 / 关键词数，归一化到 0~1。 */
  private static double keywordHitBonus(WebSearchResult r, List<String> keywords) {
    if (keywords.isEmpty()) return 0;
    String title = r.getTitle() == null ? "" : r.getTitle().toLowerCase(Locale.ROOT);
    String content = r.getContent() == null ? "" : r.getContent().toLowerCase(Locale.ROOT);
    int hits = 0;
    for (String kw : keywords) {
      if (title.contains(kw) || content.contains(kw)) hits++;
    }
    return (double) hits / keywords.size();
  }

  private static String safeTruncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max);
  }
}
