package com.ai.api.knowledge.mapper;

import com.ai.api.knowledge.entity.ChunkFeedbackLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分片反馈日志 Mapper（add-p3-rag-feedback-ops）。
 *
 * @author add-p3-rag-feedback-ops
 */
@Mapper
public interface ChunkFeedbackLogMapper extends BaseMapper<ChunkFeedbackLog> {}
