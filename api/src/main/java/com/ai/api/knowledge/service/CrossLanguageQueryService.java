package com.ai.api.knowledge.service;

import com.ai.api.translate.TranslationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 跨语言检索：query 语言预处理与召回结果回译（qwen-mt-uni）。
 *
 * <p>语料以中文为主（kb5 = K8s 中文官方文档），英文 query 直接向量化召回效果差。 本服务在检索入口把非目标语言 query 译成 target-lang（默认
 * zh）再进入召回/精排； 翻译失败自动降级用原文检索（不阻断主链路）。召回片段预览可按需回译回 query 语言（检索测试 trace 展示用，仅回译 ≤200 字符的片段预览，成本可控）。
 *
 * <p>额度边界（model-usage-policy.md §二）：仅调用 {@code qwen-mt-uni}； query 向量化（embedding）与 rerank
 * 额度禁用期与本服务无关——翻译在向量化之前发生。
 *
 * @author add-cross-language
 */
@Slf4j
@Service
public class CrossLanguageQueryService {

  private final TranslationService translationService;

  private final boolean enabled;
  private final String targetLang;
  private final boolean translateResults;

  public CrossLanguageQueryService(
      TranslationService translationService,
      @Value("${rag.cross-language.enabled:true}") boolean enabled,
      @Value("${rag.cross-language.target-lang:zh}") String targetLang,
      @Value("${rag.cross-language.translate-results:true}") boolean translateResults) {
    this.translationService = translationService;
    this.enabled = enabled;
    this.targetLang = targetLang;
    this.translateResults = translateResults;
  }

  /**
   * 预处理后的检索 query。
   *
   * @param originalQuery 用户原始 query
   * @param retrievalQuery 实际进入召回/精排的 query（译后；与原语言一致时等于 originalQuery）
   * @param detectedSourceLang 检测到的 query 语言 short code（无法判定时 unknown）
   * @param targetLang 语料语言 short code
   * @param translated 是否发生了翻译
   * @param status translated=翻译成功 / native=本身即目标语言 / fallback=翻译失败降级 / disabled=功能关闭
   */
  public record PreparedQuery(
      String originalQuery,
      String retrievalQuery,
      String detectedSourceLang,
      String targetLang,
      boolean translated,
      String status) {

    public static PreparedQuery passthrough(String query, String targetLang, String status) {
      return new PreparedQuery(query, query, "unknown", targetLang, false, status);
    }
  }

  /** 检索入口：把外文 query 译成语料语言；任何异常降级原文，绝不阻断检索。 */
  public PreparedQuery prepare(String rawQuery) {
    if (!enabled || rawQuery == null || rawQuery.isBlank()) {
      return PreparedQuery.passthrough(rawQuery, targetLang, "disabled");
    }
    String detected = detectLang(rawQuery);
    if (detected.equals(normalizeLang(targetLang))) {
      return new PreparedQuery(
          rawQuery, rawQuery, detected, normalizeLang(targetLang), false, "native");
    }
    try {
      String translated = translationService.translate(rawQuery, normalizeLang(targetLang));
      if (translated == null || translated.isBlank()) {
        return new PreparedQuery(
            rawQuery, rawQuery, detected, normalizeLang(targetLang), false, "fallback");
      }
      log.info(
          "[CrossLanguage][query 已译 {}→{} 原文={} 译文={}]",
          detected,
          normalizeLang(targetLang),
          abbreviate(rawQuery),
          abbreviate(translated));
      return new PreparedQuery(
          rawQuery, translated, detected, normalizeLang(targetLang), true, "translated");
    } catch (Exception e) {
      // 翻译服务不可用/额度禁用：降级原文检索，主链路不中断
      log.warn("[CrossLanguage][query 翻译失败，降级原文检索 query={}]", abbreviate(rawQuery), e);
      return new PreparedQuery(
          rawQuery, rawQuery, detected, normalizeLang(targetLang), false, "fallback");
    }
  }

  /**
   * 批量回译片段预览（trace 展示用）。任一批失败则整批跳过，预览保持原文。
   *
   * @param texts 片段预览文本列表（按召回顺序）
   * @param sourceLangQuery 原始 query 语言（回译目标 = query 语言）
   * @return 与入参等长、同序的回译结果（失败时元素为 null）
   */
  public List<String> backTranslatePreviews(List<String> texts, String sourceLangQuery) {
    List<String> result = new ArrayList<>(texts.size());
    if (!enabled || !translateResults || texts.isEmpty()) {
      for (String ignored : texts) {
        result.add(null);
      }
      return result;
    }
    String detected = detectLang(sourceLangQuery);
    if (detected.equals(normalizeLang(targetLang))) {
      for (String ignored : texts) {
        result.add(null);
      }
      return result;
    }
    try {
      List<String> translated = translationService.translateBatch(texts, detected);
      result.addAll(translated);
      while (result.size() < texts.size()) {
        result.add(null);
      }
    } catch (Exception e) {
      log.warn("[CrossLanguage][片段回译失败，trace 保持原文]", e);
      result.clear();
      for (String ignored : texts) {
        result.add(null);
      }
    }
    return result;
  }

  public boolean isTranslateResultsEnabled() {
    return translateResults;
  }

  /** 极简语言判定：含 CJK 统一表意文字即 zh，否则按 ASCII 占比判 en，其余 unknown。 服务端判定只需区分「中文 / 非中文」，无需引入语言检测库。 */
  static String detectLang(String text) {
    if (text == null || text.isBlank()) {
      return "unknown";
    }
    int han = 0;
    int asciiLetters = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
        han++;
      } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
        asciiLetters++;
      }
    }
    if (han > 0) {
      return "zh";
    }
    if (asciiLetters > 0) {
      return "en";
    }
    return "unknown";
  }

  private String normalizeLang(String lang) {
    if (lang == null || lang.isBlank()) {
      return "zh";
    }
    String lower = lang.trim().toLowerCase(Locale.ROOT);
    return switch (lower) {
      case "zh", "zh-cn", "chinese", "中文" -> "zh";
      case "en", "english", "英文" -> "en";
      default -> lower;
    };
  }

  private static String abbreviate(String text) {
    if (text == null) {
      return "";
    }
    return text.length() <= 40 ? text : text.substring(0, 40) + "…";
  }
}
