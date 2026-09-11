package com.ai.api.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Skill 技能元数据实体（add-agent-runtime）。
 *
 * <p>UNIQUE(name, version) 保证版本唯一。type 取值：api|local|kb|subagent|mcp。
 *
 * @author add-agent-runtime
 */
@Data
@TableName(value = "skill_definition", autoResultMap = true)
public class SkillDefinition {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;
  private String version;
  private String type;
  private String description;
  private String endpoint;
  private String beanName;
  private String methodName;
  private String permissionTag;
  private Integer enabled;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  /** 输入参数 JSON Schema。 */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String inputSchema;

  /** 输出参数 JSON Schema。 */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String outputSchema;
}
