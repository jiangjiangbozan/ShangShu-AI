package com.ai.api.agent.controller;

import com.ai.api.agent.entity.SkillDefinition;
import com.ai.api.agent.skill.SkillRegistry;
import com.ai.api.common.result.Result;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill 管理 REST API（add-agent-runtime）。
 *
 * <p>暴露 Skill 元数据查询与调用接口：
 *
 * <ul>
 *   <li>GET /api/v1/agent/skills：列出所有 enabled=true 的 Skill
 *   <li>GET /api/v1/agent/skills/{name}：查询最新版本 Skill 元数据
 *   <li>POST /api/v1/agent/skills/{name}/invoke：直接调用 Skill（调试用）
 * </ul>
 *
 * @author add-agent-runtime
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/skills")
@RequiredArgsConstructor
public class SkillController {

  private final SkillRegistry skillRegistry;

  @GetMapping
  public Result<List<SkillDefinition>> list() {
    return Result.success(skillRegistry.listEnabled());
  }

  @GetMapping("/{name}")
  public Result<SkillDefinition> get(@PathVariable String name) {
    return Result.success(skillRegistry.getLatest(name));
  }

  @PostMapping("/{name}/invoke")
  public Result<Map<String, Object>> invoke(
      @PathVariable String name,
      @RequestParam(required = false, defaultValue = "latest") String version,
      @RequestBody Map<String, Object> input) {
    log.info(
        "[SkillController][invoke][skill({}#{}) input keys={}]", name, version, input.keySet());
    Map<String, Object> result = skillRegistry.invoke(name, version, input);
    return Result.success(result);
  }

  /** 调用 Skill 但不强制 body 为 JSON Map（兼容 form 参数）。 */
  @PostMapping("/{name}/invoke-form")
  public Result<Map<String, Object>> invokeForm(
      @PathVariable String name,
      @RequestParam(required = false, defaultValue = "latest") String version,
      @RequestParam Map<String, Object> form) {
    Map<String, Object> result = skillRegistry.invoke(name, version, form);
    return Result.success(result);
  }

  /** 列出所有 Skill（含已禁用的）。 */
  @GetMapping("/all")
  public Result<List<SkillDefinition>> listAll() {
    return Result.success(skillRegistry.listAll());
  }

  /** 启用/禁用 Skill。 */
  @PutMapping("/{name}/enabled")
  public Result<Void> setEnabled(@PathVariable String name, @RequestParam boolean enabled) {
    skillRegistry.setEnabled(name, enabled);
    return Result.success(null);
  }

  /** 健康检查：返回各 Skill 状态。 */
  @GetMapping("/health")
  public Result<Map<String, Object>> health() {
    Map<String, Object> status = new HashMap<>();
    status.put("registered", skillRegistry.listEnabled().size());
    return Result.success(status);
  }
}
