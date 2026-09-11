package com.ai.api.cs.slot;

import com.ai.api.agent.skill.Skill;
import com.ai.api.agent.skill.SkillType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 槽位填充 Skill（add-customer-service）。
 *
 * <p>输入 query + intent + slots_schema + history，输出已填充槽位、缺失槽位、是否完整。
 *
 * <p>调 LLM 输出 JSON {@code {"filled_slots":{...},"missing_slots":[...],"is_complete":bool}}， 解析失败重试
 * 1 次。
 *
 * @author add-customer-service
 */
@Slf4j
@Component("slotFillerSkill")
@RequiredArgsConstructor
public class SlotFillerSkill {

  private final Map<String, ChatClient> chatClientRegistry;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Skill(
      name = "slot_filler",
      type = SkillType.LOCAL,
      description = "槽位填充：根据意图 slots_schema + 历史对话，输出已填充与缺失槽位")
  public Map<String, Object> fill(Map<String, Object> input) {
    Object queryObj = input.get("query");
    Object intentObj = input.get("intent");
    Object schemaObj = input.get("slots_schema");
    if (queryObj == null || intentObj == null) {
      return errorResult("query and intent are required");
    }
    String query = queryObj.toString();
    String intent = intentObj.toString();
    String schema = schemaObj == null ? "{}" : schemaObj.toString();

    @SuppressWarnings("unchecked")
    List<Map<String, String>> history =
        input.get("history") instanceof List
            ? (List<Map<String, String>>) input.get("history")
            : List.of();

    String prompt = buildLlmPrompt(query, intent, schema, history);
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        String llmOutput = resolveChatClient().prompt().user(prompt).call().content();
        Map<String, Object> parsed = parseLlmOutput(llmOutput);
        if (parsed != null) return parsed;
        log.warn("[SlotFiller][attempt({}) parse failed][output({})]", attempt, llmOutput);
      } catch (Exception e) {
        log.warn("[SlotFiller][attempt({}) llm error][{}]", attempt, e.getMessage());
      }
    }
    // 兜底：返回空填充
    return emptyFill();
  }

  /** 从注册表解析默认 ChatClient（优先 ollama，回退到第一个可用）。 */
  private ChatClient resolveChatClient() {
    ChatClient client = chatClientRegistry.get("ollamaChatClient");
    if (client != null) {
      return client;
    }
    return chatClientRegistry.values().stream().findFirst().orElse(null);
  }

  private String buildLlmPrompt(
      String query, String intent, String schema, List<Map<String, String>> history) {
    StringBuilder hist = new StringBuilder();
    for (Map<String, String> m : history) {
      hist.append(m.getOrDefault("role", "user"))
          .append(": ")
          .append(m.getOrDefault("content", ""))
          .append("\n");
    }
    return String.format(
        "你是一个槽位填充助手。根据用户查询 + 意图槽位 schema + 历史对话，识别用户已提供的槽位值。\n\n"
            + "意图：%s\n"
            + "槽位 schema：%s\n"
            + "历史对话：\n%s\n"
            + "当前用户查询：%s\n\n"
            + "输出格式（严格 JSON）：\n"
            + "{\"filled_slots\":{\"<槽位名>\":\"<值>\"},\"missing_slots\":[\"<缺失槽位名>\"],"
            + "\"is_complete\":<true|false>}\n"
            + "示例：{\"filled_slots\":{\"region\":\"北京\",\"product\":\"iPhone\"},"
            + "\"missing_slots\":[],\"is_complete\":true}",
        intent, schema, hist.toString(), query);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseLlmOutput(String output) {
    if (output == null || output.isBlank()) return null;
    try {
      int start = output.indexOf('{');
      int end = output.lastIndexOf('}');
      if (start < 0 || end < 0) return null;
      String json = output.substring(start, end + 1);
      Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
      Object filled = map.get("filled_slots");
      if (!(filled instanceof Map)) map.put("filled_slots", new HashMap<>());
      Object missing = map.get("missing_slots");
      if (!(missing instanceof List)) map.put("missing_slots", new ArrayList<>());
      Object complete = map.get("is_complete");
      if (!(complete instanceof Boolean)) map.put("is_complete", false);
      return map;
    } catch (Exception e) {
      log.warn("[SlotFiller][parseLlmOutput][failed][{}]", e.getMessage());
      return null;
    }
  }

  private Map<String, Object> emptyFill() {
    Map<String, Object> map = new HashMap<>();
    map.put("filled_slots", new HashMap<>());
    map.put("missing_slots", new ArrayList<>());
    map.put("is_complete", false);
    return map;
  }

  private Map<String, Object> errorResult(String msg) {
    Map<String, Object> map = new HashMap<>();
    map.put("error", msg);
    return map;
  }
}
