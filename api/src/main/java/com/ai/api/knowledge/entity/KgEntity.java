package com.ai.api.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 知识图谱实体表（add-3rd-4th-retrieval） */
@Data
@TableName("kg_entity")
public class KgEntity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long knowledgeBaseId;
  private String name;
  private String type;
  private String description;
  private Long sourceDocumentId;
  private String creator;
  private LocalDateTime createTime;

  @TableLogic private Integer deleted;
}
