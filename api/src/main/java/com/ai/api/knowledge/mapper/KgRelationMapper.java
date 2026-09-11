package com.ai.api.knowledge.mapper;

import com.ai.api.knowledge.entity.KgRelation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 知识图谱关系 Mapper（add-3rd-4th-retrieval） */
@Mapper
public interface KgRelationMapper extends BaseMapper<KgRelation> {}
