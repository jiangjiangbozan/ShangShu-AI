package com.ai.api.knowledge.mapper;

import com.ai.api.knowledge.entity.KnowledgeChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
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
   * 命中计数批量 +1（按 vector_id 匹配，供检索链路回写命中统计）。
   *
   * <p>检索结果 {@code Document.id} 即入库时写入 ES 的 {@code vector_id}（UUID）， 直接按 {@code vector_id}
   * 累加，规避"UUID 无法解析为数值主键"的转换问题，一次 SQL 完成。
   */
  @Update(
      "<script>"
          + "UPDATE knowledge_chunk SET hit_count = hit_count + 1 WHERE deleted = 0 AND vector_id IN "
          + "<foreach collection='vectorIds' item='vid' open='(' separator=',' close=')'>#{vid}</foreach>"
          + "</script>")
  int incrementHitCountByVectorIds(@Param("vectorIds") Collection<String> vectorIds);

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
