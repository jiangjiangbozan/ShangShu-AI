package com.ai.api.knowledge.schedule;

import com.ai.api.knowledge.entity.KnowledgeChunk;
import com.ai.api.knowledge.mapper.KnowledgeChunkMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 低命中/低质分片扫描器（add-p3-rag-feedback-ops）。
 *
 * <p>每日凌晨 03:30 扫描 hit_count 较低的 chunk，触发重新解析与向量化（由后续脚本或人工介入）。
 *
 * <p>设计为「输出待优化清单」而非自动重处理，避免误删已上线数据。具体重处理由 KnowledgeBaseService 在低峰期人工触发。
 *
 * @author add-p3-rag-feedback-ops
 */
@Slf4j
@Component
public class LowHitChunkScanner {

  private final KnowledgeChunkMapper chunkMapper;

  @Value("${rag.feedback.low-hit-threshold:0}")
  private int lowHitThreshold;

  @Value("${rag.feedback.low-hit-scan-limit:200}")
  private int scanLimit;

  public LowHitChunkScanner(KnowledgeChunkMapper chunkMapper) {
    this.chunkMapper = chunkMapper;
  }

  /** 每日 03:30 扫描低命中分片，输出报告日志。 */
  @Scheduled(cron = "${rag.feedback.low-hit-cron:0 30 3 * * ?}")
  public void scanLowHitChunks() {
    List<KnowledgeChunk> lowHitChunks =
        chunkMapper.selectLowHitChunks(null, lowHitThreshold, scanLimit);
    if (lowHitChunks.isEmpty()) {
      log.info("[LowHitChunkScanner][无低命中分片: threshold={}]", lowHitThreshold);
      return;
    }
    log.info(
        "[LowHitChunkScanner][发现 {} 条低命中分片（threshold={}），建议人工触发重处理]",
        lowHitChunks.size(),
        lowHitThreshold);
    for (KnowledgeChunk chunk : lowHitChunks) {
      log.info(
          "[LowHitChunkScanner][低命中分片: id={}, kbId={}, docId={}, hitCount={}, contentHead={}]",
          chunk.getId(),
          chunk.getKnowledgeBaseId(),
          chunk.getDocumentId(),
          chunk.getHitCount(),
          head(chunk.getContent()));
    }
  }

  private String head(String text) {
    if (text == null) {
      return "";
    }
    return text.length() > 60 ? text.substring(0, 60) + "..." : text;
  }
}
