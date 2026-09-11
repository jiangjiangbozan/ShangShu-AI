package com.ai.api.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 工作流定义实体。
 *
 * <p>面向前端画布编辑器，存储 DSL JSON（nodes + edges + viewport）。
 */
@Data
@TableName(value = "wf_workflow", autoResultMap = true)
public class WfWorkflow {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String workflowId;
  private String name;
  private String description;

  /** DSL JSON（与前端 types.ts WorkflowDSL 对齐）。 */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String graph;

  private Integer version;
  private Integer published;
  private Long ownerId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @TableLogic private Integer deleted;
}
