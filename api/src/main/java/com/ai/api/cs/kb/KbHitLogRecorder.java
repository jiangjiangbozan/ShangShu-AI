package com.ai.api.cs.kb;

import com.ai.api.cs.entity.CsKbHitLog;
import com.ai.api.cs.mapper.CsKbHitLogMapper;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * KB 命中日志记录器（add-customer-service）。
 *
 * <p>异步写入 {@code cs_kb_hit_log} 表，避免阻塞主流程。
 *
 * @author add-customer-service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbHitLogRecorder {

  private final CsKbHitLogMapper csKbHitLogMapper;

  /**
   * 异步记录命中日志。
   *
   * @param runtimeId runtime ID
   * @param query 原始查询
   * @param kbId 知识库 ID
   * @param docId 命中文档 ID
   * @param score 相似度分数
   * @param hit 是否命中
   */
  @Async
  public void record(
      Long runtimeId, String query, Long kbId, String docId, BigDecimal score, boolean hit) {
    try {
      CsKbHitLog logEntry = new CsKbHitLog();
      logEntry.setRuntimeId(runtimeId);
      logEntry.setHitQuery(truncate(query, 512));
      logEntry.setKbId(kbId);
      logEntry.setDocId(docId);
      logEntry.setScore(score);
      logEntry.setHit(hit ? 1 : 0);
      csKbHitLogMapper.insert(logEntry);
    } catch (Exception e) {
      log.warn(
          "[KbHitLogRecorder][record failed][runtimeId({}) error({})]", runtimeId, e.getMessage());
    }
  }

  private String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() > max ? s.substring(0, max) : s;
  }
}
