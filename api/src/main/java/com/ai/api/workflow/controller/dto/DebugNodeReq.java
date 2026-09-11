package com.ai.api.workflow.controller.dto;

import java.util.Map;

/** 单节点调试请求。 */
public record DebugNodeReq(String nodeType, Map<String, Object> data, Map<String, Object> inputs) {
  public DebugNodeReq {
    if (data == null) data = Map.of();
    if (inputs == null) inputs = Map.of();
  }
}
