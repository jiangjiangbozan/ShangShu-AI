package com.ai.api.knowledge.mapper;

import com.ai.api.knowledge.entity.KnowledgeChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 知识库分片 Mapper
 *
 * @author add-rag-module
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

  /**
   * 命中计数 +1（add-p3-rag-feedback-ops）。
   *
   * <p>使用 UPDATE...WHERE 直接累加，避免读-改-写的并发冲突。
   */
  @Update("UPDATE knowledge_chunk SET hit_count = hit_count + 1 WHERE id = #{id} AND deleted = 0")
  int incrementHitCount(@Param("id") Long id);

  /**
   * 命中计数 -3（点踩反馈，add-p3-rag-feedback-ops）。
   *
   * <p>下限保护：不会小于 0。
   */
  @Update(
      "UPDATE knowledge_chunk SET hit_count = GREATEST(hit_count - 3, 0) "
          + "WHERE id = #{id} AND deleted = 0")
  int decrementHitCountOnDislike(@Param("id") Long id);

  /**
   * 查询低命中分片（hit_count <= threshold），用于定期重处理。
   *
   * @param kbId 知识库 ID，为 null 时跨所有库扫描
   * @param threshold 命中阈值
   * @param limit 最多返回条数
   */
  // 简单查询用 BaseMapper.selectList + QueryWrapper 实现，无需 SQL 注解
  default List<KnowledgeChunk> selectLowHitChunks(Long kbId, int threshold, int limit) {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeChunk> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.le(KnowledgeChunk::getHitCount, threshold);
    if (kbId != null) {
      wrapper.eq(KnowledgeChunk::getKnowledgeBaseId, kbId);
    }
    wrapper.last("LIMIT " + Math.max(1, limit));
    return selectList(wrapper);
  }
}
