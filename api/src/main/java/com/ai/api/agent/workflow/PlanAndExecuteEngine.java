package com.ai.api.agent.workflow;

import com.ai.api.agent.config.AgentProperties;
import com.ai.api.agent.skill.SkillRegistry;
import com.ai.api.agent.trace.TraceRecorder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Plan-and-Execute 工作流编排器（add-agent-runtime）。
 *
 * <p>动态规划型工作流，与静态 {@link DagEngine} 互补：
 *
 * <ol>
 *   <li>Plan：LLM 根据用户 query + 可用 Skill 列表生成步骤列表（JSON）
 *   <li>Execute：顺序执行每个 step，下游可引用上游输出
 *   <li>Replan（可选）：每步执行后由 LLM 重新评估是否需要修正计划
 * </ol>
 *
 * <p>Replan 在本 change 阶段不实现，仅支持单次 Plan。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanAndExecuteEngine {

  private static final String PLAN_PROMPT_TEMPLATE =
      """
      你是任务规划器。请根据用户输入选择并编排可用 Skill，输出 JSON 步骤列表。

      可用 Skills:
      %s

      用户输入: %s

      要求:
      1. 只输出 JSON 数组，无任何额外文字
      2. 每个 step 形如: {"skill":"<skill_name>","input":{"key":"value"}}
      3. 引用上游 step 输出使用 ${step_<index>.field}
      4. step 从 0 开始编号

      示例:
      [
        {"skill":"kb_retrieval","input":{"query":"${user_query}","kbId":2,"topK":5}},
        {"skill":"calculator","input":{"expr":"${step_0.chunks_count} * 0.1"}}
      ]
      """;

  private final SkillRegistry skillRegistry;
  private final TraceRecorder traceRecorder;
  private final AgentProperties agentProperties;
  private final ObjectMapper objectMapper;

  /**
   * 执行 Plan-and-Execute 工作流。
   *
   * @param runtimeId runtime ID（用于 Trace 写入）
   * @param userQuery 用户输入
   * @param chatClient 用于规划的 ChatClient
   * @return 最终输出
   */
  public Map<String, Object> execute(Long runtimeId, String userQuery, ChatClient chatClient) {
    // 1. Plan
    String skillsCatalog = buildSkillsCatalog();
    String prompt = String.format(PLAN_PROMPT_TEMPLATE, skillsCatalog, userQuery);
    log.info("[PlanAndExecute][execute][runtimeId({}) 生成计划 prompt]", runtimeId);

    String planJson;
    try {
      planJson = chatClient.prompt().user(prompt).call().content();
    } catch (Exception e) {
      log.error("[PlanAndExecute][execute][runtimeId({}) LLM 规划失败]", runtimeId, e);
      Map<String, Object> err = new HashMap<>();
      err.put("error", "plan generation failed: " + e.getMessage());
      return err;
    }

    List<Map<String, Object>> steps = parsePlan(planJson);
    if (steps == null || steps.isEmpty()) {
      log.warn("[PlanAndExecute][execute][runtimeId({}) 计划为空，原始输出: {}]", runtimeId, planJson);
      Map<String, Object> err = new HashMap<>();
      err.put("error", "empty plan");
      err.put("raw", planJson);
      return err;
    }

    traceRecorder.recordPlan(runtimeId, steps, planJson);
    log.info("[PlanAndExecute][execute][runtimeId({}) 生成 {} 步计划]", runtimeId, steps.size());

    // 2. Execute
    Map<String, Map<String, Object>> outputs = new HashMap<>();
    for (int i = 0; i < steps.size(); i++) {
      Map<String, Object> step = steps.get(i);
      String skillName = (String) step.get("skill");
      @SuppressWarnings("unchecked")
      Map<String, Object> input = (Map<String, Object>) step.getOrDefault("input", new HashMap<>());

      // 注入 user_query 与 step_<i>.* 变量
      Map<String, Object> interpolated = new HashMap<>();
      for (Map.Entry<String, Object> e : input.entrySet()) {
        if (e.getValue() instanceof String s && s.contains("${user_query}")) {
          interpolated.put(e.getKey(), s.replace("${user_query}", userQuery));
        } else {
          interpolated.put(e.getKey(), e.getValue());
        }
      }
      // 简化的变量引用（step_<i>.field）
      interpolated = interpolateStepVars(interpolated, outputs);

      Map<String, Object> result;
      try {
        // 统一回调管线自动落 trace（skill_before/after/error → agent_trace）
        result = skillRegistry.invoke(runtimeId, i, skillName, "latest", interpolated);
      } catch (Exception e) {
        log.error(
            "[PlanAndExecute][execute][runtimeId({}) step[{}] skill({}) 失败]",
            runtimeId,
            i,
            skillName,
            e);
        Map<String, Object> err = new HashMap<>();
        err.put("error", "step " + i + " failed: " + e.getMessage());
        return err;
      }
      outputs.put("step_" + i, result);
    }

    // 返回最后一步输出
    String lastKey = "step_" + (steps.size() - 1);
    return outputs.getOrDefault(lastKey, new HashMap<>());
  }

  private String buildSkillsCatalog() {
    StringBuilder sb = new StringBuilder();
    skillRegistry
        .listEnabled()
        .forEach(
            d -> {
              sb.append("- ").append(d.getName());
              sb.append(" (").append(d.getDescription()).append(")\n");
            });
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> parsePlan(String planJson) {
    if (planJson == null) return List.of();
    // 提取第一个 [ 到最后一个 ]
    int start = planJson.indexOf('[');
    int end = planJson.lastIndexOf(']');
    if (start < 0 || end < 0 || end <= start) return List.of();
    String json = planJson.substring(start, end + 1);
    try {
      return objectMapper.readValue(json, List.class);
    } catch (Exception e) {
      log.warn("[PlanAndExecute][parsePlan][解析失败: {}]", e.getMessage());
      return List.of();
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> interpolateStepVars(
      Map<String, Object> input, Map<String, Map<String, Object>> outputs) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Object> e : input.entrySet()) {
      Object v = e.getValue();
      if (v instanceof String s) {
        // 简化：支持 ${step_<i>.field} 形式
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("\\$\\{(step_\\d+)\\.(\\w+)\\}").matcher(s);
        if (m.find()) {
          String stepKey = m.group(1);
          String field = m.group(2);
          Object resolved = outputs.getOrDefault(stepKey, new HashMap<>()).get(field);
          if (resolved != null) result.put(e.getKey(), resolved);
          else result.put(e.getKey(), s);
        } else {
          result.put(e.getKey(), s);
        }
      } else if (v instanceof Map) {
        result.put(e.getKey(), interpolateStepVars((Map<String, Object>) v, outputs));
      } else {
        result.put(e.getKey(), v);
      }
    }
    return result;
  }
}
