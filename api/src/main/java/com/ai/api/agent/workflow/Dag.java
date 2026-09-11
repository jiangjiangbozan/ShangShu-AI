package com.ai.api.agent.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DAG（有向无环图）模型（add-agent-runtime）。
 *
 * <p>从 workflow_definition.dag JSON 字段反序列化得到。提供拓扑校验与拓扑排序。
 *
 * @author add-agent-runtime
 */
@Slf4j
public class Dag {

  /** 节点列表。 */
  private final List<Node> nodes;

  /** 边列表。 */
  private final List<Edge> edges;

  /** 节点 ID → Node 索引。 */
  private final Map<String, Node> nodeMap;

  /** 节点 ID → 直接前驱节点 ID 集合。 */
  private final Map<String, Set<String>> predecessors;

  /** 节点 ID → 直接后继节点 ID 集合。 */
  private final Map<String, Set<String>> successors;

  public Dag(List<Node> nodes, List<Edge> edges) {
    this.nodes = nodes;
    this.edges = edges;
    this.nodeMap = new HashMap<>();
    for (Node n : nodes) nodeMap.put(n.getId(), n);
    this.predecessors = new HashMap<>();
    this.successors = new HashMap<>();
    for (Node n : nodes) {
      predecessors.put(n.getId(), new HashSet<>());
      successors.put(n.getId(), new HashSet<>());
    }
    for (Edge e : edges) {
      Set<String> succ = successors.get(e.getFrom());
      if (succ != null) succ.add(e.getTo());
      Set<String> pred = predecessors.get(e.getTo());
      if (pred != null) pred.add(e.getFrom());
    }
  }

  /**
   * 从 JSON 反序列化。JSON 结构：{@code
   * {"nodes":[{"id":"n1","skill":"calculator",...}],"edges":[{"from":"n1","to":"n2"}]}}。
   */
  public static Dag parse(ObjectMapper objectMapper, String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      List<Node> nodes = new ArrayList<>();
      List<Edge> edges = new ArrayList<>();

      JsonNode nodesArr = root.get("nodes");
      if (nodesArr != null && nodesArr.isArray()) {
        for (JsonNode n : nodesArr) {
          Node node = new Node();
          node.setId(n.get("id").asText());
          node.setSkill(n.get("skill").asText());
          node.setName(n.has("name") ? n.get("name").asText() : n.get("id").asText());
          if (n.has("input")) {
            Map<String, Object> input = objectMapper.convertValue(n.get("input"), Map.class);
            node.setInput(input);
          } else {
            node.setInput(new HashMap<>());
          }
          if (n.has("condition")) {
            node.setCondition(n.get("condition").asText());
          }
          nodes.add(node);
        }
      }

      JsonNode edgesArr = root.get("edges");
      if (edgesArr != null && edgesArr.isArray()) {
        for (JsonNode e : edgesArr) {
          Edge edge = new Edge();
          edge.setFrom(e.get("from").asText());
          edge.setTo(e.get("to").asText());
          if (e.has("condition")) {
            edge.setCondition(e.get("condition").asText());
          }
          edges.add(edge);
        }
      }

      return new Dag(nodes, edges);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid dag json: " + e.getMessage(), e);
    }
  }

  /** 校验 DAG 合法性：无环 + 无孤立节点 + 节点/边引用一致。 */
  public void validate() {
    if (nodes.isEmpty()) throw new IllegalArgumentException("dag has no nodes");

    // 引用一致性
    for (Edge e : edges) {
      if (!nodeMap.containsKey(e.getFrom()))
        throw new IllegalArgumentException("edge from unknown node: " + e.getFrom());
      if (!nodeMap.containsKey(e.getTo()))
        throw new IllegalArgumentException("edge to unknown node: " + e.getTo());
    }

    // 拓扑排序检测环
    Map<String, Integer> indegree = new HashMap<>();
    for (Node n : nodes) indegree.put(n.getId(), predecessors.get(n.getId()).size());
    List<String> queue = new ArrayList<>();
    for (Map.Entry<String, Integer> e : indegree.entrySet()) {
      if (e.getValue() == 0) queue.add(e.getKey());
    }
    int visited = 0;
    while (!queue.isEmpty()) {
      String id = queue.remove(0);
      visited++;
      for (String next : successors.get(id)) {
        int d = indegree.get(next) - 1;
        indegree.put(next, d);
        if (d == 0) queue.add(next);
      }
    }
    if (visited != nodes.size()) {
      throw new IllegalArgumentException("dag has cycle");
    }
  }

  /** 拓扑排序（Kahn），返回 Node 列表。 */
  public List<Node> topologicalSort() {
    Map<String, Integer> indegree = new HashMap<>();
    for (Node n : nodes) indegree.put(n.getId(), predecessors.get(n.getId()).size());
    List<Node> result = new ArrayList<>();
    List<String> queue = new ArrayList<>();
    for (Node n : nodes) {
      if (indegree.get(n.getId()) == 0) queue.add(n.getId());
    }
    while (!queue.isEmpty()) {
      String id = queue.remove(0);
      result.add(nodeMap.get(id));
      for (String next : successors.get(id)) {
        int d = indegree.get(next) - 1;
        indegree.put(next, d);
        if (d == 0) queue.add(next);
      }
    }
    return result;
  }

  public List<Node> getNodes() {
    return nodes;
  }

  public List<Edge> getEdges() {
    return edges;
  }

  public Node getNode(String id) {
    return nodeMap.get(id);
  }

  /** DAG 节点。 */
  @Data
  public static class Node {
    private String id;
    private String name;

    /** Skill 名（调用 SkillRegistry.invoke 时使用）。 */
    private String skill;

    /** 输入参数（可引用上游节点输出，如 {@code {"expr":"${n1.result}"}}）。 */
    private Map<String, Object> input;

    /** 条件表达式（可选，形如 {@code "n1.result > 0"}）。 */
    private String condition;
  }

  /** DAG 边。 */
  @Data
  public static class Edge {
    private String from;
    private String to;

    /** 条件分支（可选）。 */
    private String condition;
  }
}
