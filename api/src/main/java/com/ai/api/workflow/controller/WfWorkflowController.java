package com.ai.api.workflow.controller;

import com.ai.api.common.result.Result;
import com.ai.api.workflow.controller.dto.CreateWorkflowReq;
import com.ai.api.workflow.controller.dto.DebugNodeReq;
import com.ai.api.workflow.controller.dto.RunWorkflowReq;
import com.ai.api.workflow.controller.dto.UpdateWorkflowReq;
import com.ai.api.workflow.entity.WfWorkflow;
import com.ai.api.workflow.entity.WfWorkflowRun;
import com.ai.api.workflow.service.WfWorkflowService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * 工作流编辑器 REST API。
 *
 * <p>提供工作流 CRUD、发布、运行（SSE）、单节点调试等接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WfWorkflowController {

  private final WfWorkflowService workflowService;
  private final ObjectMapper objectMapper;

  /** 创建工作流。 */
  @PostMapping
  public Result<WfWorkflow> create(
      @RequestBody CreateWorkflowReq req, @AuthenticationPrincipal Long userId) {
    WfWorkflow wf = workflowService.create(req.name(), req.description(), req.graph(), userId);
    return Result.success(wf);
  }

  /** 获取工作流详情。 */
  @GetMapping("/{workflowId}")
  public Result<WfWorkflow> get(@PathVariable String workflowId) {
    return Result.success(workflowService.getByWorkflowId(workflowId));
  }

  /** 工作流列表。 */
  @GetMapping
  public Result<List<WfWorkflow>> list(@AuthenticationPrincipal Long userId) {
    return Result.success(workflowService.list(userId));
  }

  /** 更新工作流草稿。 */
  @PutMapping("/{workflowId}")
  public Result<WfWorkflow> update(
      @PathVariable String workflowId, @RequestBody UpdateWorkflowReq req) {
    return Result.success(
        workflowService.updateDraft(workflowId, req.name(), req.description(), req.graph()));
  }

  /** 删除工作流。 */
  @DeleteMapping("/{workflowId}")
  public Result<Void> delete(@PathVariable String workflowId) {
    workflowService.delete(workflowId);
    return Result.success();
  }

  /** 复制工作流。 */
  @PostMapping("/{workflowId}/duplicate")
  public Result<WfWorkflow> duplicate(@PathVariable String workflowId) {
    return Result.success(workflowService.duplicate(workflowId));
  }

  /** 发布工作流。 */
  @PostMapping("/{workflowId}/publish")
  public Result<WfWorkflow> publish(@PathVariable String workflowId) {
    return Result.success(workflowService.publish(workflowId));
  }

  /** 运行工作流（SSE 流式返回执行进度）。 */
  @PostMapping(value = "/{workflowId}/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter run(@PathVariable String workflowId, @RequestBody RunWorkflowReq req) {
    SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
    // 异步执行工作流
    Thread.ofVirtual()
        .name("wf-run-" + workflowId)
        .start(
            () -> {
              try {
                workflowService.run(workflowId, req.inputs(), emitter);
              } catch (Exception e) {
                log.error("[WfWorkflowController][run][workflow({}) 执行失败]", workflowId, e);
                try {
                  emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                  emitter.complete();
                } catch (Exception ignored) {
                }
              }
            });
    return emitter;
  }

  /** 单节点调试。 */
  @PostMapping("/debug/node")
  public Result<Map<String, Object>> debugNode(@RequestBody DebugNodeReq req) {
    return Result.success(workflowService.debugNode(req.nodeType(), req.data(), req.inputs()));
  }

  /** 运行历史。 */
  @GetMapping("/{workflowId}/runs")
  public Result<List<WfWorkflowRun>> listRuns(@PathVariable String workflowId) {
    return Result.success(workflowService.listRuns(workflowId));
  }
}
