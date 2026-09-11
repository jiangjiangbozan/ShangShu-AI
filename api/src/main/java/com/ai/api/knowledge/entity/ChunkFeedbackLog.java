package com.ai.api.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 分片反馈日志实体（add-p3-rag-feedback-ops）。
 *
 * <p>记录用户对知识库分片的点踩反馈，唯一索引 {@code uk_user_chunk_type} 保证同一用户对同一分片的同类反馈幂等。
 *
 * @author add-p3-rag-feedback-ops
 */
@Data
@TableName("chunk_feedback_log")
public class ChunkFeedbackLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long chunkId;
  private Long userId;
  private String feedbackType;
  private LocalDateTime createTime;
}
