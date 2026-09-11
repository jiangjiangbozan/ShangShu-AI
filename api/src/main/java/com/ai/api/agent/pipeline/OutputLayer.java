package com.ai.api.agent.pipeline;

import com.ai.api.agent.pipeline.Layer.Phase;
import com.ai.api.agent.runner.AgentStrategy;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 输出 Layer（add-agent-runner-pipeline）。
 *
 * <p>职责：模型输出解析、结构化输出、输出格式校验。
 *
 * <ul>
 *   <li>COT 策略：解析模型 JSON（thought / action / final_answer / ask_human），容错提取首个平衡 JSON 对象
 *   <li>FC 策略：模型已原生完成工具循环，输出即最终答案
 * </ul>
 *
 * <p>解析失败时写入 {@code ctx.error}，由 Runner 终止并标记失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutputLayer implements Layer {

  private final ObjectMapper objectMapper;

  @Override
  public Phase phase() {
    return Phase.RESPONSE;
  }

  @Override
  public int order() {
    return 10;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void process(PipelineContext ctx) {
    String modelOutput = ctx.getModelOutput();
    if (ctx.getStrategy() == AgentStrategy.FC) {
      // Function Calling：ChatClient 内部已执行工具循环，输出即最终答案
      ctx.setParsedOutput(Map.of("final_answer", modelOutput == null ? "" : modelOutput));
      return;
    }

    Map<String, Object> parsed = parseLlmOutput(modelOutput);
    if (parsed == null) {
      ctx.setError("invalid llm output: " + (modelOutput == null ? "null" : modelOutput));
      return;
    }
    ctx.setParsedOutput(parsed);
  }

  /** 解析 LLM 输出 JSON。容错处理：提取第一个平衡 JSON 对象。 */
  private Map<String, Object> parseLlmOutput(String llmOutput) {
    if (llmOutput == null || llmOutput.isBlank()) return null;
    String trimmed = llmOutput.trim();

    // 直接尝试
    try {
      return objectMapper.readValue(trimmed, Map.class);
    } catch (Exception ignored) {
      // continue
    }

    // 提取首个平衡 {...}（手动括号计数，避免 Java 正则不支持递归平衡匹配）
    String json = extractFirstJsonObject(trimmed);
    if (json != null) {
      try {
        return objectMapper.readValue(json, Map.class);
      } catch (Exception e) {
        log.warn("[OutputLayer][parseLlmOutput][解析失败: {}]", e.getMessage());
        return null;
      }
    }
    return null;
  }

  /** 从第一个 '{' 开始，手动计数花括号提取首个平衡 JSON 对象。 */
  private String extractFirstJsonObject(String s) {
    int start = s.indexOf('{');
    if (start < 0) {
      return null;
    }
    int depth = 0;
    for (int i = start; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return s.substring(start, i + 1);
        }
      }
    }
    return null;
  }
}
