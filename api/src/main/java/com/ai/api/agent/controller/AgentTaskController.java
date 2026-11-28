package com.ai.api.agent.controller;

import com.ai.api.agent.dto.AgentTaskControllerDTO;
import com.ai.api.agent.service.AgentTaskService;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.ai.api.identity.security.AuthUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 面向移动端用户的任务中心接口，不暴露 Runtime 调试概念。 */
@RestController
@RequestMapping("/api/v1/agent/tasks")
public class AgentTaskController {

  private static final String DEFAULT_PAGE = "1";
  private static final String DEFAULT_PAGE_SIZE = "20";

  private final AgentTaskService agentTaskService; // 任务查询服务

  public AgentTaskController(AgentTaskService agentTaskService) {
    this.agentTaskService = agentTaskService;
  }

  private Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthUserDetails details)) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
    }
    return details.userId();
  }

  /** 查询当前用户可见的任务详情。GET /api/v1/agent/tasks/{runtimeId} */
  @GetMapping("/{runtimeId}")
  public Result<AgentTaskControllerDTO.DetailResponse> detail(@PathVariable Long runtimeId) {
    return Result.success(agentTaskService.detail(runtimeId, currentUserId()));
  }

  /** 分页查询当前用户的任务列表。GET /api/v1/agent/tasks */
  @GetMapping
  public Result<PageResult<AgentTaskControllerDTO.ListResponse>> list(
      @RequestParam(defaultValue = DEFAULT_PAGE) int page,
      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size,
      @RequestParam(required = false) String status) {
    return Result.success(agentTaskService.listByUser(currentUserId(), status, page, size));
  }
}
