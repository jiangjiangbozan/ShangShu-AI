package com.ai.api.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Agent 全链路追踪实体（add-agent-runtime）。
 *
 * <p>step_type 取值：thought|action|observation|resume|error。UNIQUE(runtime_id, step_index)
 * 防止断点续跑导致重复写入。
 *
 * @author add-agent-runtime
 */
@Data
@TableName(value = "agent_trace", autoResultMap = true)
public class AgentTrace {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String traceId;
  private Long runtimeId;
  private Integer stepIndex;
  private String stepType;
  private String skillId;
  private Integer tokenIn;
  private Integer tokenOut;
  private Integer latencyMs;
  private Long parentStepId;
  private String error;
  private String dlqMessageId;
  private LocalDateTime createTime;

  /** action 的输入 / thought 的 prompt。 */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String input;

  /** action 的返回 / thought 的 LLM 响应。 */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String output;
}
