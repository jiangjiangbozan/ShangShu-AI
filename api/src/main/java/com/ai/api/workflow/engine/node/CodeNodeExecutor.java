package com.ai.api.workflow.engine.node;

import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import com.ai.api.workflow.engine.VariablePool;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Code 节点执行器。
 *
 * <p>执行简单的 JavaScript 表达式/代码片段（使用 GraalVM Polyglot 或 Nashorn）。 Phase 4 阶段使用 Nashorn 引擎（JDK
 * 内置），后续可替换为 GraalVM。
 *
 * <p>输入变量通过 {@code inputs} 配置映射，代码通过 {@code code} 字段指定， 代码中可通过 {@code result} 对象设置输出。
 */
@Slf4j
@Component
public class CodeNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "code";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) throws Exception {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    String codeLanguage = (String) data.getOrDefault("codeLanguage", "javascript");
    String code = (String) data.getOrDefault("code", "");
    Map<String, Object> inputMappings = (Map<String, Object>) data.getOrDefault("inputs", Map.of());

    // 解析输入变量
    Map<String, Object> resolvedInputs = new HashMap<>();
    for (Map.Entry<String, Object> entry : inputMappings.entrySet()) {
      Object val = entry.getValue();
      if (val instanceof String s && s.startsWith("${") && s.endsWith("}")) {
        resolvedInputs.put(entry.getKey(), vars.resolve(s));
      } else {
        resolvedInputs.put(entry.getKey(), val);
      }
    }

    Map<String, Object> outputs = new HashMap<>();

    if ("javascript".equals(codeLanguage) && !code.isBlank()) {
      // 使用 javax.script Nashorn 引擎执行 JS
      javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
      javax.script.ScriptEngine engine = manager.getEngineByName("nashorn");
      if (engine == null) {
        // Nashorn 在 JDK 15+ 被移除，尝试 GraalVM
        engine = manager.getEngineByName("graal.js");
      }
      if (engine == null) {
        throw new UnsupportedOperationException(
            "No JavaScript engine available. Please use JDK 11-14 or add GraalVM JS dependency.");
      }

      // 注入变量
      for (Map.Entry<String, Object> entry : resolvedInputs.entrySet()) {
        engine.put(entry.getKey(), entry.getValue());
      }
      // result 对象用于收集输出
      Map<String, Object> result = new HashMap<>();
      engine.put("result", result);

      engine.eval(code);

      // 读取 result
      @SuppressWarnings("unchecked")
      Map<String, Object> resultMap = (Map<String, Object>) engine.get("result");
      if (resultMap != null) {
        outputs.putAll(resultMap);
      }
    } else {
      log.warn(
          "[CodeNode][{}][language={} not supported, skipping]", context.nodeId(), codeLanguage);
    }

    log.info("[CodeNode][{}][outputs={}]", context.nodeId(), outputs.keySet());
    return NodeResult.of(outputs);
  }
}
