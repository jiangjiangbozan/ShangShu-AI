package com.ai.api.agent.pipeline;

import com.ai.api.agent.config.AgentProperties;
import com.ai.api.agent.pipeline.Layer.Phase;
import com.ai.api.agent.runtime.AgentContext;
import com.ai.api.agent.runtime.AgentContextManager;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 上下文 Layer（add-agent-runner-pipeline）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>会话历史 / 快照上下文注入：对接 {@link AgentContextManager}，加载 AgentContext（断点续跑恢复）
 *   <li>上下文窗口裁剪：chatHistory 按滑动窗口保留最近 N 轮
 *   <li>上下文块格式化：历史观察 + 人工回复注入，供 PromptLayer 拼装
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextLayer implements Layer {

  /** 人工回复注入 observations 的固定 key（由 AgentRuntimeService.respond 写入）。 */
  public static final String HUMAN_RESPONSE_KEY = "ask_human_response";

  private final AgentContextManager agentContextManager;
  private final AgentProperties agentProperties;

  @Override
  public Phase phase() {
    return Phase.REQUEST;
  }

  @Override
  public int order() {
    return 10;
  }

  @Override
  public void process(PipelineContext ctx) {
    // 1. 快照上下文注入（未预加载时从 AgentContextManager 恢复）
    AgentContext agentContext = ctx.getAgentContext();
    if (agentContext == null) {
      agentContext = agentContextManager.load(ctx.getRuntimeId());
      ctx.setAgentContext(agentContext);
    }

    // 2. 用户 query 回填
    if (ctx.getUserQuery() == null || ctx.getUserQuery().isBlank()) {
      ctx.setUserQuery(agentContext.getUserQuery());
    }

    // 3. 会话历史窗口裁剪（保留最近 N 条消息）
    trimHistory(agentContext);

    // 4. 组装上下文块（历史观察 + 人工回复）
    ctx.setContextBlock(formatContextBlock(agentContext));
  }

  /** chatHistory 滑动窗口裁剪：超出 windowSize 的部分丢弃（保留最近 N 轮）。 */
  private void trimHistory(AgentContext agentContext) {
    List<Map<String, Object>> history = agentContext.getChatHistory();
    int windowSize = Math.max(1, agentProperties.getMemory().getWindowSize());
    if (history.size() > windowSize) {
      agentContext.setChatHistory(
          new java.util.ArrayList<>(history.subList(history.size() - windowSize, history.size())));
    }
  }

  /** 格式化历史观察与人工回复，供 PromptLayer 拼入系统提示词。 */
  private String formatContextBlock(AgentContext agentContext) {
    StringBuilder sb = new StringBuilder();
    if (agentContext.getObservations().isEmpty()) {
      sb.append("(无历史观察)\n");
    } else {
      agentContext
          .getObservations()
          .forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
    }
    return sb.toString();
  }
}
