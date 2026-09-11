package com.ai.api.document.dto;

import java.util.List;
import lombok.Data;

/**
 * 批量重处理文档响应 DTO（add-p3-rag-feedback-ops）。
 *
 * <p>异步队列处理：接口立即返回 batchId，前端轮询进度接口。
 *
 * @author add-p3-rag-feedback-ops
 */
@Data
public class BatchReprocessRespDTO {

  /** 批次 ID（UUID） */
  private String batchId;

  /** 已入队的文档数量 */
  private int queuedCount;

  /** 被去重跳过的文档列表（同一 docId 已在队列中） */
  private List<Long> skippedDocIds;

  /** 进度查询接口 URL */
  private String progressUrl;
}
