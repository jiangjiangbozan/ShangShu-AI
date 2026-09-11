package com.ai.api.cs.intent;

import com.ai.api.agent.skill.Skill;
import com.ai.api.agent.skill.SkillType;
import com.ai.api.cs.config.CustomerServiceProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 意图识别 Skill（add-customer-service）。
 *
 * <p>关键词规则 + LLM 双驱动，支持<b>多意图输出</b>：
 *
 * <ol>
 *   <li>关键词规则优先：意图与触发关键词来自 {@code cs_intent} 表（{@link IntentRuleCacheService}
 *       缓存，可热更新）。<b>顺序匹配修复</b>：不再只取第一个命中，而是收集所有命中的意图， 按命中关键词数降序（稳定保持规则顺序），避免"一句话命中多意图"误判
 *   <li>未命中调 LLM 输出多意图 JSON： {@code
 *       {"intents":[{"intent":"<str>","confidence":<double>,"slots_needed":[<str>]}]}}， 兼容旧单意图格式
 *   <li>过滤 confidence < 阈值（{@code cs.intent-confidence-threshold}）的意图；全部低于阈值或 LLM/解析失败 → 降级
 *       fallback_intent
 * </ol>
 *
 * <p>多轮上下文：支持入参 {@code history}（[{role, content}]），拼进 prompt 供 LLM 理解语境， 避免"当前一句"孤立识别。
 *
 * @author add-customer-service
 */
@Slf4j
@Component("intentClassifierSkill")
@RequiredArgsConstructor
public class IntentClassifierSkill {

  private final Map<String, ChatClient> chatClientRegistry;
  private final CustomerServiceProperties properties;
  private final IntentRuleCacheService ruleCacheService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Skill(
      name = "intent_classifier",
      type = SkillType.LOCAL,
      description = "意图识别：输入 query + history，输出 intents（多意图）+ 主 intent + confidence + slots_needed")
  public Map<String, Object> classify(Map<String, Object> input) {
    Object queryObj = input.get("query");
    if (queryObj == null) {
      return errorResult("query is required");
    }
    String rawQuery = queryObj.toString();
    // 归一化：全角→半角、去空白标点、转小写；关键词在缓存加载时已同样归一化（含小写），双方一致避免英文大小写误匹配
    String query = IntentRuleCacheService.normalize(rawQuery);

    @SuppressWarnings("unchecked")
    List<Map<String, String>> history =
        input.get("history") instanceof List
            ? (List<Map<String, String>>) input.get("history")
            : List.of();

    // 1. 关键词规则多意图匹配：收集所有命中意图，按命中关键词数降序
    List<IntentResult> ruleHits = matchByKeywords(query);
    if (!ruleHits.isEmpty()) {
      log.debug("[IntentClassifier][rule hit][query({}) intents({})]", query, ruleHits);
      return toMap(ruleHits);
    }

    // 2. 调 LLM（多意图）
    try {
      ChatClient client = resolveChatClient();
      if (client == null) {
        log.warn("[IntentClassifier][no chat client available, fallback][query({})]", query);
        return fallbackResult();
      }
      String prompt = buildLlmPrompt(rawQuery, history);
      String llmOutput = client.prompt().user(prompt).call().content();
      List<IntentResult> parsed = parseLlmOutput(llmOutput);
      if (parsed == null || parsed.isEmpty()) {
        log.warn("[IntentClassifier][llm parse failed][fallback][query({})]", query);
        return fallbackResult();
      }
      // 低置信度过滤：保留 >= 阈值的意图；全部低于阈值 → 兜底
      List<IntentResult> filtered =
          parsed.stream()
              .filter(r -> r.getConfidence() >= properties.getIntentConfidenceThreshold())
              .toList();
      if (filtered.isEmpty()) {
        log.info(
            "[IntentClassifier][low confidence < threshold({})][fallback][query({})]",
            properties.getIntentConfidenceThreshold(),
            query);
        return fallbackResult();
      }
      return toMap(filtered);
    } catch (Exception e) {
      log.warn("[IntentClassifier][llm error][query({}) error({})]", query, e.getMessage());
      return fallbackResult();
    }
  }

  /**
   * 多意图关键词匹配（缺陷修复：不再只取第一个命中）。
   *
   * <p>统计每个意图命中的关键词数量，只保留命中 > 0 的意图，按命中数降序、稳定保持规则顺序—— 命中词越多说明信号越强，避免"一句话同时包含多意图关键词"时漏判。
   */
  private List<IntentResult> matchByKeywords(String query) {
    Map<String, Integer> scores = new LinkedHashMap<>();
    Map<String, IntentRuleCacheService.IntentRule> ruleByName = new LinkedHashMap<>();
    for (IntentRuleCacheService.IntentRule rule : ruleCacheService.getAllEnabled()) {
      ruleByName.put(rule.name(), rule);
      int matched = 0;
      for (String kw : rule.keywords()) {
        if (query.contains(kw)) {
          matched++;
        }
      }
      if (matched > 0) {
        scores.put(rule.name(), matched);
      }
    }
    List<String> sorted = new ArrayList<>(scores.keySet());
    sorted.sort(Comparator.comparingInt(scores::get).reversed());
    List<IntentResult> hits = new ArrayList<>(sorted.size());
    for (String intent : sorted) {
      hits.add(
          new IntentResult(intent, 1.0, parseRequiredSlots(ruleByName.get(intent).slotsSchema())));
    }
    return hits;
  }

  /** 从注册表解析默认 ChatClient（优先 ollama，回退到第一个可用；注册表为空返回 null）。 */
  private ChatClient resolveChatClient() {
    if (chatClientRegistry == null || chatClientRegistry.isEmpty()) {
      return null;
    }
    ChatClient client = chatClientRegistry.get("ollamaChatClient");
    if (client != null) {
      return client;
    }
    return chatClientRegistry.values().stream().findFirst().orElse(null);
  }

  private String buildLlmPrompt(String query, List<Map<String, String>> history) {
    // 可选意图列表动态拼接：新增/停用意图通过 cs_intent 表生效，prompt 自动跟随
    StringBuilder intentList = new StringBuilder();
    List<IntentRuleCacheService.IntentRule> rules = ruleCacheService.getAllEnabled();
    for (int i = 0; i < rules.size(); i++) {
      IntentRuleCacheService.IntentRule rule = rules.get(i);
      intentList.append(rule.name()).append('（').append(rule.description()).append('）');
      if (i < rules.size() - 1) {
        intentList.append('、');
      }
    }
    StringBuilder hist = new StringBuilder();
    for (Map<String, String> m : history) {
      hist.append(m.getOrDefault("role", "user"))
          .append(": ")
          .append(m.getOrDefault("content", ""))
          .append("\n");
    }
    return String.format(
        "你是一个意图识别助手。请识别当前用户查询的意图，最多返回 3 个，按置信度降序，并按 JSON 格式输出。\n"
            + "可选意图：%s。\n\n"
            + "历史对话（仅用于理解上下文，不要识别历史本身的意图）：\n%s"
            + "当前用户查询：%s\n\n"
            + "输出格式（严格 JSON）：\n"
            + "{\"intents\":[{\"intent\":\"<意图名>\",\"confidence\":<0-1的浮点数>,"
            + "\"slots_needed\":[\"<槽位名>\"]}]}\n"
            + "示例：{\"intents\":[{\"intent\":\"order_query\",\"confidence\":0.95,"
            + "\"slots_needed\":[\"customer_name\"]}]}",
        intentList, hist, query);
  }

  /**
   * 从槽位 schema JSON 解析必填槽位列表（用于关键词命中场景）。
   * 示例：{"customer_name":{"required":true},"order_no":{"required":false}} → [customer_name]
   */
  private List<String> parseRequiredSlots(String slotsSchema) {
    if (slotsSchema == null || slotsSchema.isBlank()) {
      return List.of();
    }
    try {
      Map<String, Object> schema =
          objectMapper.readValue(slotsSchema, new TypeReference<Map<String, Object>>() {});
      List<String> slots = new ArrayList<>();
      schema.forEach(
          (slot, conf) -> {
            if (conf instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("required"))) {
              slots.add(slot);
            }
          });
      return slots;
    } catch (Exception e) {
      log.warn("[IntentClassifier][parseRequiredSlots failed][schema({})]", slotsSchema);
      return List.of();
    }
  }

  /** LLM 未给 slots_needed 时，按该意图在规则缓存中的槽位 schema 解析必填槽位。 */
  private List<String> resolveDefaultSlots(String intent) {
    return ruleCacheService.getAllEnabled().stream()
        .filter(rule -> rule.name().equals(intent))
        .findFirst()
        .map(rule -> parseRequiredSlots(rule.slotsSchema()))
        .orElse(List.of());
  }

  /** 解析 LLM 输出：优先 intents 数组（多意图），兼容旧单 intent 对象格式。 */
  private List<IntentResult> parseLlmOutput(String output) {
    if (output == null || output.isBlank()) {
      return null;
    }
    try {
      String json = extractJson(output);
      Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
      Object intentsObj = map.get("intents");
      if (intentsObj instanceof List<?> list) {
        List<IntentResult> results = new ArrayList<>();
        for (Object item : list) {
          if (!(item instanceof Map<?, ?> im)) {
            continue;
          }
          Map<String, Object> m = new HashMap<>();
          im.forEach((k, v) -> m.put(String.valueOf(k), v));
          String intent = (String) m.get("intent");
          if (intent == null || intent.isBlank()) {
            continue;
          }
          double confidence = m.get("confidence") instanceof Number n ? n.doubleValue() : 0.5;
          results.add(new IntentResult(intent, confidence, parseSlotsNeeded(m, intent)));
        }
        return results.isEmpty() ? null : results;
      }
      // 兼容旧格式：单 intent 对象
      String intent = (String) map.get("intent");
      if (intent == null || intent.isBlank()) {
        return null;
      }
      double confidence = map.get("confidence") instanceof Number n ? n.doubleValue() : 0.5;
      return List.of(new IntentResult(intent, confidence, parseSlotsNeeded(map, intent)));
    } catch (Exception e) {
      log.warn(
          "[IntentClassifier][parseLlmOutput][failed][output({}) error({})]",
          output,
          e.getMessage());
      return null;
    }
  }

  private List<String> parseSlotsNeeded(Map<String, Object> map, String intent) {
    if (map.get("slots_needed") instanceof List<?> list) {
      List<String> slots = new ArrayList<>(list.size());
      for (Object o : list) {
        slots.add(String.valueOf(o));
      }
      return slots;
    }
    return resolveDefaultSlots(intent);
  }

  private String extractJson(String s) {
    int start = s.indexOf('{');
    int end = s.lastIndexOf('}');
    if (start < 0 || end < 0 || end < start) {
      return s;
    }
    return s.substring(start, end + 1);
  }

  /** 组装返回：主意图字段 + intents 多意图列表。 */
  private Map<String, Object> toMap(List<IntentResult> results) {
    IntentResult primary = results.get(0);
    Map<String, Object> map = new HashMap<>();
    map.put("intent", primary.getIntent());
    map.put("confidence", primary.getConfidence());
    map.put("slots_needed", primary.getSlotsNeeded());
    List<Map<String, Object>> intents = new ArrayList<>(results.size());
    for (IntentResult r : results) {
      Map<String, Object> m = new HashMap<>();
      m.put("intent", r.getIntent());
      m.put("confidence", r.getConfidence());
      m.put("slots_needed", r.getSlotsNeeded());
      intents.add(m);
    }
    map.put("intents", intents);
    return map;
  }

  private Map<String, Object> fallbackResult() {
    return toMap(List.of(new IntentResult(properties.getFallbackIntent(), 0.0, List.of())));
  }

  private Map<String, Object> errorResult(String msg) {
    Map<String, Object> map = new HashMap<>();
    map.put("error", msg);
    return map;
  }
}
