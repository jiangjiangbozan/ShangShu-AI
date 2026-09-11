package com.ai.api.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Agent Runtime 运行时状态实体（add-agent-runtime）。
 *
 * <p>状态字段 status 取值：pending|running|paused|done|failed|cancelled，状态转移由 {@code
 * AgentRuntimeService.transition()} 校验。
 *
 * @author add-agent-runtime
 */
@Data
@TableName(value = "agent_runtime", autoResultMap = true)
public class AgentRuntime {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long sessionId;
  private Long agentConfigId;
  private Long parentRuntimeId;
  private String status;
  private Integer nextStepIndex;
  private LocalDateTime lastHeartbeat;
  private Integer ttlSeconds;
  private String errorMessage;
  private String finalAnswer;
  private String modelId;
  private Long userId;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  /** 执行策略：COT（ReAct 文本解析）| FC（原生 Function Calling），由 StrategyFactory 选择 Runner。 */
  private String strategy;

  /** AgentContext 序列化快照（chat history + observations + thought chain）。 */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String contextSnapshot;
}
