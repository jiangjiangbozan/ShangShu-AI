package com.ai.api.agent.controller;

import com.ai.api.agent.entity.AgentRuntime;
import com.ai.api.agent.runtime.AgentRuntimeService;
import com.ai.api.common.result.Result;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Runtime REST API（add-agent-runtime）。
 *
 * <p>暴露 runtime 生命周期管理接口：
 *
 * <ul>
 *   <li>POST /api/v1/agent/runtime：创建 runtime（可指定 strategy：COT / FC）
 *   <li>POST /api/v1/agent/runtime/{id}/start：启动
 *   <li>POST /api/v1/agent/runtime/{id}/pause：暂停
 *   <li>POST /api/v1/agent/runtime/{id}/resume：恢复
 *   <li>POST /api/v1/agent/runtime/{id}/cancel：取消
 *   <li>POST /api/v1/agent/runtime/{id}/ask-human：人工介入（生成 ASK_HUMAN 暂停点）
 *   <li>POST /api/v1/agent/runtime/{id}/respond：人工回复（回填上下文并从 nextStepIndex 继续）
 *   <li>GET /api/v1/agent/runtime/{id}：查询状态与最终答案
 * </ul>
 *
 * @author add-agent-runtime
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/runtime")
@RequiredArgsConstructor
public class AgentRuntimeController {

  private final AgentRuntimeService agentRuntimeService;

  @PostMapping
  public Result<Map<String, Object>> createRuntime(
      @RequestParam(required = false, defaultValue = "0") Long sessionId,
      @RequestParam(required = false) Long agentConfigId,
      @RequestParam(required = false) String modelId,
      @RequestParam(required = false, defaultValue = "COT") String strategy) {
    Long userId = getCurrentUserId();
    Long runtimeId =
        agentRuntimeService.createRuntime(sessionId, agentConfigId, modelId, userId, strategy);
    Map<String, Object> data = new HashMap<>();
    data.put("runtimeId", runtimeId);
    return Result.success(data);
  }

  @PostMapping("/{id}/start")
  public Result<Void> start(@PathVariable Long id) {
    agentRuntimeService.start(id);
    return Result.success(null);
  }

  @PostMapping("/{id}/pause")
  public Result<Void> pause(@PathVariable Long id) {
    agentRuntimeService.pause(id);
    return Result.success(null);
  }

  @PostMapping("/{id}/resume")
  public Result<Void> resume(@PathVariable Long id) {
    agentRuntimeService.resume(id);
    return Result.success(null);
  }

  @PostMapping("/{id}/cancel")
  public Result<Void> cancel(@PathVariable Long id) {
    agentRuntimeService.cancel(id);
    return Result.success(null);
  }

  /** 人工介入（ask-human）：手动创建 ASK_HUMAN 暂停点。 */
  @PostMapping("/{id}/ask-human")
  public Result<Map<String, Object>> askHuman(
      @PathVariable Long id,
      @RequestParam String question,
      @RequestParam(required = false) Map<String, Object> variables,
      @RequestParam(required = false, defaultValue = "0") Integer nextStepIndex) {
    Map<String, Object> data = new HashMap<>();
    data.put(
        "pauseId", agentRuntimeService.askHuman(id, question, variables, nextStepIndex).getId());
    return Result.success(data);
  }

  /** 人工回复（respond）：解析 PauseState，回填上下文并恢复执行。 */
  @PostMapping("/{id}/respond")
  public Result<Void> respond(@PathVariable Long id, @RequestParam String input) {
    agentRuntimeService.respond(id, input);
    return Result.success(null);
  }

  @GetMapping("/{id}")
  public Result<AgentRuntime> get(@PathVariable Long id) {
    return Result.success(agentRuntimeService.loadRuntime(id));
  }

  private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.getPrincipal()
            instanceof org.springframework.security.core.userdetails.UserDetails details) {
      // 兼容 AuthUserDetails（用户 ID 在 username 字段）
      try {
        return Long.parseLong(details.getUsername());
      } catch (NumberFormatException e) {
        return 0L;
      }
    }
    return 0L;
  }
}
