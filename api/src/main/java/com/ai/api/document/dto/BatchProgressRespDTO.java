package com.ai.api.document.dto;

import java.util.List;
import lombok.Data;

/**
 * 批量重处理进度响应 DTO（add-p3-rag-feedback-ops）。
 *
 * <p>Redis Hash {@code batch:progress:{batchId}} 实时反映处理状态，TTL 1 天。
 *
 * @author add-p3-rag-feedback-ops
 */
@Data
public class BatchProgressRespDTO {

  /** 批次 ID */
  private String batchId;

  /** 总文档数 */
  private int total;

  /** 成功数 */
  private int success;

  /** 失败数 */
  private int failed;

  /** 处理中数 */
  private int processing;

  /** 待处理数 */
  private int pending;

  /** 是否已完成（success + failed = total） */
  private boolean done;

  /** 失败项详情（每条 {docId, reason}） */
  private List<BatchDeleteRespDTO.Failure> failures;
}
