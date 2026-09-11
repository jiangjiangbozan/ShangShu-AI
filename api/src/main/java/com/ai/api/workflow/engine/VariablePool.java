package com.ai.api.workflow.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流变量池。
 *
 * <p>每个节点执行后，将输出写入 VariablePool；下游节点通过 {@code ${nodeId.outputKey}} 引用上游节点输出。
 * 支持：整串变量引用（保留原始类型）、字符串内嵌变量（拼接为字符串）。
 */
public class VariablePool {

  /** nodeId → outputs */
  private final Map<String, Map<String, Object>> pool = new HashMap<>();

  /** 写入节点输出。 */
  public void put(String nodeId, Map<String, Object> outputs) {
    pool.put(nodeId, outputs);
  }

  /** 读取节点完整输出。 */
  public Map<String, Object> get(String nodeId) {
    return pool.getOrDefault(nodeId, Map.of());
  }

  /** 读取节点单个输出字段。 */
  public Object get(String nodeId, String key) {
    Map<String, Object> outputs = pool.get(nodeId);
    return outputs != null ? outputs.get(key) : null;
  }

  /**
   * 变量插值：将字符串中 {@code ${nodeId.key}} 替换为实际值。
   *
   * <p>如果整个字符串恰好是一个变量引用，则保留原始类型（如 List、Number）； 否则做字符串拼接。
   */
  public Object resolve(String template) {
    if (template == null || !template.contains("${")) {
      return template;
    }

    // 整串是单个变量引用 → 保留原始类型
    if (template.startsWith("${")
        && template.endsWith("}")
        && template.indexOf("${") == 0
        && template.lastIndexOf("}") == template.length() - 1) {
      String path = template.substring(2, template.length() - 1);
      return resolvePath(path);
    }

    // 包含多个变量或带其他文本 → 字符串拼接
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < template.length()) {
      int start = template.indexOf("${", i);
      if (start < 0) {
        sb.append(template, i, template.length());
        break;
      }
      int end = template.indexOf("}", start);
      if (end < 0) {
        sb.append(template, i, template.length());
        break;
      }
      sb.append(template, i, start);
      String path = template.substring(start + 2, end);
      Object value = resolvePath(path);
      sb.append(value != null ? value : "");
      i = end + 1;
    }
    return sb.toString();
  }

  /** 解析变量路径 nodeId.key。 */
  private Object resolvePath(String path) {
    int dot = path.indexOf('.');
    if (dot < 0) {
      return pool.get(path);
    }
    String nodeId = path.substring(0, dot);
    String key = path.substring(dot + 1);
    return get(nodeId, key);
  }

  /** 清空变量池。 */
  public void clear() {
    pool.clear();
  }
}
