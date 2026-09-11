package com.ai.api.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 知识图谱实体-分片关联表（add-3rd-4th-retrieval） */
@Data
@TableName("kg_entity_chunk")
public class KgEntityChunk {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long knowledgeBaseId;
  private Long entityId;
  private Long chunkId;
  private String creator;
  private LocalDateTime createTime;

  @TableLogic private Integer deleted;
}
