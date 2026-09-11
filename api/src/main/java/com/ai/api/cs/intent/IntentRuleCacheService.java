package com.ai.api.cs.intent;

import com.ai.api.cs.entity.CsIntent;
import com.ai.api.cs.mapper.CsIntentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 意图规则缓存（意图识别关键词规则配置化）。
 *
 * <p>从 {@code cs_intent} 表加载启用意图及其触发关键词到内存，支持定时刷新实现运营热更新： 运营直接在数据库改 {@code keywords} / 新增意图，最多
 * {@code cs.intent-rule-refresh-ms} （默认 30s）后规则生效，无需发版。
 *
 * <p>构造时先加载一次，保证首次调用即有规则；刷新失败保留旧规则，不阻塞线上。
 *
 * @author add-customer-service
 */
@Slf4j
@Service
public class IntentRuleCacheService {

  private final CsIntentMapper csIntentMapper;

  /** intent name → 意图规则（关键词已归一化）。 */
  private volatile Map<String, IntentRule> rules = Map.of();

  public IntentRuleCacheService(CsIntentMapper csIntentMapper) {
    this.csIntentMapper = csIntentMapper;
    refresh();
  }

  @Scheduled(fixedDelayString = "${cs.intent-rule-refresh-ms:30000}")
  public void refresh() {
    try {
      LambdaQueryWrapper<CsIntent> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(CsIntent::getEnabled, 1);
      List<CsIntent> intents = csIntentMapper.selectList(wrapper);
      Map<String, IntentRule> newRules = new ConcurrentHashMap<>();
      for (CsIntent intent : intents) {
        newRules.put(
            intent.getName(),
            new IntentRule(
                intent.getName(),
                intent.getDescription(),
                parseKeywords(intent.getKeywords()),
                intent.getSlotsSchema(),
                intent.getFallbackIntent()));
      }
      this.rules = newRules;
      log.info("[IntentRuleCache][refresh total({})]", newRules.size());
    } catch (Exception e) {
      log.warn("[IntentRuleCache][refresh failed, keep old rules: error({})]", e.getMessage());
    }
  }

  /** 全部启用中的意图规则。 */
  public List<IntentRule> getAllEnabled() {
    return List.copyOf(rules.values());
  }

  /** 文本归一化：全角→半角、去空白与标点、转小写。 */
  public static String normalize(String s) {
    if (s == null) return "";
    StringBuilder sb = new StringBuilder(s.length());
    for (char c : s.toCharArray()) {
      if (c == '\u3000') {
        c = ' ';
      } else if (c >= '\uFF01' && c <= '\uFF5E') {
        c = (char) (c - 0xFEE0); // 全角字符 → 半角
      }
      sb.append(c);
    }
    return sb.toString().toLowerCase().replaceAll("[\\s\\p{Punct}]", "");
  }

  private List<String> parseKeywords(String keywords) {
    if (keywords == null || keywords.isBlank()) {
      return List.of();
    }
    return Arrays.stream(keywords.split(","))
        .map(String::trim)
        .filter(k -> !k.isEmpty())
        .map(IntentRuleCacheService::normalize)
        .distinct()
        .toList();
  }

  /** 意图规则（name + 描述 + 归一化触发关键词 + 槽位 schema + 兜底意图）。 */
  public record IntentRule(
      String name,
      String description,
      List<String> keywords,
      String slotsSchema,
      String fallbackIntent) {}
}
