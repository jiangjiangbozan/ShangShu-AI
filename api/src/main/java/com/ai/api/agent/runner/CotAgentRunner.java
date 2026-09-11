package com.ai.api.agent.runner;

import com.ai.api.agent.config.AgentProperties;
import com.ai.api.agent.pause.PauseState;
import com.ai.api.agent.pipeline.AgentPipeline;
import com.ai.api.agent.pipeline.Layer.Phase;
import com.ai.api.agent.pipeline.PipelineContext;
import com.ai.api.agent.pipeline.PromptLayer;
import com.ai.api.agent.pipeline.ToolLayer;
import com.ai.api.agent.runtime.AgentContext;
import com.ai.api.agent.runtime.AgentContextManager;
import com.ai.api.agent.runtime.AgentEventBus;
import com.ai.api.agent.runtime.AgentRuntimeService;
import com.ai.api.agent.skill.SkillInvoker;
import com.ai.api.agent.skill.SkillRegistry;
import com.ai.api.agent.trace.TraceRecorder;
import com.ai.api.common.exception.BusinessException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * CoT 执行器（add-agent-runner-pipeline，由 ReactEngine 改造）。
 *
 * <p>实现 Thought → Action → Observation 循环（ReAct 式文本解析策略）：
 *
 * <ol>
 *   <li>管线 REQUEST：AskHuman 暂停检测 → 上下文注入（AgentContextManager）→ 技能目录（ToolLayer）→ 提示词组装（PromptLayer）
 *   <li>Thought：LLM 推理，OutputLayer 解析 JSON（thought / action / final_answer / ask_human）
 *   <li>Action：Skill 调用，经 SkillRegistry 触发统一回调管线（TraceRecorder 自动落 trace）
 *   <li>Observation：结果存入 AgentContext，快照落盘（断点续跑兜底）
 * </ol>
 *
 * <p>人工介入：模型输出 {@code ask_human} 时，生成 {@link PauseState}（ASK_HUMAN）挂起执行， 收到人工回复后由 {@code
 * AgentRuntimeService.respond} 回填上下文，从 nextStepIndex 继续。
 *
 * @author add-agent-runner-pipeline
 */
@Slf4j
@Component("cotAgentRunner")
@RequiredArgsConstructor
public class CotAgentRunner implements AgentRunner {

  private final AgentContextManager agentContextManager;
  private final AgentRuntimeService agentRuntimeService;
  private final SkillRegistry skillRegistry;
  private final TraceRecorder traceRecorder;
  private final AgentProperties agentProperties;
  private final ObjectMapper objectMapper;
  private final AgentEventBus agentEventBus;
  private final AgentPipeline agentPipeline;
  private final PromptLayer promptLayer;
  private final ToolLayer toolLayer;

  /** 注入点：按会话 modelId 从注册表解析 ChatClient（默认 ollama）。 */
  private final Map<String, ChatClient> chatClientRegistry;

  @Override
  public AgentStrategy strategy() {
    return AgentStrategy.COT;
  }

  /**
   * 完整执行 ReAct 循环，直至 DONE / PAUSED / 超过最大迭代数。
   *
   * @param runtimeId runtime ID
   */
  @Override
  public RunnerOutput run(Long runtimeId) {
    AgentContext context = agentContextManager.load(runtimeId);
    int maxIterations = agentProperties.getReact().getMaxIterations();
    long startTime = System.currentTimeMillis();
    log.info(
        "[CotAgentRunner][run][runtimeId({}) nextStepIndex({}) maxIterations({})]",
        runtimeId,
        context.getNextStepIndex(),
        maxIterations);

    for (int i = context.getNextStepIndex(); i < maxIterations; i++) {
      RunnerOutput output = step(runtimeId, context, i, startTime);
      if (output.getResult() != StepResult.CONTINUE) {
        return output;
      }
    }

    // 超过最大迭代数
    log.warn("[CotAgentRunner][run][runtimeId({}) 超过最大迭代数 {}]", runtimeId, maxIterations);
    traceRecorder.recordError(
        runtimeId, maxIterations, "max iterations reached: " + maxIterations, null);
    agentEventBus.publishError(
        runtimeId, "MAX_ITERATIONS", "max iterations reached: " + maxIterations);
    agentRuntimeService.fail(runtimeId, "max iterations reached", null);
    return RunnerOutput.failed(runtimeId, "max iterations reached", maxIterations);
  }

  /**
   * 单步执行：一个 Thought→Action→Observation 循环。
   *
   * @param runtimeId runtime ID
   */
  @Override
  public RunnerOutput step(Long runtimeId) {
    AgentContext context = agentContextManager.load(runtimeId);
    long startTime = System.currentTimeMillis();
    return step(runtimeId, context, context.getNextStepIndex(), startTime);
  }

  /** 暂停：写入轻量 PauseState（NORMAL）+ 状态机 running → paused。 */
  @Override
  public void pause(Long runtimeId) {
    agentRuntimeService.pause(runtimeId);
  }

  /** 恢复：状态机 paused → running，重新派发任务从 nextStepIndex 继续。 */
  @Override
  public void resume(Long runtimeId) {
    agentRuntimeService.resume(runtimeId);
  }

  /** 单步执行核心逻辑。 */
  private RunnerOutput step(Long runtimeId, AgentContext context, int i, long startTime) {
    agentRuntimeService.heartbeat(runtimeId);

    // 管线 REQUEST：暂停检测 → 上下文注入 → 技能目录 → 提示词组装
    PipelineContext ctx = new PipelineContext();
    ctx.setRuntimeId(runtimeId);
    ctx.setStrategy(AgentStrategy.COT);
    ctx.setAgentContext(context);
    ctx.setUserQuery(context.getUserQuery());
    ctx.setStepIndex(i);
    agentPipeline.run(Phase.REQUEST, ctx);
    if (ctx.isPaused()) {
      log.info("[CotAgentRunner][step][runtimeId({}) step={} 暂停挂起]", runtimeId, i);
      return RunnerOutput.paused(runtimeId, i, ctx.getPauseState());
    }

    // 1. Thought：LLM 推理
    long startTs = System.currentTimeMillis();
    String llmOutput;
    try {
      llmOutput = callLlm(ctx.getSystemPrompt());
    } catch (Exception e) {
      log.error("[CotAgentRunner][step][runtimeId({}) LLM 推理失败 step={}]", runtimeId, i, e);
      traceRecorder.recordError(runtimeId, i, "LLM inference failed: " + e.getMessage(), null);
      agentEventBus.publishError(
          runtimeId, "LLM_INFERENCE_FAILED", "LLM inference failed: " + e.getMessage());
      agentRuntimeService.fail(runtimeId, "LLM inference failed: " + e.getMessage(), null);
      return RunnerOutput.failed(runtimeId, "LLM inference failed: " + e.getMessage(), i);
    }
    long thoughtLatency = System.currentTimeMillis() - startTs;

    // 管线 RESPONSE：OutputLayer 解析模型输出
    ctx.setModelOutput(llmOutput);
    agentPipeline.run(Phase.RESPONSE, ctx);
    if (ctx.getError() != null) {
      log.warn("[CotAgentRunner][step][runtimeId({}) 模型输出无效: {}]", runtimeId, llmOutput);
      traceRecorder.recordError(runtimeId, i, ctx.getError(), null);
      agentEventBus.publishError(runtimeId, "INVALID_LLM_OUTPUT", ctx.getError());
      agentRuntimeService.fail(runtimeId, ctx.getError(), null);
      return RunnerOutput.failed(runtimeId, ctx.getError(), i);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> parsed = ctx.getParsedOutput();
    String thought = (String) parsed.get("thought");
    traceRecorder.recordThought(
        runtimeId, i, ctx.getSystemPrompt(), llmOutput, 0, 0, thoughtLatency, null);
    agentEventBus.publishThought(runtimeId, i, thought == null ? "" : thought);

    // final_answer：终止
    if (parsed.containsKey("final_answer")) {
      String finalAnswer = String.valueOf(parsed.get("final_answer"));
      log.info(
          "[CotAgentRunner][step][runtimeId({}) step={} 生成 final_answer: {}]",
          runtimeId,
          i,
          finalAnswer.substring(0, Math.min(100, finalAnswer.length())));
      context.setNextStepIndex(i + 1);
      agentContextManager.snapshot(runtimeId, context);
      agentEventBus.publishDone(runtimeId, System.currentTimeMillis() - startTime);
      agentRuntimeService.done(runtimeId, finalAnswer);
      return RunnerOutput.done(runtimeId, finalAnswer, i);
    }

    // ask_human：人工介入（生成 PauseState 挂起）
    if (parsed.containsKey("ask_human")) {
      return handleAskHuman(runtimeId, context, parsed, i, startTime);
    }

    // 2. Action：调用 Skill
    @SuppressWarnings("unchecked")
    Map<String, Object> action = (Map<String, Object>) parsed.get("action");
    if (action == null) {
      log.warn("[CotAgentRunner][step][runtimeId({}) step={} 无 action 字段，视为最终答案]", runtimeId, i);
      agentEventBus.publishDone(runtimeId, System.currentTimeMillis() - startTime);
      agentRuntimeService.done(runtimeId, thought == null ? "" : thought);
      return RunnerOutput.done(runtimeId, thought == null ? "" : thought, i);
    }
    String skillName = (String) action.get("skill");
    @SuppressWarnings("unchecked")
    Map<String, Object> skillInput =
        (Map<String, Object>) action.getOrDefault("input", new HashMap<>());

    // 3. Skill 调用（回调管线自动落 trace）
    Map<String, Object> skillOutput;
    try {
      toolLayer.validateSkillInput(skillName, "latest", skillInput);
      agentEventBus.publishAction(runtimeId, skillName, skillInput);
      skillOutput = skillRegistry.invoke(runtimeId, i, skillName, "latest", skillInput);
    } catch (BusinessException e) {
      log.error(
          "[CotAgentRunner][step][runtimeId({}) step={} skill({}) 调用失败]",
          runtimeId,
          i,
          skillName,
          e);
      agentEventBus.publishError(
          runtimeId, "SKILL_INVOKE_FAILED", "skill invoke failed: " + e.getMessage());
      agentRuntimeService.fail(runtimeId, "skill invoke failed: " + e.getMessage(), null);
      return RunnerOutput.failed(runtimeId, "skill invoke failed: " + e.getMessage(), i);
    }

    // 4. Observation：存入 context + 快照落盘
    context.getObservations().put("step_" + i, skillOutput);
    agentEventBus.publishObservation(runtimeId, i, skillOutput);
    context.setNextStepIndex(i + 1);
    agentContextManager.snapshot(runtimeId, context);
    log.info(
        "[CotAgentRunner][step][runtimeId({}) step={} action({}) 完成, observation keys={}]",
        runtimeId,
        i,
        skillName,
        skillOutput.keySet());

    // 超时护栏：Skill 返回 {"error":"skill timeout"} → 停止继续调用工具，基于已有 observation 收尾
    if (SkillInvoker.TIMEOUT_ERROR.equals(skillOutput.get(SkillInvoker.ERROR_KEY))) {
      log.warn(
          "[CotAgentRunner][step][runtimeId({}) step={} skill({}) 超时，停止调用工具收尾]",
          runtimeId,
          i,
          skillName);
      traceRecorder.recordError(runtimeId, i, "skill timeout: " + skillName, null);
      agentEventBus.publishError(
          runtimeId,
          "SKILL_TIMEOUT",
          "skill(" + skillName + ") timeout, finalize with partial observations");
      finalizeWithPartialObservations(runtimeId, context, i, startTime);
      return RunnerOutput.done(runtimeId, "partial finalize", i);
    }

    return RunnerOutput.continuing(runtimeId, i);
  }

  /** 处理模型 ask_human 输出：快照 → 创建 PauseState(ASK_HUMAN) → 状态机 paused。 */
  @SuppressWarnings("unchecked")
  private RunnerOutput handleAskHuman(
      Long runtimeId, AgentContext context, Map<String, Object> parsed, int i, long startTime) {
    Map<String, Object> ask = (Map<String, Object>) parsed.get("ask_human");
    String question = String.valueOf(ask.getOrDefault("question", "需要人工确认"));
    Map<String, Object> variables =
        ask.get("variables") instanceof Map<?, ?> m ? (Map<String, Object>) m : new HashMap<>();
    log.info("[CotAgentRunner][handleAskHuman][runtimeId({}) step={} 需人工介入]", runtimeId, i);

    context.setNextStepIndex(i + 1);
    agentContextManager.snapshot(runtimeId, context);
    PauseState pause = agentRuntimeService.askHuman(runtimeId, question, variables, i + 1);
    return RunnerOutput.paused(runtimeId, i, pause);
  }

  /**
   * 超时收尾：用已有 observation 让 LLM 生成最终答案并结束任务。
   *
   * @param runtimeId runtime ID
   * @param context Agent 上下文（含已收集 observation）
   * @param stepIndex 当前 step
   * @param startTime 任务启动时间（用于耗时上报）
   */
  private void finalizeWithPartialObservations(
      Long runtimeId, AgentContext context, int stepIndex, long startTime) {
    String prompt =
        promptLayer.buildTimeoutFinalPrompt(
            context.getUserQuery() == null ? "" : context.getUserQuery(),
            formatObservations(context));
    try {
      String llmOutput = callLlm(prompt);
      Map<String, Object> parsed = objectMapper.readValue(llmOutput, Map.class);
      String finalAnswer =
          parsed != null && parsed.get("final_answer") != null
              ? String.valueOf(parsed.get("final_answer"))
              : "部分工具调用超时，基于已获取的信息无法给出完整回答，请稍后重试。";
      log.info(
          "[CotAgentRunner][finalizeWithPartialObservations][runtimeId({}) 基于 {} 条 observation 收尾]",
          runtimeId,
          context.getObservations().size());
      context.setNextStepIndex(stepIndex + 1);
      agentContextManager.snapshot(runtimeId, context);
      agentEventBus.publishDone(runtimeId, System.currentTimeMillis() - startTime);
      agentRuntimeService.done(runtimeId, finalAnswer);
    } catch (Exception e) {
      log.error(
          "[CotAgentRunner][finalizeWithPartialObservations][runtimeId({}) 收尾 LLM 调用失败，直接返回提示]",
          runtimeId,
          e);
      agentRuntimeService.done(runtimeId, "部分工具调用超时，基于已有信息仍无法给出完整回答，请稍后重试。");
    }
  }

  /** 调用 ChatClient 获取 LLM 输出。 */
  private String callLlm(String prompt) {
    return resolveChatClient().prompt().user(prompt).call().content();
  }

  /** 从注册表解析默认 ChatClient（优先 ollama，回退到第一个可用）。 */
  private ChatClient resolveChatClient() {
    ChatClient client = chatClientRegistry.get("ollamaChatClient");
    if (client != null) {
      return client;
    }
    return chatClientRegistry.values().stream().findFirst().orElse(null);
  }

  private String formatObservations(AgentContext context) {
    if (context.getObservations().isEmpty()) return "(无)";
    StringBuilder sb = new StringBuilder();
    context
        .getObservations()
        .forEach(
            (k, v) -> {
              sb.append(k).append(": ").append(v).append("\n");
            });
    return sb.toString();
  }
}
