package com.ai.api.agent.runner;

import com.ai.api.agent.pipeline.AgentPipeline;
import com.ai.api.agent.pipeline.Layer.Phase;
import com.ai.api.agent.pipeline.PipelineContext;
import com.ai.api.agent.runtime.AgentContext;
import com.ai.api.agent.runtime.AgentContextManager;
import com.ai.api.agent.runtime.AgentEventBus;
import com.ai.api.agent.runtime.AgentRuntimeService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * FC 执行器（add-agent-runner-pipeline）。
 *
 * <p>基于模型原生 Function Calling 的工具调用策略，不再解析思维文本：
 *
 * <ol>
 *   <li>管线 REQUEST：暂停检测 → 上下文注入 → ToolLayer 将 SkillRegistry 注册的 Skill 包装为 {@link SkillToolCallback}
 *   <li>单次模型调用：通过 {@link ChatClient#prompt()} 的 {@code tools()} 注册原生工具， 由 ChatClient
 *       内部自动执行工具循环（模型发工具调用 → 执行 → 回填结果 → 再推理）
 *   <li>模型最终输出即最终答案，无需正则解析
 * </ol>
 *
 * <p>每次工具调用经 SkillRegistry 触发统一回调管线，自动落 agent_trace。 暂停场景走整体级 PauseState（FC 工具循环由 ChatClient
 * 托管，不做步内挂起）。
 *
 * @author add-agent-runner-pipeline
 */
@Slf4j
@Component("fcAgentRunner")
@RequiredArgsConstructor
public class FcAgentRunner implements AgentRunner {

  private final AgentContextManager agentContextManager;
  private final AgentRuntimeService agentRuntimeService;
  private final AgentEventBus agentEventBus;
  private final AgentPipeline agentPipeline;

  /** 注入点：按会话 modelId 从注册表解析 ChatClient（默认 ollama）。 */
  private final Map<String, ChatClient> chatClientRegistry;

  @Override
  public AgentStrategy strategy() {
    return AgentStrategy.FC;
  }

  /**
   * 完整执行：一次 Function Calling 回合（工具循环由 ChatClient 内部托管）。
   *
   * @param runtimeId runtime ID
   */
  @Override
  public RunnerOutput run(Long runtimeId) {
    AgentContext context = agentContextManager.load(runtimeId);
    long startTime = System.currentTimeMillis();
    log.info(
        "[FcAgentRunner][run][runtimeId({}) nextStepIndex({})]",
        runtimeId,
        context.getNextStepIndex());

    // 管线 REQUEST：暂停检测 → 上下文注入 → 工具注册（ToolLayer）→ 提示词组装（PromptLayer）
    PipelineContext ctx = new PipelineContext();
    ctx.setRuntimeId(runtimeId);
    ctx.setStrategy(AgentStrategy.FC);
    ctx.setAgentContext(context);
    ctx.setUserQuery(context.getUserQuery());
    agentPipeline.run(Phase.REQUEST, ctx);
    if (ctx.isPaused()) {
      log.info("[FcAgentRunner][run][runtimeId({}) 暂停挂起]", runtimeId);
      return RunnerOutput.paused(runtimeId, context.getNextStepIndex(), ctx.getPauseState());
    }

    // 模型调用：原生 Function Calling，工具循环由 ChatClient 自动执行
    String answer;
    try {
      answer =
          resolveChatClient()
              .prompt()
              .system(ctx.getSystemPrompt())
              .user(ctx.getUserQuery())
              .tools(ctx.getToolCallbacks())
              .call()
              .content();
    } catch (Exception e) {
      log.error("[FcAgentRunner][run][runtimeId({}) LLM 调用失败]", runtimeId, e);
      agentRuntimeService.fail(runtimeId, "LLM call failed: " + e.getMessage(), null);
      return RunnerOutput.failed(
          runtimeId, "LLM call failed: " + e.getMessage(), context.getNextStepIndex());
    }

    if (answer == null || answer.isBlank()) {
      agentRuntimeService.fail(runtimeId, "LLM returned empty answer", null);
      return RunnerOutput.failed(
          runtimeId, "LLM returned empty answer", context.getNextStepIndex());
    }

    // 完成：快照 + 事件 + 状态机
    context.setNextStepIndex(context.getNextStepIndex() + 1);
    agentContextManager.snapshot(runtimeId, context);
    agentEventBus.publishDone(runtimeId, System.currentTimeMillis() - startTime);
    agentRuntimeService.done(runtimeId, answer);
    return RunnerOutput.done(runtimeId, answer, context.getNextStepIndex() - 1);
  }

  /** FC 策略单步即一次完整工具循环回合。 */
  @Override
  public RunnerOutput step(Long runtimeId) {
    return run(runtimeId);
  }

  /** 暂停：写入轻量 PauseState（NORMAL）+ 状态机 running → paused。 */
  @Override
  public void pause(Long runtimeId) {
    agentRuntimeService.pause(runtimeId);
  }

  /** 恢复：状态机 paused → running，重新派发任务。 */
  @Override
  public void resume(Long runtimeId) {
    agentRuntimeService.resume(runtimeId);
  }

  /** 从注册表解析默认 ChatClient（优先 ollama，回退到第一个可用）。 */
  private ChatClient resolveChatClient() {
    ChatClient client = chatClientRegistry.get("ollamaChatClient");
    if (client != null) {
      return client;
    }
    return chatClientRegistry.values().stream().findFirst().orElse(null);
  }
}
