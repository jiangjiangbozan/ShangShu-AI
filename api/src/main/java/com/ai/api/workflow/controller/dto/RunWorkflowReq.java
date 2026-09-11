package com.ai.api.workflow.controller.dto;

import java.util.Map;

/** 运行工作流请求。 */
public record RunWorkflowReq(Map<String, Object> inputs) {
  public RunWorkflowReq {
    if (inputs == null) inputs = Map.of();
  }
}
