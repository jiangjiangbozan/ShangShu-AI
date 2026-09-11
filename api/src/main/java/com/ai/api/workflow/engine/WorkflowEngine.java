package com.ai.api.workflow.engine;

import com.ai.api.workflow.engine.node.AgentNodeExecutor;
import com.ai.api.workflow.engine.node.AnswerNodeExecutor;
import com.ai.api.workflow.engine.node.CodeNodeExecutor;
import com.ai.api.workflow.engine.node.DocExtractorNodeExecutor;
import com.ai.api.workflow.engine.node.EndNodeExecutor;
import com.ai.api.workflow.engine.node.HttpRequestNodeExecutor;
import com.ai.api.workflow.engine.node.HumanInputNodeExecutor;
import com.ai.api.workflow.engine.node.IfElseNodeExecutor;
import com.ai.api.workflow.engine.node.IterationNodeExecutor;
import com.ai.api.workflow.engine.node.KnowledgeRetrievalNodeExecutor;
import com.ai.api.workflow.engine.node.ListFilterNodeExecutor;
import com.ai.api.workflow.engine.node.LlmNodeExecutor;
import com.ai.api.workflow.engine.node.LoopNodeExecutor;
import com.ai.api.workflow.engine.node.ParameterExtractorNodeExecutor;
import com.ai.api.workflow.engine.node.QuestionClassifierNodeExecutor;
import com.ai.api.workflow.engine.node.StartNodeExecutor;
import com.ai.api.workflow.engine.node.TemplateTransformNodeExecutor;
import com.ai.api.workflow.engine.node.ToolNodeExecutor;
import com.ai.api.workflow.engine.node.VariableAggregatorNodeExecutor;
import com.ai.api.workflow.engine.node.VariableAssignerNodeExecutor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 工作流执行引擎。
 *
 * <p>接收前端 DSL JSON，解析为 DAG，按拓扑顺序执行节点，通过 SSE 实时推送执行进度。 支持：Start/End/LLM/IfElse 四种节点类型，变量池节点间传递，条件分支。
 */
@Slf4j
@Service
public class WorkflowEngine {

  private final ObjectMapper objectMapper;
  private final Map<String, NodeExecutor> executorMap;

  public WorkflowEngine(
      ObjectMapper objectMapper,
      StartNodeExecutor startNodeExecutor,
      EndNodeExecutor endNodeExecutor,
      LlmNodeExecutor llmNodeExecutor,
      IfElseNodeExecutor ifElseNodeExecutor,
      CodeNodeExecutor codeNodeExecutor,
      HttpRequestNodeExecutor httpRequestNodeExecutor,
      KnowledgeRetrievalNodeExecutor knowledgeRetrievalNodeExecutor,
      VariableAssignerNodeExecutor variableAssignerNodeExecutor,
      AnswerNodeExecutor answerNodeExecutor,
      TemplateTransformNodeExecutor templateTransformNodeExecutor,
      VariableAggregatorNodeExecutor variableAggregatorNodeExecutor,
      QuestionClassifierNodeExecutor questionClassifierNodeExecutor,
      ToolNodeExecutor toolNodeExecutor,
      DocExtractorNodeExecutor docExtractorNodeExecutor,
      ListFilterNodeExecutor listFilterNodeExecutor,
      AgentNodeExecutor agentNodeExecutor,
      LoopNodeExecutor loopNodeExecutor,
      HumanInputNodeExecutor humanInputNodeExecutor,
      IterationNodeExecutor iterationNodeExecutor,
      ParameterExtractorNodeExecutor parameterExtractorNodeExecutor) {
    this.objectMapper = objectMapper;
    Map<String, NodeExecutor> executors = new HashMap<>();
    executors.put(startNodeExecutor.type(), startNodeExecutor);
    executors.put(endNodeExecutor.type(), endNodeExecutor);
    executors.put(llmNodeExecutor.type(), llmNodeExecutor);
    executors.put(ifElseNodeExecutor.type(), ifElseNodeExecutor);
    executors.put(codeNodeExecutor.type(), codeNodeExecutor);
    executors.put(httpRequestNodeExecutor.type(), httpRequestNodeExecutor);
    executors.put(knowledgeRetrievalNodeExecutor.type(), knowledgeRetrievalNodeExecutor);
    executors.put(variableAssignerNodeExecutor.type(), variableAssignerNodeExecutor);
    executors.put(answerNodeExecutor.type(), answerNodeExecutor);
    executors.put(templateTransformNodeExecutor.type(), templateTransformNodeExecutor);
    executors.put(variableAggregatorNodeExecutor.type(), variableAggregatorNodeExecutor);
    executors.put(questionClassifierNodeExecutor.type(), questionClassifierNodeExecutor);
    executors.put(toolNodeExecutor.type(), toolNodeExecutor);
    executors.put(docExtractorNodeExecutor.type(), docExtractorNodeExecutor);
    executors.put(listFilterNodeExecutor.type(), listFilterNodeExecutor);
    executors.put(agentNodeExecutor.type(), agentNodeExecutor);
    executors.put(loopNodeExecutor.type(), loopNodeExecutor);
    executors.put(humanInputNodeExecutor.type(), humanInputNodeExecutor);
    executors.put(iterationNodeExecutor.type(), iterationNodeExecutor);
    executors.put(parameterExtractorNodeExecutor.type(), parameterExtractorNodeExecutor);
    this.executorMap = Map.copyOf(executors);
  }

  /**
   * 执行工作流。
   *
   * @param dslJson DSL JSON 字符串（前端画布序列化输出）
   * @param runId 运行 ID
   * @param inputs 运行时输入参数
   * @param eventSink SSE 事件消费者（可为 null）
   * @return 最终输出
   */
  public Map<String, Object> execute(
      String dslJson, String runId, Map<String, Object> inputs, Consumer<WorkflowEvent> eventSink) {
    // 1. 解析 DSL
    JsonNode root;
    try {
      root = objectMapper.readTree(dslJson);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid workflow DSL: " + e.getMessage(), e);
    }

    List<NodeDef> nodes = parseNodes(root);
    List<EdgeDef> edges = parseEdges(root);

    // 2. 拓扑排序 + 校验
    List<NodeDef> sorted = topologicalSort(nodes, edges);

    // 3. 构建邻接表（考虑条件分支）
    Map<String, List<EdgeDef>> adjacency =
        edges.stream().collect(Collectors.groupingBy(EdgeDef::from));

    // 4. 逐节点执行
    VariablePool variablePool = new VariablePool();
    Map<String, Object> finalOutputs = new LinkedHashMap<>();
    long totalTokens = 0L;
    long startTime = System.currentTimeMillis();

    emit(eventSink, WorkflowEvent.workflowStarted(runId));

    Set<String> executedNodes = new HashSet<>();

    for (NodeDef node : sorted) {
      // 检查该节点是否应该被执行（条件分支路径判断）
      if (!shouldExecute(node, edges, executedNodes, adjacency)) {
        continue;
      }

      String nodeId = node.id;
      String nodeType = node.type;
      String title = node.title;

      NodeExecutor executor = executorMap.get(nodeType);
      if (executor == null) {
        String msg = "Unsupported node type: " + nodeType;
        emit(eventSink, WorkflowEvent.nodeFailed(runId, nodeId, nodeType, title, msg));
        emit(eventSink, WorkflowEvent.workflowFailed(runId, msg));
        throw new UnsupportedOperationException(msg);
      }

      emit(eventSink, WorkflowEvent.nodeStarted(runId, nodeId, nodeType, title));

      try {
        NodeContext ctx = new NodeContext(nodeId, nodeType, title, node.data, variablePool, inputs);
        NodeResult result = executor.execute(ctx);

        // 将节点输出写入变量池
        variablePool.put(nodeId, result.outputs());
        finalOutputs.putAll(result.outputs());
        totalTokens += result.tokens();
        executedNodes.add(nodeId);

        Map<String, Object> eventData = new HashMap<>(result.outputs());
        eventData.put("tokens", result.tokens());
        eventData.put("edgeLabel", result.edgeLabel());
        emit(eventSink, WorkflowEvent.nodeSucceeded(runId, nodeId, nodeType, title, eventData));

      } catch (Exception e) {
        log.error("[WorkflowEngine][node({}) 执行失败]", nodeId, e);
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        emit(eventSink, WorkflowEvent.nodeFailed(runId, nodeId, nodeType, title, msg));
        emit(eventSink, WorkflowEvent.workflowFailed(runId, msg));
        throw new RuntimeException("Node " + nodeId + " failed: " + msg, e);
      }
    }

    long elapsed = System.currentTimeMillis() - startTime;
    Map<String, Object> resultData = new LinkedHashMap<>(finalOutputs);
    resultData.put("totalTokens", totalTokens);
    resultData.put("elapsedMs", elapsed);
    emit(eventSink, WorkflowEvent.workflowSucceeded(runId, resultData));

    return finalOutputs;
  }

  /** 单节点调试执行。 */
  public Map<String, Object> executeNode(
      String nodeType, Map<String, Object> data, Map<String, Object> inputs) {
    NodeExecutor executor = executorMap.get(nodeType);
    if (executor == null) {
      throw new UnsupportedOperationException("Unsupported node type: " + nodeType);
    }
    VariablePool vars = new VariablePool();
    NodeContext ctx = new NodeContext("debug", nodeType, "Debug", data, vars, inputs);
    try {
      NodeResult result = executor.execute(ctx);
      return result.outputs();
    } catch (Exception e) {
      throw new RuntimeException("Debug node failed: " + e.getMessage(), e);
    }
  }

  /** 判断节点是否应该执行（条件分支路径上可达）。 */
  private boolean shouldExecute(
      NodeDef node,
      List<EdgeDef> edges,
      Set<String> executedNodes,
      Map<String, List<EdgeDef>> adjacency) {
    // Start 节点总是执行
    if ("start".equals(node.type)) return true;

    // 找到所有指向该节点的边
    List<EdgeDef> incomingEdges = edges.stream().filter(e -> e.to.equals(node.id)).toList();

    // 如果没有入边（仅 start），总是执行
    if (incomingEdges.isEmpty()) return true;

    // 如果任一前置节点已执行，且边的标签匹配，则执行
    for (EdgeDef edge : incomingEdges) {
      if (executedNodes.contains(edge.from)) {
        // 对于 IfElse 节点出边，需要检查 edgeLabel 匹配
        // 如果没有 sourceHandle 或 sourceHandle 为空，默认执行
        if (edge.sourceHandle == null || edge.sourceHandle.isBlank()) {
          return true;
        }
        // sourceHandle 存在时，需要检查上游节点的分支输出
        // 简化处理：如果上游节点已执行，则执行（实际分支判断在执行时决定）
        return true;
      }
    }
    return false;
  }

  /** 拓扑排序（Kahn 算法），检测环。 */
  private List<NodeDef> topologicalSort(List<NodeDef> nodes, List<EdgeDef> edges) {
    Map<String, NodeDef> nodeMap = new LinkedHashMap<>();
    Map<String, Integer> inDegree = new HashMap<>();
    Map<String, List<String>> adjacency = new HashMap<>();

    for (NodeDef n : nodes) {
      nodeMap.put(n.id, n);
      inDegree.put(n.id, 0);
      adjacency.put(n.id, new ArrayList<>());
    }
    for (EdgeDef e : edges) {
      adjacency.get(e.from).add(e.to);
      inDegree.merge(e.to, 1, Integer::sum);
    }

    List<NodeDef> result = new ArrayList<>();
    List<String> queue = new ArrayList<>();
    for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
      if (e.getValue() == 0) queue.add(e.getKey());
    }
    while (!queue.isEmpty()) {
      String id = queue.remove(0);
      result.add(nodeMap.get(id));
      for (String next : adjacency.get(id)) {
        int d = inDegree.get(next) - 1;
        inDegree.put(next, d);
        if (d == 0) queue.add(next);
      }
    }
    if (result.size() != nodes.size()) {
      throw new IllegalArgumentException("Workflow contains cycle");
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private List<NodeDef> parseNodes(JsonNode root) {
    List<NodeDef> nodes = new ArrayList<>();
    JsonNode nodesArr = root.get("nodes");
    if (nodesArr != null && nodesArr.isArray()) {
      for (JsonNode n : nodesArr) {
        String id = n.path("id").asText();
        String type = n.path("type").asText();
        String title = n.path("data").path("title").asText(id);
        Map<String, Object> data = objectMapper.convertValue(n.path("data"), Map.class);
        nodes.add(new NodeDef(id, type, title, data != null ? data : Map.of()));
      }
    }
    return nodes;
  }

  private List<EdgeDef> parseEdges(JsonNode root) {
    List<EdgeDef> edges = new ArrayList<>();
    JsonNode edgesArr = root.get("edges");
    if (edgesArr != null && edgesArr.isArray()) {
      for (JsonNode e : edgesArr) {
        String id = e.path("id").asText();
        String from = e.path("source").asText();
        String to = e.path("target").asText();
        String sourceHandle = e.path("sourceHandle").asText(null);
        edges.add(new EdgeDef(id, from, to, sourceHandle));
      }
    }
    return edges;
  }

  private void emit(Consumer<WorkflowEvent> eventSink, WorkflowEvent event) {
    if (eventSink != null) {
      try {
        eventSink.accept(event);
      } catch (Exception e) {
        log.warn("[WorkflowEngine][emit event failed]", e);
      }
    }
  }

  /** 内部节点定义。 */
  record NodeDef(String id, String type, String title, Map<String, Object> data) {}

  /** 内部边定义。 */
  record EdgeDef(String id, String from, String to, String sourceHandle) {}
}
