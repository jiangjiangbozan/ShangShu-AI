package com.ai.api.translate;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 文本翻译服务：统一走百炼 qwen-mt-uni（OpenAI 兼容端点）。
 *
 * <p>该模型要求请求体携带 {@code input.source_texts} 与目标语言，响应为 {@code Data.TranslatedTexts} 字符串数组，而非标准 chat
 * completions 结构，故这里用 JsonNode 解析，不套用 ChatClient。
 *
 * <p>缓存按「目标语言 + 原文」做进程内 LRU 近似（条目上限后整体清空），避免重复翻译同一段内容； 翻译失败抛出 {@link
 * ErrorCode#MODEL_TRANSLATE_FAILED}，由上层决定是否降级展示原文。
 *
 * @author cross-language
 */
@Slf4j
@Service
public class TranslationService {

  /** 单次请求最多携带的文本条数，超出后分批，避免请求体过大。 */
  private static final int MAX_BATCH_SIZE = 20;

  /** 进程内缓存条目上限。 */
  private static final int MAX_CACHE_ENTRIES = 2_000;

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  private final boolean enabled;
  private final String baseUrl;
  private final String apiKey;
  private final String model;

  public TranslationService(
      @Value("${translation.enabled:true}") boolean enabled,
      @Value("${translation.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
          String baseUrl,
      @Value("${translation.api-key:}") String apiKey,
      @Value("${translation.model:qwen-mt-uni}") String model) {
    this.enabled = enabled;
    this.baseUrl = trimTrailingSlash(baseUrl);
    this.apiKey = apiKey;
    this.model = model;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(10_000);
    factory.setReadTimeout(30_000);
    this.restTemplate = new RestTemplate(factory);
  }

  /** 翻译开关，关闭时调用方应直接展示原文。 */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 翻译单条文本。
   *
   * @param text 原文
   * @param targetLang 目标语言（支持 zh/en/ja/ko 等短码或 Chinese/English 全称）
   * @return 译文；原文为空时返回空串
   */
  public String translate(String text, String targetLang) {
    List<String> result = translateBatch(List.of(text), targetLang);
    return result.isEmpty() ? "" : result.get(0);
  }

  /**
   * 批量翻译（一次请求最多 {@value #MAX_BATCH_SIZE} 条，超出自动分批）。
   *
   * @param texts 原文列表
   * @param targetLang 目标语言
   * @return 与入参等长的译文列表
   */
  public List<String> translateBatch(List<String> texts, String targetLang) {
    if (texts == null || texts.isEmpty()) {
      return List.of();
    }
    if (!enabled) {
      throw new BusinessException(ErrorCode.MODEL_TRANSLATE_DISABLED);
    }
    if (apiKey == null || apiKey.isBlank()) {
      throw new BusinessException(ErrorCode.MODEL_API_KEY_MISSING);
    }
    String lang = normalizeTargetLang(targetLang);
    List<String> output = new ArrayList<>(texts.size());
    for (int start = 0; start < texts.size(); start += MAX_BATCH_SIZE) {
      int end = Math.min(start + MAX_BATCH_SIZE, texts.size());
      output.addAll(requestBatch(texts.subList(start, end), lang));
    }
    return output;
  }

  /**
   * 推断目标语言：中文内容译成英文，其余译成中文。
   *
   * @param text 原文
   * @return 目标语言全称
   */
  public String inferTargetLang(String text) {
    return containsChinese(text) ? "English" : "Chinese";
  }

  /**
   * 推断源语言（用于响应回显）。
   *
   * @param text 原文
   * @return 语言全称
   */
  public String inferSourceLang(String text) {
    return containsChinese(text) ? "Chinese" : "English";
  }

  /** 目标语言短码/别名归一化为模型可识别的全称。 */
  public static String normalizeTargetLang(String lang) {
    if (lang == null || lang.isBlank()) {
      return "Chinese";
    }
    return switch (lang.trim().toLowerCase()) {
      case "zh", "zh-cn", "zh_cn", "chinese", "中文" -> "Chinese";
      case "en", "en-us", "english", "英文" -> "English";
      case "ja", "jp", "japanese", "日文", "日语" -> "Japanese";
      case "ko", "korean", "韩文", "韩语" -> "Korean";
      case "fr", "french", "法文", "法语" -> "French";
      case "de", "german", "德文", "德语" -> "German";
      case "es", "spanish", "西班牙语" -> "Spanish";
      case "ru", "russian", "俄语" -> "Russian";
      case "ar", "arabic", "阿拉伯语" -> "Arabic";
      default -> lang.trim();
    };
  }

  /** 判断文本是否包含 CJK 汉字。 */
  private boolean containsChinese(String text) {
    if (text == null) {
      return false;
    }
    for (int i = 0; i < text.length(); i++) {
      if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
        return true;
      }
    }
    return false;
  }

  /** 发起一批翻译请求，命中缓存直接返回。 */
  private List<String> requestBatch(List<String> texts, String targetLang) {
    List<String> result = new ArrayList<>(texts.size());
    List<String> pending = new ArrayList<>();
    List<Integer> pendingIndex = new ArrayList<>();
    for (int i = 0; i < texts.size(); i++) {
      String text = texts.get(i);
      if (text == null || text.isBlank()) {
        result.add(text == null ? "" : text);
        continue;
      }
      String cached = cache.get(cacheKey(targetLang, text));
      if (cached != null) {
        result.add(cached);
      } else {
        pending.add(text);
        pendingIndex.add(i);
      }
    }
    if (pending.isEmpty()) {
      return result;
    }
    List<String> translated = callModel(pending, targetLang);
    for (int i = 0; i < pendingIndex.size(); i++) {
      int idx = pendingIndex.get(i);
      String value = translated.get(i);
      result.add(idx, value);
      if (cache.size() >= MAX_CACHE_ENTRIES) {
        cache.clear();
      }
      cache.put(cacheKey(targetLang, pending.get(i)), value);
    }
    return result;
  }

  /** 真正调用百炼翻译接口。 */
  private List<String> callModel(List<String> texts, String targetLang) {
    Map<String, Object> body =
        Map.of(
            "model",
            model,
            "input",
            Map.of(
                "source_texts", texts,
                "source_lang", "auto",
                "target_lang", targetLang));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
    headers.setBearerAuth(apiKey);
    String url = baseUrl + "/chat/completions";
    try {
      String raw =
          restTemplate
              .exchange(
                  url,
                  org.springframework.http.HttpMethod.POST,
                  new HttpEntity<>(body, headers),
                  String.class)
              .getBody();
      JsonNode root = objectMapper.readTree(raw);
      if (root.path("Success").isBoolean() && !root.path("Success").asBoolean()) {
        throw new BusinessException(ErrorCode.MODEL_TRANSLATE_FAILED);
      }
      JsonNode translatedTexts = root.path("Data").path("TranslatedTexts");
      if (!translatedTexts.isArray() || translatedTexts.size() != texts.size()) {
        log.warn("翻译返回结构异常：期望 {} 条，实际 {}，response={}", texts.size(), translatedTexts.size(), raw);
        throw new BusinessException(ErrorCode.MODEL_TRANSLATE_FAILED);
      }
      List<String> output = new ArrayList<>(texts.size());
      translatedTexts.forEach(node -> output.add(node.asText()));
      return output;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("调用翻译模型失败 url={} model={} size={}", url, model, texts.size(), e);
      throw new BusinessException(ErrorCode.MODEL_TRANSLATE_FAILED);
    }
  }

  private String cacheKey(String targetLang, String text) {
    return targetLang + ":" + text;
  }

  private String trimTrailingSlash(String url) {
    if (url == null) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
