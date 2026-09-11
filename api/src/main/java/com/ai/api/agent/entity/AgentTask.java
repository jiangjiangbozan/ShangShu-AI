package com.ai.api.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Agent 任务步骤实例（add-agent-runtime）。
 *
 * <p>每个 runtime 关联 N 个 task，对应 LLM 规划的子步骤。
 *
 * @author add-agent-runtime
 */
@Data
@TableName(value = "agent_task", autoResultMap = true)
public class AgentTask {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long runtimeId;
  private Integer stepIndex;
  private String description;
  private String skillName;
  private String skillVersion;
  private String status;
  private Long parentStepId;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  @TableField(typeHandler = JacksonTypeHandler.class)
  private String input;

  @TableField(typeHandler = JacksonTypeHandler.class)
  private String output;
}
