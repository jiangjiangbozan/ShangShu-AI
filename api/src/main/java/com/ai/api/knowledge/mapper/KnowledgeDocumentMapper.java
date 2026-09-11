package com.ai.api.knowledge.mapper;

import com.ai.api.knowledge.entity.KnowledgeDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 知识库文档 Mapper */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {}
