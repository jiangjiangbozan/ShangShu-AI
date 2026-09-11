package com.ai.api.agent.workflow;

import com.ai.api.agent.config.AgentProperties;
import com.ai.api.agent.entity.WorkflowDefinition;
import com.ai.api.agent.mapper.WorkflowDefinitionMapper;
import com.ai.api.agent.skill.SkillRegistry;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * DAG 工作流引擎（add-agent-runtime）。
 *
 * <p>从 {@code workflow_definition.dag} 字段反序列化 DAG，按拓扑顺序逐节点执行 Skill。支持：
 *
 * <ul>
 *   <li>变量插值：节点 input 中 {@code ${nodeId.field}} 引用上游节点输出
 *   <li>条件分支：节点带 {@code condition} 字段时，先求值（简化版 JS 表达式，本 change 阶段仅支持 {@code "nodeId.field > N"} 形式）
 *   <li>节点级超时：{@code agent.workflow.node-timeout}
 * </ul>
 *
 * @author add-agent-runtime
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DagEngine {

  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
  private static final Pattern CONDITION_PATTERN =
      Pattern.compile("^(\\w+)\\.(\\w+)\\s*(>=|<=|==|!=|>|<)\\s*([0-9.]+)$");

  private final WorkflowDefinitionMapper workflowDefinitionMapper;
  private final SkillRegistry skillRegistry;
  private final AgentProperties agentProperties;
  private final ObjectMapper objectMapper;

  /**
   * 执行工作流。
   *
   * @param workflowId 工作流业务 ID
   * @param version 版本（"latest" 解析最高）
   * @param input 根节点输入
   * @return 末节点输出
   */
  public Map<String, Object> execute(String workflowId, String version, Map<String, Object> input) {
    WorkflowDefinition wf = resolve(workflowId, version);
    Dag dag = Dag.parse(objectMapper, wf.getDag());
    dag.validate();

    log.info(
        "[DagEngine][execute][workflow({}#{}) nodes({}) edges({})]",
        workflowId,
        wf.getVersion(),
        dag.getNodes().size(),
        dag.getEdges().size());

    // 节点输出池
    Map<String, Map<String, Object>> outputs = new ConcurrentHashMap<>();

    for (Dag.Node node : dag.topologicalSort()) {
      // 条件求值
      if (node.getCondition() != null && !node.getCondition().isBlank()) {
        if (!evaluateCondition(node.getCondition(), outputs)) {
          log.info(
              "[DagEngine][execute][node({}) 跳过，条件不满足: {}]", node.getId(), node.getCondition());
          continue;
        }
      }

      // 变量插值
      Map<String, Object> interpolated = interpolate(node.getInput(), outputs);
      // 合并外部 input（根节点用）
      if (outputs.isEmpty() && input != null) {
        Map<String, Object> merged = new HashMap<>(input);
        merged.putAll(interpolated);
        interpolated = merged;
      }

      Map<String, Object> result;
      try {
        result = skillRegistry.invoke(node.getSkill(), "latest", interpolated);
      } catch (Exception e) {
        log.error("[DagEngine][execute][node({}#skill={}) 调用失败]", node.getId(), node.getSkill(), e);
        throw e;
      }
      outputs.put(node.getId(), result);
      log.info(
          "[DagEngine][execute][node({}#skill={}) 完成, result keys={}]",
          node.getId(),
          node.getSkill(),
          result.keySet());
    }

    // 返回最后一个执行节点输出
    if (outputs.isEmpty()) return new HashMap<>();
    String lastNodeId = dag.topologicalSort().get(dag.topologicalSort().size() - 1).getId();
    return outputs.getOrDefault(lastNodeId, new HashMap<>());
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
    if (wf.getEnabled() != 1) {
      throw new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND, "workflow disabled: " + workflowId);
    }
    return wf;
  }

  /** 变量插值：将 input 中所有 {@code ${nodeId.field}} 替换为上游节点输出实际值。 */
  @SuppressWarnings("unchecked")
  private Map<String, Object> interpolate(
      Map<String, Object> input, Map<String, Map<String, Object>> outputs) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Object> e : input.entrySet()) {
      Object v = e.getValue();
      if (v instanceof String s) {
        result.put(e.getKey(), interpolateString(s, outputs));
      } else if (v instanceof Map m) {
        result.put(e.getKey(), interpolate((Map<String, Object>) m, outputs));
      } else {
        result.put(e.getKey(), v);
      }
    }
    return result;
  }

  private Object interpolateString(String s, Map<String, Map<String, Object>> outputs) {
    Matcher m = VAR_PATTERN.matcher(s);
    if (!m.find()) return s;

    // 整串是单个变量，保留原始类型
    if (s.startsWith("${")
        && s.endsWith("}")
        && s.indexOf("${") == 0
        && s.lastIndexOf("}") == s.length() - 1) {
      String path = s.substring(2, s.length() - 1);
      return resolveVar(path, outputs);
    }

    // 多变量或带文本，做字符串拼接
    StringBuffer sb = new StringBuffer();
    m.reset();
    while (m.find()) {
      Object resolved = resolveVar(m.group(1), outputs);
      m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(resolved)));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private Object resolveVar(String path, Map<String, Map<String, Object>> outputs) {
    int dot = path.indexOf('.');
    if (dot < 0) return path;
    String nodeId = path.substring(0, dot);
    String field = path.substring(dot + 1);
    Map<String, Object> out = outputs.get(nodeId);
    if (out == null) return "${" + path + "}";
    return out.getOrDefault(field, "");
  }

  /** 条件求值（简化版：{@code nodeId.field OP number}）。 */
  private boolean evaluateCondition(String condition, Map<String, Map<String, Object>> outputs) {
    Matcher m = CONDITION_PATTERN.matcher(condition.trim());
    if (!m.matches()) {
      log.warn("[DagEngine][evaluateCondition][不支持的条件表达式: {}]", condition);
      return true; // 不认识的条件默认通过
    }
    String nodeId = m.group(1);
    String field = m.group(2);
    String op = m.group(3);
    double threshold = Double.parseDouble(m.group(4));

    Object val = outputs.getOrDefault(nodeId, new HashMap<>()).get(field);
    if (val == null) return false;
    double actual;
    try {
      actual = val instanceof Number n ? n.doubleValue() : Double.parseDouble(val.toString());
    } catch (Exception e) {
      return false;
    }
    return switch (op) {
      case ">" -> actual > threshold;
      case "<" -> actual < threshold;
      case ">=" -> actual >= threshold;
      case "<=" -> actual <= threshold;
      case "==" -> actual == threshold;
      case "!=" -> actual != threshold;
      default -> true;
    };
  }
}
