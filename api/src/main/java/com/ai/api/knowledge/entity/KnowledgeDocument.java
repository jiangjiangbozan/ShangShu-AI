package com.ai.api.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 知识库文档表 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long knowledgeBaseId;
  private String fileName;
  private String filePath;
  private String fileType;
  private Long fileSize;
  private String contentHash;
  private Integer chunkCount;
  private String status;
  private String errorMessage;

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
