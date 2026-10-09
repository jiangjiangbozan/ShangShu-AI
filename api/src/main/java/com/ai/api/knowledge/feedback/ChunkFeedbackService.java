package com.ai.api.knowledge.feedback;

import com.ai.api.knowledge.entity.ChunkFeedbackLog;
import com.ai.api.knowledge.mapper.ChunkFeedbackLogMapper;
import com.ai.api.knowledge.mapper.KnowledgeChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * RAG 检索反馈闭环 Service（add-p3-rag-feedback-ops）。
 *
 * <p>检索命中异步 +1，用户点踩 -3（下限 0）；点踩通过 {@code chunk_feedback_log} 唯一索引保证幂等， 同一用户对同一分片重复点踩不会重复扣分。
 *
 * @author add-p3-rag-feedback-ops
 */
@Slf4j
@Service
public class ChunkFeedbackService {

  private static final String FEEDBACK_NEGATIVE = "negative";

  private final KnowledgeChunkMapper chunkMapper;
  private final ChunkFeedbackLogMapper feedbackLogMapper;

  public ChunkFeedbackService(
      KnowledgeChunkMapper chunkMapper, ChunkFeedbackLogMapper feedbackLogMapper) {
    this.chunkMapper = chunkMapper;
    this.feedbackLogMapper = feedbackLogMapper;
  }

  /**
   * 命中计数 +1（异步，主流程不阻塞）。
   *
   * @param chunkId 分片 ID
   */
  @Async
  public void incrHit(Long chunkId) {
    try {
      chunkMapper.incrementHitCount(chunkId);
    } catch (Exception e) {
      log.warn(
          "[ChunkFeedbackService][命中计数失败（不影响主流程）: chunkId={}, error={}]", chunkId, e.getMessage());
    }
  }

  /**
   * 命中计数批量 +1（异步，主流程不阻塞）。
   *
   * <p>入参为检索结果的 {@code vectorId}（ES 文档 id），按 {@code vector_id} 批量累加， 一次 SQL 完成，且规避"UUID
   * 无法解析为数值主键"的问题。
   *
   * <p>由 {@code RagRetrievalService} 跨 Bean 调用，{@code @Async} 才会真正生效。
   *
   * @param vectorIds ES 文档 id（入库时生成的 vectorId）
   */
  @Async
  public void incrHitsByVectorIds(List<String> vectorIds) {
    if (vectorIds == null || vectorIds.isEmpty()) {
      return;
    }
    try {
      int updated = chunkMapper.incrementHitCountByVectorIds(vectorIds);
      log.debug(
          "[ChunkFeedbackService][批量命中计数 +1: candidates={}, updated={}]",
          vectorIds.size(),
          updated);
    } catch (Exception e) {
      log.warn(
          "[ChunkFeedbackService][批量命中计数失败（不影响主流程）: size={}, error={}]",
          vectorIds.size(),
          e.getMessage());
    }
  }

  /**
   * 点踩反馈（幂等）：命中计数 -3（下限 0），并写反馈日志。
   *
   * <p>同一用户对同一分片重复点踩时，唯一索引冲突仅跳过扣分，不抛异常。
   *
   * @param chunkId 分片 ID
   * @param userId 反馈用户 ID
   * @return true 表示本次为首次点踩且已扣分；false 表示重复点踩（已幂等跳过）
   */
  public boolean dislike(Long chunkId, Long userId) {
    ChunkFeedbackLog feedbackLog = new ChunkFeedbackLog();
    feedbackLog.setChunkId(chunkId);
    feedbackLog.setUserId(userId);
    feedbackLog.setFeedbackType(FEEDBACK_NEGATIVE);
    try {
      feedbackLogMapper.insert(feedbackLog);
    } catch (DuplicateKeyException e) {
      log.info("[ChunkFeedbackService][重复点踩，幂等跳过: chunkId={}, userId={}]", chunkId, userId);
      return false;
    }
    chunkMapper.decrementHitCountOnDislike(chunkId);
    log.info("[ChunkFeedbackService][点踩生效: chunkId={}, userId={}, hit_count -3]", chunkId, userId);
    return true;
  }

  /** 查询某分片是否已被某用户点踩过。 */
  public boolean isDisliked(Long chunkId, Long userId) {
    LambdaQueryWrapper<ChunkFeedbackLog> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .eq(ChunkFeedbackLog::getChunkId, chunkId)
        .eq(ChunkFeedbackLog::getUserId, userId)
        .eq(ChunkFeedbackLog::getFeedbackType, FEEDBACK_NEGATIVE);
    return feedbackLogMapper.selectCount(wrapper) > 0;
  }
}
