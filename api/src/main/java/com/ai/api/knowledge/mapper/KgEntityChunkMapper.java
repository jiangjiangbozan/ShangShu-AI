package com.ai.api.knowledge.mapper;

import com.ai.api.knowledge.entity.KgEntityChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 知识图谱实体-分片关联 Mapper（add-3rd-4th-retrieval） */
@Mapper
public interface KgEntityChunkMapper extends BaseMapper<KgEntityChunk> {}
