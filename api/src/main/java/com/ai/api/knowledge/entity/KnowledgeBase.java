package com.ai.api.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 知识库表 */
@Data
@TableName("knowledge_base")
public class KnowledgeBase {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;
  private String description;
  private Long ownerId;
  private String embeddingModel;
  private Integer chunkSize;
  private Integer chunkOverlap;

  /** 相似度阈值，QuestionAnswerAdvisor 检索时只保留相似度 ≥ 该值的分片。 默认 0.5；中文短查询场景建议 0.3-0.4，技术文档密集场景建议 0.5-0.6。 */
  private Double similarityThreshold;

  private Integer documentCount;
  private Integer chunkCount;
  private String status;

  @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
  private String creator;

  @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
  private String updater;

  @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;

  @TableLogic private Integer deleted;
}
