package com.ai.api.agent.runner;

import com.ai.api.agent.entity.SkillDefinition;
import com.ai.api.agent.skill.SkillRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Skill 原生 Function Calling 工具回调（add-agent-runner-pipeline）。
 *
 * <p>将 {@link SkillRegistry} 中注册的 Skill 包装为 Spring AI {@link ToolCallback}， 供 FC 策略执行器（{@link
 * FcAgentRunner}）通过模型原生 Function Calling 调用：
 *
 * <ul>
 *   <li>getToolDefinition：名称 / 描述 / 入参 Schema（取自 skill_definition）
 *   <li>call：解析模型下发的 JSON 入参 → 经 SkillRegistry 执行（触发统一回调管线自动落 trace）→ 返回 JSON 出参
 * </ul>
 *
 * <p>step 序号由内部计数器递增（每次工具调用分配独立 stepIndex，满足 agent_trace 唯一约束）。
 */
public class SkillToolCallback implements ToolCallback {

  private final ToolDefinition definition;
  private final SkillRegistry skillRegistry;
  private final ObjectMapper objectMapper;
  private final Long runtimeId;
  private final AtomicInteger stepCounter = new AtomicInteger(1);

  public SkillToolCallback(
      SkillDefinition def, SkillRegistry skillRegistry, ObjectMapper objectMapper, Long runtimeId) {
    this.definition =
        ToolDefinition.builder()
            .name(def.getName())
            .description(def.getDescription() == null ? "" : def.getDescription())
            .inputSchema(
                def.getInputSchema() == null
                    ? "{\"type\":\"object\",\"properties\":{}}"
                    : def.getInputSchema())
            .build();
    this.skillRegistry = skillRegistry;
    this.objectMapper = objectMapper;
    this.runtimeId = runtimeId;
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return definition;
  }

  @Override
  public String call(String toolInput) {
    Map<String, Object> input = parseInput(toolInput);
    int step = stepCounter.getAndIncrement();
    try {
      Map<String, Object> output =
          skillRegistry.invoke(runtimeId, step, definition.name(), "latest", input);
      return toJson(output == null ? new HashMap<>() : output);
    } catch (Exception e) {
      // 工具执行失败不向模型传播异常：返回结构化 error，让模型基于错误调整或如实说明
      Map<String, Object> err = new HashMap<>();
      err.put("error", definition.name() + " invoke failed: " + e.getMessage());
      return toJson(err);
    }
  }

  private Map<String, Object> parseInput(String toolInput) {
    if (toolInput == null || toolInput.isBlank()) {
      return new HashMap<>();
    }
    try {
      return objectMapper.readValue(toolInput, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return new HashMap<>();
    }
  }

  private String toJson(Map<String, Object> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (Exception e) {
      return "{}";
    }
  }
}
