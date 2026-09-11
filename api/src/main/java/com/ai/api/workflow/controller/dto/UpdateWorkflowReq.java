package com.ai.api.workflow.controller.dto;

/** 更新工作流请求。 */
public record UpdateWorkflowReq(String name, String description, String graph) {}
