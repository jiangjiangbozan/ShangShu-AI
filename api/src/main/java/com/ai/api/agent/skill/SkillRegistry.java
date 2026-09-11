package com.ai.api.agent.skill;

import com.ai.api.agent.entity.SkillDefinition;
import com.ai.api.agent.mapper.SkillDefinitionMapper;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Skill 注册中心（add-agent-runtime）。
 *
 * <p>启动时扫描所有 {@code @Skill} 注解方法，upsert 到 {@code skill_definition} 表。运行时按 name+version 路由到 {@link
 * SkillInvoker} 执行。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillRegistry {

  private final SkillDefinitionMapper skillDefinitionMapper;
  private final SkillInvoker skillInvoker;
  private final ApplicationContext applicationContext;
  private final ObjectMapper objectMapper;

  /** 启动时扫描所有 {@code @Skill} 注解方法并注册。 */
  public void register() {
    ConfigurableListableBeanFactory beanFactory =
        ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
    String[] beanNames = applicationContext.getBeanDefinitionNames();
    int count = 0;
    for (String beanName : beanNames) {
      Class<?> beanType;
      try {
        beanType = applicationContext.getType(beanName);
      } catch (Exception e) {
        continue;
      }
      if (beanType == null) continue;
      // 只扫描本项目 Bean，避免反射第三方框架 Bean 方法签名时触发无关类型加载失败
      if (!beanType.getName().startsWith("com.ai.api.")) continue;

      for (Method method : beanType.getDeclaredMethods()) {
        Skill skill = method.getAnnotation(Skill.class);
        if (skill == null) continue;

        upsertSkillDefinition(beanName, method, skill);
        count++;
      }
    }
    log.info("[SkillRegistry][register][扫描到 {} 个 Skill 并注册]", count);
  }

  /**
   * 调用 Skill。
   *
   * @param skillName Skill 名
   * @param version 版本（{@code "latest"} 解析最高版本）
   * @param input 输入参数（JSON Map）
   * @return Skill 输出（JSON Map）
   */
  public Map<String, Object> invoke(String skillName, String version, Map<String, Object> input) {
    return invoke(null, null, skillName, version, input);
  }

  /**
   * 调用 Skill（Agent 编排上下文版，回调自动落 trace）。
   *
   * @param runtimeId 关联 runtimeId（可空）
   * @param stepIndex 当前 step 序号（可空）
   * @param skillName Skill 名
   * @param version 版本（{@code "latest"} 解析最高版本）
   * @param input 输入参数（JSON Map）
   * @return Skill 输出（JSON Map）
   */
  public Map<String, Object> invoke(
      Long runtimeId,
      Integer stepIndex,
      String skillName,
      String version,
      Map<String, Object> input) {
    SkillDefinition def = resolve(skillName, version);
    log.info(
        "[SkillRegistry][invoke][skill({}#{}) type({}) runtimeId({})]",
        skillName,
        def.getVersion(),
        def.getType(),
        runtimeId);
    return skillInvoker.invoke(def, input, runtimeId, stepIndex);
  }

  /** 查询所有 enabled=true 的 Skill 元数据。 */
  public List<SkillDefinition> listEnabled() {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SkillDefinition> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.eq(SkillDefinition::getEnabled, 1).orderByAsc(SkillDefinition::getName);
    return skillDefinitionMapper.selectList(wrapper);
  }

  /** 查询所有 Skill（含已禁用的）。 */
  public List<SkillDefinition> listAll() {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SkillDefinition> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.orderByAsc(SkillDefinition::getName);
    return skillDefinitionMapper.selectList(wrapper);
  }

  /** 启用/禁用 Skill。 */
  public void setEnabled(String name, boolean enabled) {
    SkillDefinition def = skillDefinitionMapper.selectLatestByName(name);
    if (def == null) {
      throw new BusinessException(ErrorCode.SKILL_NOT_FOUND, "skill not found: " + name);
    }
    def.setEnabled(enabled ? 1 : 0);
    skillDefinitionMapper.updateById(def);
  }

  /** 按 name 查询最新版本 Skill 元数据。 */
  public SkillDefinition getLatest(String name) {
    return skillDefinitionMapper.selectLatestByName(name);
  }

  private SkillDefinition resolve(String name, String version) {
    SkillDefinition def;
    if ("latest".equalsIgnoreCase(version)) {
      def = skillDefinitionMapper.selectLatestByName(name);
    } else {
      def = skillDefinitionMapper.selectByNameAndVersion(name, version);
    }
    if (def == null) {
      throw new BusinessException(ErrorCode.SKILL_NOT_FOUND, "skill not registered: " + name);
    }
    return def;
  }

  /** upsert Skill 元数据到 {@code skill_definition} 表（INSERT ... ON DUPLICATE KEY UPDATE）。 */
  @SuppressWarnings("unchecked")
  private void upsertSkillDefinition(String beanName, Method method, Skill skill) {
    SkillDefinition existing =
        skillDefinitionMapper.selectByNameAndVersion(skill.name(), skill.version());
    SkillDefinition def = Optional.ofNullable(existing).orElseGet(SkillDefinition::new);

    def.setName(skill.name());
    def.setVersion(skill.version());
    def.setType(skill.type().name().toLowerCase());
    def.setDescription(skill.description());
    def.setBeanName(beanName);
    def.setMethodName(method.getName());
    def.setPermissionTag(skill.permission());
    def.setEnabled(1);

    // 默认 input/output schema（M6 完善：扫描 @RequestBody / @ResponseBody 类型）
    def.setInputSchema(toJson(Map.of("type", "object")));
    def.setOutputSchema(toJson(Map.of("type", "object")));

    if (existing == null) {
      skillDefinitionMapper.insert(def);
    } else {
      skillDefinitionMapper.updateById(def);
    }
    log.info(
        "[SkillRegistry][registered skill: {}#{} type={} bean={}.{}]",
        skill.name(),
        skill.version(),
        skill.type(),
        beanName,
        method.getName());
  }

  private String toJson(Map<String, Object> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JacksonException e) {
      return "{}";
    }
  }

  private Map<String, Object> parseJson(String json) {
    if (json == null || json.isBlank()) return new HashMap<>();
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (JacksonException e) {
      return new HashMap<>();
    }
  }
}
