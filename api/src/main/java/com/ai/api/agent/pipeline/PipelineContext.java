package com.ai.api.agent.pipeline;

import com.ai.api.agent.pause.PauseState;
import com.ai.api.agent.runner.AgentStrategy;
import com.ai.api.agent.runtime.AgentContext;
import java.util.Map;
import lombok.Data;
import org.springframework.ai.tool.ToolCallback;

/**
 * 管线统一上下文对象（add-agent-runner-pipeline）。
 *
 * <p>Layer 之间仅传递本对象，承载 REQUEST 阶段（上下文注入 / 工具注册 / 提示词组装 / 暂停检测） 与 RESPONSE
 * 阶段（模型输出解析）的全部中间产物，屏蔽各层对底层实现的直接依赖。
 */
@Data
public class PipelineContext {

  // ============ 输入 ============
  private Long runtimeId;

  /** 用户原始 query。 */
  private String userQuery;

  /** 执行策略（决定各 Layer 的分支行为）。 */
  private AgentStrategy strategy;

  /** Agent 执行上下文（由 ContextLayer 从 AgentContextManager 注入快照）。 */
  private AgentContext agentContext;

  /** 当前 step 序号。 */
  private int stepIndex;

  // ============ REQUEST 阶段产物 ============
  /** 系统提示词（PromptLayer 组装，含角色 / 指令 / 技能目录 / 上下文块）。 */
  private String systemPrompt;

  /** 技能目录文本（COT 策略，ToolLayer 生成）。 */
  private String skillCatalog;

  /** Function Calling 工具注册表（FC 策略，ToolLayer 生成）。 */
  private ToolCallback[] toolCallbacks;

  /** 上下文块（ContextLayer 格式化：会话历史 + 历史观察 + 人工回复注入）。 */
  private String contextBlock;

  /** 暂停状态（AskHumanLayer 检测到活跃 PauseState 时注入）。 */
  private PauseState pauseState;

  // ============ 模型调用 ============
  /** 模型原始输出（Runner 调用 LLM 后写入）。 */
  private String modelOutput;

  // ============ RESPONSE 阶段产物 ============
  /** 模型输出解析结果（OutputLayer 写入）。 */
  private Map<String, Object> parsedOutput;

  // ============ 控制标记 ============
  /** 是否暂停挂起（AskHumanLayer 置位，Runner 据此停止循环）。 */
  private boolean paused;

  /** 最终答案（OutputLayer / Runner 写入）。 */
  private String finalAnswer;

  /** 错误信息（OutputLayer / Runner 写入，非空即终止）。 */
  private String error;
}
