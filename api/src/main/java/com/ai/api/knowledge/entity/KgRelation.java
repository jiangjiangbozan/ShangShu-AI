package com.ai.api.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 知识图谱实体关系表（add-3rd-4th-retrieval） */
@Data
@TableName("kg_relation")
public class KgRelation {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long knowledgeBaseId;
  private Long sourceEntityId;
  private Long targetEntityId;
  private String relationType;
  private Long sourceDocumentId;
  private String creator;
  private LocalDateTime createTime;

  @TableLogic private Integer deleted;
}
