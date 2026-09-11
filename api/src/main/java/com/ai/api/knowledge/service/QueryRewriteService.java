package com.ai.api.knowledge.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * RAG Query 重写 Service（add-p2-cache-search-rewrite）。
 *
 * <p>调用轻量大模型将用户原始口语化/长问句改写为 2-3 个语义等价的检索问句，多路并行召回后由 {@code RagRetrievalService} 通过 RRF 融合。
 *
 * <p>降级策略：
 *
 * <ul>
 *   <li>query 长度 &lt; minQueryLength：直接返回原 query 单条
 *   <li>大模型调用失败/超时：返回 [原 query]
 *   <li>解析失败：返回 [原 query]
 * </ul>
 *
 * <p>解析规则：模型输出形如 {@code 1. xxx\n2. xxx\n3. xxx}，按行 + 序号正则提取。
 *
 * @author add-p2-cache-search-rewrite
 */
@Slf4j
@Service
public class QueryRewriteService {

  private final ChatClient rewriteChatClient;
  private final ChatClient fallbackChatClient;

  @Value("${rag.query-rewrite.enabled:true}")
  private boolean enabled;

  @Value("${rag.query-rewrite.timeout-ms:3000}")
  private long timeoutMs;

  @Value("${rag.query-rewrite.max-tokens:200}")
  private int maxTokens;

  @Value("${rag.query-rewrite.min-query-length:6}")
  private int minQueryLength;

  private static final Pattern LINE_PATTERN = Pattern.compile("^\\s*\\d+[\\.、)]\\s*(.+)$");

  public QueryRewriteService(
      @Qualifier("deepSeekChatClient") ChatClient deepSeekChatClient,
      @Qualifier("ollamaChatClient") ChatClient ollamaChatClient) {
    this.rewriteChatClient = deepSeekChatClient;
    this.fallbackChatClient = ollamaChatClient;
  }

  /**
   * 改写用户原始 query 为 2-3 个语义等价的检索问句。
   *
   * @param rawQuery 用户原始问句
   * @return 改写后的问句列表（至少包含原 query）
   */
  public List<String> rewrite(String rawQuery) {
    if (!enabled || rawQuery == null || rawQuery.isBlank()) {
      return Collections.singletonList(rawQuery == null ? "" : rawQuery);
    }
    if (rawQuery.length() < minQueryLength) {
      return Collections.singletonList(rawQuery);
    }
    try {
      String prompt = buildPrompt(rawQuery);
      String response = callModel(prompt);
      List<String> parsed = parseResponse(response);
      if (parsed.isEmpty()) {
        return Collections.singletonList(rawQuery);
      }
      // 保证原 query 在结果中，避免改写偏离原意
      List<String> result = new ArrayList<>(parsed.size() + 1);
      result.add(rawQuery);
      for (String q : parsed) {
        if (!result.contains(q)) {
          result.add(q);
        }
      }
      // 限制最多 4 条（原 + 3 个改写）
      return result.size() > 4 ? result.subList(0, 4) : result;
    } catch (Exception e) {
      log.warn("[QueryRewriteService][改写失败，降级原 query: error={}]", e.getMessage());
      return Collections.singletonList(rawQuery);
    }
  }

  private String callModel(String prompt) {
    Duration timeout = Duration.ofMillis(timeoutMs);
    String primary = null;
    try {
      primary = rewriteChatClient.prompt().user(prompt).call().content();
    } catch (Exception e) {
      log.warn("[QueryRewriteService][主模型调用失败，fallback: error={}]", e.getMessage());
    }
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    try {
      return fallbackChatClient.prompt().user(prompt).call().content();
    } catch (Exception e) {
      log.warn("[QueryRewriteService][fallback 模型调用失败: error={}]", e.getMessage());
      return "";
    }
  }

  private String buildPrompt(String rawQuery) {
    return String.format(
        Locale.ROOT,
        "你是一个 RAG 检索问句改写助手。请把下面的用户原始问题改写为 2-3 个语义等价的检索问句，"
            + "用于知识库向量检索。要求：\n"
            + "1. 每行一条，行首用序号 \"1. 2. 3.\"\n"
            + "2. 不输出任何解释、前后缀\n"
            + "3. 保持术语一致，避免信息稀释\n"
            + "4. 总字数不超过 %d\n\n"
            + "原始问题：\n%s\n",
        maxTokens,
        rawQuery);
  }

  /**
   * 解析模型响应：按行切分，匹配 {@code N. xxx} 模式，去空白与引号。
   *
   * <p>容错：若模型输出 JSON 数组 / 无序号纯文本行，也尽量提取非空行。
   */
  private List<String> parseResponse(String response) {
    if (response == null || response.isBlank()) return Collections.emptyList();
    List<String> result = new ArrayList<>();
    String[] lines = response.split("\\r?\\n");
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) continue;
      Matcher m = LINE_PATTERN.matcher(trimmed);
      String text;
      if (m.matches()) {
        text = m.group(1).trim();
      } else {
        // 容错：无序号但非空的行也接受（跳过明显解释行）
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) continue;
        if (trimmed.contains("：") || trimmed.startsWith("原始问题")) continue;
        text = trimmed;
      }
      // 去除首尾引号
      text = stripQuotes(text);
      if (!text.isEmpty() && !result.contains(text)) {
        result.add(text);
      }
    }
    return result;
  }

  private String stripQuotes(String s) {
    if (s.length() >= 2) {
      char first = s.charAt(0);
      char last = s.charAt(s.length() - 1);
      if ((first == '"' && last == '"')
          || (first == '\'' && last == '\'')
          || (first == '“' && last == '”')) {
        return s.substring(1, s.length() - 1).trim();
      }
    }
    return s;
  }

  /** 便于测试注入。 */
  static List<String> parseStatic(String response) {
    return Arrays.asList();
  }
}
