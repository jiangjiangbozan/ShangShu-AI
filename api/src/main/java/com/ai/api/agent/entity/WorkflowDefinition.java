package com.ai.api.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 工作流定义实体（add-agent-runtime）。
 *
 * <p>UNIQUE(workflow_id, version) 保证业务 ID 与版本唯一。dag 字段为 JSON，描述节点+边+条件分支。
 *
 * @author add-agent-runtime
 */
@Data
@TableName(value = "workflow_definition", autoResultMap = true)
public class WorkflowDefinition {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String workflowId;
  private String name;
  private String version;
  private String triggerType;
  private String owner;
  private Integer enabled;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  /** DAG JSON（nodes + edges）。 */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String dag;
}
