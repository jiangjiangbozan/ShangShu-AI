package com.ai.api.workflow.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.workflow.engine.WorkflowEngine;
import com.ai.api.workflow.engine.WorkflowEvent;
import com.ai.api.workflow.entity.WfNodeRun;
import com.ai.api.workflow.entity.WfWorkflow;
import com.ai.api.workflow.entity.WfWorkflowRun;
import com.ai.api.workflow.mapper.WfNodeRunMapper;
import com.ai.api.workflow.mapper.WfWorkflowMapper;
import com.ai.api.workflow.mapper.WfWorkflowRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/** 工作流业务层：CRUD + 执行 + SSE 推送。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfWorkflowService {

  private final WfWorkflowMapper workflowMapper;
  private final WfWorkflowRunMapper runMapper;
  private final WfNodeRunMapper nodeRunMapper;
  private final WorkflowEngine workflowEngine;
  private final ObjectMapper objectMapper;

  // ============ CRUD ============

  /** 创建工作流。 */
  @Transactional
  public WfWorkflow create(String name, String description, String graph, Long ownerId) {
    WfWorkflow wf = new WfWorkflow();
    wf.setWorkflowId(UUID.randomUUID().toString().replace("-", ""));
    wf.setName(name != null ? name : "未命名工作流");
    wf.setDescription(description != null ? description : "");
    wf.setGraph(
        graph != null
            ? graph
            : "{\"version\":\"1.0\",\"viewport\":{\"x\":0,\"y\":0,\"zoom\":1},\"nodes\":[],\"edges\":[]}");
    wf.setVersion(1);
    wf.setPublished(0);
    wf.setOwnerId(ownerId);
    wf.setDeleted(0);
    workflowMapper.insert(wf);
    return wf;
  }

  /** 更新工作流草稿。 */
  @Transactional
  public WfWorkflow updateDraft(String workflowId, String name, String description, String graph) {
    WfWorkflow wf = getByWorkflowId(workflowId);
    if (name != null) wf.setName(name);
    if (description != null) wf.setDescription(description);
    if (graph != null) wf.setGraph(graph);
    workflowMapper.updateById(wf);
    return wf;
  }

  /** 发布工作流（版本号 +1）。 */
  @Transactional
  public WfWorkflow publish(String workflowId) {
    WfWorkflow wf = getByWorkflowId(workflowId);
    wf.setVersion(wf.getVersion() + 1);
    wf.setPublished(1);
    workflowMapper.updateById(wf);
    return wf;
  }

  /** 获取工作流详情。 */
  public WfWorkflow getByWorkflowId(String workflowId) {
    LambdaQueryWrapper<WfWorkflow> qw =
        new LambdaQueryWrapper<WfWorkflow>()
            .eq(WfWorkflow::getWorkflowId, workflowId)
            .eq(WfWorkflow::getDeleted, 0);
    WfWorkflow wf = workflowMapper.selectOne(qw);
    if (wf == null) {
      throw new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND, "工作流不存在: " + workflowId);
    }
    return wf;
  }

  /** 工作流列表。 */
  public List<WfWorkflow> list(Long ownerId) {
    LambdaQueryWrapper<WfWorkflow> qw =
        new LambdaQueryWrapper<WfWorkflow>()
            .eq(WfWorkflow::getDeleted, 0)
            .eq(ownerId != null, WfWorkflow::getOwnerId, ownerId)
            .orderByDesc(WfWorkflow::getUpdatedAt);
    return workflowMapper.selectList(qw);
  }

  /** 删除工作流（逻辑删除）。 */
  @Transactional
  public void delete(String workflowId) {
    WfWorkflow wf = getByWorkflowId(workflowId);
    wf.setDeleted(1);
    workflowMapper.updateById(wf);
  }

  /** 复制工作流。 */
  @Transactional
  public WfWorkflow duplicate(String workflowId) {
    WfWorkflow source = getByWorkflowId(workflowId);
    WfWorkflow copy = new WfWorkflow();
    copy.setWorkflowId(UUID.randomUUID().toString().replace("-", ""));
    copy.setName(source.getName() + " (副本)");
    copy.setDescription(source.getDescription());
    copy.setGraph(source.getGraph());
    copy.setVersion(1);
    copy.setPublished(0);
    copy.setOwnerId(source.getOwnerId());
    copy.setDeleted(0);
    workflowMapper.insert(copy);
    return copy;
  }

  // ============ 执行 ============

  /** 运行工作流（同步返回结果 + 通过 SseEmitter 推送事件）。 */
  public WfWorkflowRun run(String workflowId, Map<String, Object> inputs, SseEmitter sseEmitter) {
    WfWorkflow wf = getByWorkflowId(workflowId);
    String runId = UUID.randomUUID().toString().replace("-", "");

    // 创建运行记录
    WfWorkflowRun runRecord = new WfWorkflowRun();
    runRecord.setRunId(runId);
    runRecord.setWorkflowId(workflowId);
    runRecord.setVersion(wf.getVersion());
    runRecord.setStatus("running");
    runRecord.setInputs(toJson(inputs));
    runRecord.setTriggeredBy("manual");
    runMapper.insert(runRecord);

    // SSE 事件消费者
    Consumer<WorkflowEvent> eventSink =
        event -> {
          try {
            String json = objectMapper.writeValueAsString(event);
            sseEmitter.send(SseEmitter.event().name("workflow").data(json));
            // 记录节点运行
            if (WorkflowEvent.NODE_SUCCEEDED.equals(event.eventType())
                || WorkflowEvent.NODE_FAILED.equals(event.eventType())) {
              saveNodeRun(runId, event);
            }
          } catch (Exception e) {
            log.warn("[WfWorkflowService][SSE send failed]", e);
          }
        };

    try {
      Map<String, Object> outputs = workflowEngine.execute(wf.getGraph(), runId, inputs, eventSink);
      // 更新运行记录
      runRecord.setStatus("succeeded");
      runRecord.setOutputs(toJson(outputs));
      runRecord.setFinishedAt(LocalDateTime.now());
      runMapper.updateById(runRecord);
    } catch (Exception e) {
      runRecord.setStatus("failed");
      runRecord.setErrorMessage(e.getMessage());
      runRecord.setFinishedAt(LocalDateTime.now());
      runMapper.updateById(runRecord);
      throw e;
    } finally {
      try {
        sseEmitter.complete();
      } catch (Exception ignored) {
      }
    }

    return runRecord;
  }

  /** 单节点调试。 */
  public Map<String, Object> debugNode(
      String nodeType, Map<String, Object> data, Map<String, Object> inputs) {
    return workflowEngine.executeNode(nodeType, data, inputs);
  }

  // ============ 运行历史 ============

  /** 查询工作流运行历史。 */
  public List<WfWorkflowRun> listRuns(String workflowId) {
    LambdaQueryWrapper<WfWorkflowRun> qw =
        new LambdaQueryWrapper<WfWorkflowRun>()
            .eq(WfWorkflowRun::getWorkflowId, workflowId)
            .orderByDesc(WfWorkflowRun::getCreatedAt);
    return runMapper.selectList(qw);
  }

  // ============ 内部方法 ============

  private void saveNodeRun(String runId, WorkflowEvent event) {
    WfNodeRun nodeRun = new WfNodeRun();
    nodeRun.setRunId(runId);
    nodeRun.setNodeId(event.nodeId());
    nodeRun.setNodeType(event.nodeType());
    nodeRun.setTitle(event.title());
    nodeRun.setStatus(
        WorkflowEvent.NODE_SUCCEEDED.equals(event.eventType()) ? "succeeded" : "failed");
    if (event.data() != null) {
      nodeRun.setOutputs(toJson(event.data()));
      if (event.data().containsKey("error")) {
        nodeRun.setErrorMessage(String.valueOf(event.data().get("error")));
      }
    }
    nodeRun.setFinishedAt(LocalDateTime.now());
    nodeRunMapper.insert(nodeRun);
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      return "{}";
    }
  }
}
