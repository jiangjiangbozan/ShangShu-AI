package com.ai.api.workflow.controller.dto;

/** 创建工作流请求。 */
public record CreateWorkflowReq(String name, String description, String graph) {}
