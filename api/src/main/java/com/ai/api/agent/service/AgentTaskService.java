package com.ai.api.agent.service;

import com.ai.api.agent.dto.AgentTaskControllerDTO;
import com.ai.api.agent.entity.AgentRuntime;
import com.ai.api.agent.mapper.AgentRuntimeMapper;
import com.ai.api.agent.runtime.AgentStatus;
import com.ai.api.agent.trace.TraceService;
import com.ai.api.common.exception.BadRequestException;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 面向普通用户的 Agent 任务查询服务：所有查询都在 SQL 层按当前用户过滤。 */
@Service
@RequiredArgsConstructor
public class AgentTaskService {

  private static final int MAX_PAGE_SIZE = 500;
  private static final int MIN_PAGE_NUMBER = 1;
  private static final int MIN_PAGE_SIZE = 1;

  private final AgentRuntimeMapper agentRuntimeMapper; // Runtime 查询 Mapper
  private final TraceService traceService; // 执行日志回放服务

  /** 查询当前用户可见的任务详情，并校验 Runtime 归属。 */
  public AgentTaskControllerDTO.DetailResponse detail(Long runtimeId, Long userId) {
    AgentRuntime runtime = agentRuntimeMapper.selectById(runtimeId);
    if (runtime == null || !userId.equals(runtime.getUserId())) {
      throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
    }
    return AgentTaskControllerDTO.DetailResponse.builder()
        .createTime(runtime.getCreateTime())
        .errorMessage(runtime.getErrorMessage())
        .finalAnswer(runtime.getFinalAnswer())
        .modelId(runtime.getModelId())
        .nextStepIndex(runtime.getNextStepIndex())
        .runtimeId(runtime.getId())
        .sessionId(runtime.getSessionId())
        .status(runtime.getStatus())
        .strategy(runtime.getStrategy())
        .traces(traceService.replay(runtimeId))
        .updateTime(runtime.getUpdateTime())
        .build();
  }

  /** 分页查询当前用户的任务列表，状态过滤下推到 SQL。 */
  public PageResult<AgentTaskControllerDTO.ListResponse> listByUser(
      Long userId, String status, int page, int size) {
    int safePage = validatePage(page);
    int safeSize = validatePageSize(size);
    String safeStatus = validateStatus(status);
    LambdaQueryWrapper<AgentRuntime> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .eq(AgentRuntime::getUserId, userId)
        .eq(safeStatus != null, AgentRuntime::getStatus, safeStatus)
        .select(
            AgentRuntime::getId,
            AgentRuntime::getSessionId,
            AgentRuntime::getAgentConfigId,
            AgentRuntime::getStatus,
            AgentRuntime::getNextStepIndex,
            AgentRuntime::getErrorMessage,
            AgentRuntime::getModelId,
            AgentRuntime::getStrategy,
            AgentRuntime::getCreateTime,
            AgentRuntime::getUpdateTime)
        .orderByDesc(AgentRuntime::getCreateTime)
        .orderByDesc(AgentRuntime::getId);
    IPage<AgentRuntime> result =
        agentRuntimeMapper.selectPage(Page.of(safePage, safeSize), wrapper);
    return PageResult.of(
        result.getRecords().stream().map(this::toListItem).toList(), result.getTotal());
  }

  private String normalizeStatus(String status) {
    return AgentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT))
        .name()
        .toLowerCase(Locale.ROOT);
  }

  private AgentTaskControllerDTO.ListResponse toListItem(AgentRuntime runtime) {
    return AgentTaskControllerDTO.ListResponse.builder()
        .agentConfigId(runtime.getAgentConfigId())
        .createTime(runtime.getCreateTime())
        .errorMessage(runtime.getErrorMessage())
        .modelId(runtime.getModelId())
        .nextStepIndex(runtime.getNextStepIndex())
        .runtimeId(runtime.getId())
        .sessionId(runtime.getSessionId())
        .status(runtime.getStatus())
        .strategy(runtime.getStrategy())
        .updateTime(runtime.getUpdateTime())
        .build();
  }

  private int validatePage(int page) {
    return Math.max(MIN_PAGE_NUMBER, page);
  }

  private int validatePageSize(int size) {
    return Math.max(MIN_PAGE_SIZE, Math.min(size, MAX_PAGE_SIZE));
  }

  private String validateStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return normalizeStatus(status);
    } catch (IllegalArgumentException ex) {
      throw new BadRequestException("状态参数不合法");
    }
  }
}
