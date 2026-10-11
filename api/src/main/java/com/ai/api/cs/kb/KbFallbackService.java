package com.ai.api.cs.kb;

import com.ai.api.cs.config.CustomerServiceProperties;
import com.ai.api.knowledge.service.RagRetrievalService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * KB 命中回退服务（add-customer-service）。
 *
 * <p>意图识别成功后、业务 Skill 执行前调用，若 top1 score > {@code cs.kb-hit-threshold} 则跳过 LLM 直接返回。
 *
 * @author add-customer-service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbFallbackService {

  private final RagRetrievalService ragRetrievalService;
  private final KbHitLogRecorder kbHitLogRecorder;
  private final CustomerServiceProperties properties;

  /**
   * 尝试 KB 命中回退。
   *
   * @param runtimeId runtime ID
   * @param query 用户查询
   * @param kbId 知识库 ID（可空）
   * @return 命中返回文本片段；未命中返回 null
   */
  public String tryFallback(Long runtimeId, String query, Long kbId) {
    if (query == null || query.isBlank()) {
      return null;
    }
    List<Document> docs;
    try {
      docs = ragRetrievalService.retrieveWithRrf(kbId, query, 1);
    } catch (Exception e) {
      log.warn(
          "[KbFallback][retrieve failed][runtimeId({}) query({}) error({})]",
          runtimeId,
          query,
          e.getMessage());
      return null;
    }
    if (docs == null || docs.isEmpty()) {
      kbHitLogRecorder.record(runtimeId, query, kbId, null, BigDecimal.ZERO, false);
      return null;
    }
    Document top1 = docs.get(0);
    // RRF 融合后无显式 score 字段，用 1.0 / (rank + 1) 作为近似
    BigDecimal score = BigDecimal.ONE;
    boolean hit = score.doubleValue() >= properties.getKbHitThreshold();
    kbHitLogRecorder.record(runtimeId, query, kbId, top1.getId(), score, hit);
    if (hit) {
      log.info(
          "[KbFallback][hit][runtimeId({}) query({}) docId({}) score({})]",
          runtimeId,
          query,
          top1.getId(),
          score);
      return top1.getText();
    }
    log.debug(
        "[KbFallback][no hit][runtimeId({}) score({}) < threshold({})]",
        runtimeId,
        score,
        properties.getKbHitThreshold());
    return null;
  }

  /** 暴露给上层查询当前 KB 命中阈值。 */
  public double getThreshold() {
    return properties.getKbHitThreshold();
  }

  /** 预留：按意图映射到不同 KB（M5 简化为单 KB）。 */
  public Long resolveKbId(String intent) {
    // M5 简化实现：所有意图走默认 KB（kbId=null，检索全部）
    return null;
  }
}
