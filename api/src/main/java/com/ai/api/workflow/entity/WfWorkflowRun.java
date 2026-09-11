package com.ai.api.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "wf_workflow_run", autoResultMap = true)
public class WfWorkflowRun {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String runId;
  private String workflowId;
  private Integer version;
  private String status;

  @TableField(typeHandler = JacksonTypeHandler.class)
  private String inputs;

  @TableField(typeHandler = JacksonTypeHandler.class)
  private String outputs;

  private Long totalTokens;
  private Long elapsedMs;
  private String triggeredBy;
  private LocalDateTime createdAt;
  private LocalDateTime finishedAt;
  private String errorMessage;
}
