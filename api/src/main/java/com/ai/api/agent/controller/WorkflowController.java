package com.ai.api.agent.controller;

import com.ai.api.agent.entity.WorkflowDefinition;
import com.ai.api.agent.mapper.WorkflowDefinitionMapper;
import com.ai.api.agent.workflow.DagEngine;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.Result;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workflow 管理 REST API（add-agent-runtime）。
 *
 * <p>暴露工作流查询与执行接口：
 *
 * <ul>
 *   <li>GET /api/v1/agent/workflows/{workflowId}：查询最新版本工作流
 *   <li>POST /api/v1/agent/workflows/{workflowId}/execute：执行工作流
 * </ul>
 *
 * @author add-agent-runtime
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/workflows")
@RequiredArgsConstructor
public class WorkflowController {

  private final WorkflowDefinitionMapper workflowDefinitionMapper;
  private final DagEngine dagEngine;

  @GetMapping("/{workflowId}")
  public Result<WorkflowDefinition> get(
      @PathVariable String workflowId,
      @RequestParam(required = false, defaultValue = "latest") String version) {
    WorkflowDefinition wf = resolve(workflowId, version);
    return Result.success(wf);
  }

  @PostMapping("/{workflowId}/execute")
  public Result<Map<String, Object>> execute(
      @PathVariable String workflowId,
      @RequestParam(required = false, defaultValue = "latest") String version,
      @RequestBody Map<String, Object> input) {
    log.info(
        "[WorkflowController][execute][workflow({}#{}) input keys={}]",
        workflowId,
        version,
        input.keySet());
    Map<String, Object> result = dagEngine.execute(workflowId, version, input);
    return Result.success(result);
  }

  private WorkflowDefinition resolve(String workflowId, String version) {
    WorkflowDefinition wf;
    if ("latest".equalsIgnoreCase(version)) {
      wf = workflowDefinitionMapper.selectLatestByWorkflowId(workflowId);
    } else {
      wf = workflowDefinitionMapper.selectByWorkflowIdAndVersion(workflowId, version);
    }
    if (wf == null) {
      throw new BusinessException(
          ErrorCode.WORKFLOW_NOT_FOUND, "workflow not found: " + workflowId);
    }
    return wf;
  }
}
