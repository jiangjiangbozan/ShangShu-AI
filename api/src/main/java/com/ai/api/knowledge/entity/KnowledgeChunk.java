package com.ai.api.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 知识库分片表（向量数据存放在 ES VectorStore，本表只存元信息与 ES doc id 关联） */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long knowledgeBaseId;
  private Long documentId;
  private Integer chunkIndex;
  private String content;
  private Integer tokenCount;
  private String vectorId;

  /** 命中计数（add-p3-rag-feedback-ops）：每次检索命中异步 +1 */
  private Integer hitCount;

  private String creator;
  private LocalDateTime createTime;

  @TableLogic private Integer deleted;
}
