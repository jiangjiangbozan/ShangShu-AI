package com.ai.api.agent.pipeline;

import com.ai.api.agent.entity.SkillDefinition;
import com.ai.api.agent.pipeline.Layer.Phase;
import com.ai.api.agent.runner.AgentStrategy;
import com.ai.api.agent.runner.SkillToolCallback;
import com.ai.api.agent.skill.SkillRegistry;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 工具 Layer（add-agent-runner-pipeline）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>工具注册：FC 策略下基于 {@link SkillRegistry} 注册信息构建 {@link ToolCallback} 数组
 *   <li>工具描述生成：COT 策略下生成技能目录文本（name + description）供提示词使用
 *   <li>工具入参校验：{@link #validateSkillInput} 按 skill_definition.input_schema 校验必填字段
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolLayer implements Layer {

  private final SkillRegistry skillRegistry;
  private final ObjectMapper objectMapper;

  @Override
  public Phase phase() {
    return Phase.REQUEST;
  }

  @Override
  public int order() {
    return 20;
  }

  @Override
  public void process(PipelineContext ctx) {
    if (ctx.getStrategy() == AgentStrategy.FC) {
      ctx.setToolCallbacks(buildToolCallbacks(ctx.getRuntimeId()));
    } else {
      ctx.setSkillCatalog(buildSkillCatalog());
    }
  }

  /** FC 策略：为每个启用 Skill 构建一个 {@link SkillToolCallback}。 */
  private ToolCallback[] buildToolCallbacks(Long runtimeId) {
    List<SkillDefinition> skills = skillRegistry.listEnabled();
    List<ToolCallback> callbacks = new ArrayList<>();
    for (SkillDefinition def : skills) {
      callbacks.add(new SkillToolCallback(def, skillRegistry, objectMapper, runtimeId));
    }
    return callbacks.toArray(new ToolCallback[0]);
  }

  /** COT 策略：生成技能目录文本（name + description）。 */
  public String buildSkillCatalog() {
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

  /**
   * 工具入参校验：按 skill_definition.input_schema 的 required 字段校验。
   *
   * @param skillName Skill 名
   * @param version Skill 版本
   * @param input 待校验入参
   */
  public void validateSkillInput(String skillName, String version, Map<String, Object> input) {
    SkillDefinition def = skillRegistry.getLatest(skillName);
    if (def == null) {
      throw new BusinessException(ErrorCode.SKILL_NOT_FOUND, "skill not registered: " + skillName);
    }
    List<String> required = parseRequiredFields(def.getInputSchema());
    if (required.isEmpty()) {
      return;
    }
    Map<String, Object> safeInput = input == null ? new HashMap<>() : input;
    List<String> missing = required.stream().filter(f -> safeInput.get(f) == null).toList();
    if (!missing.isEmpty()) {
      throw new BusinessException(
          ErrorCode.SKILL_INVOKE_FAILED,
          "skill(" + skillName + ") missing required params: " + missing);
    }
  }

  /** 解析 input_schema 的 required 字段数组（schema 为占位时返回空）。 */
  @SuppressWarnings("unchecked")
  private List<String> parseRequiredFields(String inputSchema) {
    if (inputSchema == null || inputSchema.isBlank()) {
      return List.of();
    }
    try {
      Map<String, Object> schema = objectMapper.readValue(inputSchema, Map.class);
      Object required = schema.get("required");
      if (required instanceof List<?> list) {
        return list.stream().map(String::valueOf).toList();
      }
    } catch (Exception e) {
      log.warn("[ToolLayer][parseRequiredFields][schema 解析失败: {}]", e.getMessage());
    }
    return List.of();
  }
}
