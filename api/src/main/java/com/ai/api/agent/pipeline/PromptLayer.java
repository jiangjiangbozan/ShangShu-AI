package com.ai.api.agent.pipeline;

import com.ai.api.agent.pipeline.Layer.Phase;
import com.ai.api.agent.runner.AgentStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 提示词 Layer（add-agent-runner-pipeline）。
 *
 * <p>职责：系统提示词、角色设定、Agent 指令组装。
 *
 * <ul>
 *   <li>COT 策略：ReAct 模板（角色 + 技能目录 + 用户问题 + 历史观察 + JSON 输出要求）
 *   <li>FC 策略：Function Calling 角色指令（工具由模型原生感知，无需目录文本）
 * </ul>
 */
@Slf4j
@Component
public class PromptLayer implements Layer {

  private static final String COT_SYSTEM_PROMPT_TEMPLATE =
      """
      你是 ReAct 推理 Agent。请根据用户问题与历史观察，输出下一步动作。

      可用 Skills:
      %s

      用户问题: %s

      历史观察:
      %s

      输出要求（严格 JSON，不要额外文字）:
      1. 继续推理: {"thought":"<推理过程>","action":{"skill":"<skill_name>","input":{...}}}
      2. 给出最终答案: {"thought":"<推理过程>","final_answer":"<最终答案>"}
      3. 需要人工确认: {"thought":"<推理过程>","ask_human":{"question":"<向人类确认的问题>","variables":{...}}}
      """;

  private static final String FC_SYSTEM_PROMPT =
      """
      你是 Function Calling Agent。请基于用户问题调用可用工具获取信息，最终给出简洁准确的回答。

      规则:
      1. 工具已原生注册，直接按需调用
      2. 所有信息获取完成后，给出最终答案
      3. 信息不足时如实说明
      """;

  /** 超时收尾 prompt：不再允许调用工具，基于已收集的观察直接给出最终答案。 */
  private static final String TIMEOUT_FINAL_PROMPT_TEMPLATE =
      """
      你是 ReAct 推理 Agent。由于某个 Skill 调用超时，你已无法继续调用任何工具。

      用户问题: %s

      已收集到的观察（可能是部分信息）:
      %s

      请基于以上已有信息，尽最大努力给出最完整的最终答案。
      如果信息不足，请明确告知用户当前无法获取的信息，并给出已有信息下的最佳答复。

      输出要求（严格 JSON，不要额外文字）:
      {"thought":"<基于已有信息的推理>","final_answer":"<最终答案>"}
      """;

  @Override
  public Phase phase() {
    return Phase.REQUEST;
  }

  @Override
  public int order() {
    return 30;
  }

  @Override
  public void process(PipelineContext ctx) {
    if (ctx.getStrategy() == AgentStrategy.FC) {
      ctx.setSystemPrompt(FC_SYSTEM_PROMPT);
    } else {
      ctx.setSystemPrompt(
          String.format(
              COT_SYSTEM_PROMPT_TEMPLATE,
              ctx.getSkillCatalog() == null ? "(无)" : ctx.getSkillCatalog(),
              ctx.getUserQuery() == null ? "" : ctx.getUserQuery(),
              ctx.getContextBlock() == null ? "(无)" : ctx.getContextBlock()));
    }
  }

  /** 构造超时收尾 prompt（基于已有 observation 生成最终答案）。 */
  public String buildTimeoutFinalPrompt(String userQuery, String contextBlock) {
    return String.format(
        TIMEOUT_FINAL_PROMPT_TEMPLATE,
        userQuery == null ? "" : userQuery,
        contextBlock == null ? "(无)" : contextBlock);
  }
}
