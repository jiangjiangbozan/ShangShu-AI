package com.ai.api.document.dto;

import java.util.List;
import lombok.Data;

/**
 * 批量删除文档响应 DTO（add-p3-rag-feedback-ops）。
 *
 * <p>容错设计：部分失败不影响已成功项的提交，响应同时返回成功数与失败详情。
 *
 * @author add-p3-rag-feedback-ops
 */
@Data
public class BatchDeleteRespDTO {

  /** 成功删除的文档数量 */
  private int successCount;

  /** 失败项详情 */
  private List<Failure> failures;

  /** 单条失败详情 */
  @Data
  public static class Failure {
    /** 文档 ID */
    private Long docId;

    /** 失败原因 */
    private String reason;
  }
}
