package com.ai.api.agent.dto;

import com.ai.api.agent.entity.AgentTrace;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 用户任务中心 Controller 的响应模型。
 *
 * <p>按后端约定，每个接口方法在此文件内定义独立响应类，显式排除 contextSnapshot 大字段，避免移动端分页接口拉取执行快照。
 */
public final class AgentTaskControllerDTO {

  private AgentTaskControllerDTO() {}

  /** detail 接口响应：任务详情与执行日志。 */
  @Builder
  @Data
  public static class DetailResponse {

    private LocalDateTime createTime;
    private String errorMessage;
    private String finalAnswer;
    private String modelId;
    private Integer nextStepIndex;
    private Long runtimeId;
    private Long sessionId;
    private String status;
    private String strategy;
    private List<AgentTrace> traces;
    private LocalDateTime updateTime;
  }

  /** list 接口响应：任务分页中的单条记录。 */
  @Builder
  @Data
  public static class ListResponse {

    private Long agentConfigId;
    private LocalDateTime createTime;
    private String errorMessage;
    private String modelId;
    private Integer nextStepIndex;
    private Long runtimeId;
    private Long sessionId;
    private String status;
    private String strategy;
    private LocalDateTime updateTime;
  }
}
