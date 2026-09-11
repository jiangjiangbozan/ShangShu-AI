package com.ai.api.document.controller;

import com.ai.api.common.result.Result;
import com.ai.api.document.dto.BatchDeleteRespDTO;
import com.ai.api.document.dto.BatchProgressRespDTO;
import com.ai.api.document.dto.BatchReprocessRespDTO;
import com.ai.api.document.dto.RelatedChatRespDTO;
import com.ai.api.document.service.BatchDocumentService;
import com.ai.api.document.service.DocumentRelationService;
import java.util.List;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档管理增强 REST API（add-p3-rag-feedback-ops）。
 *
 * <p>提供文档关联问答记录、批量删除、批量重处理与进度查询接口。
 *
 * @author add-p3-rag-feedback-ops
 */
@RestController
@RequestMapping("/api/v1/documents")
public class BatchDocumentController {

  private final BatchDocumentService batchDocumentService;
  private final DocumentRelationService documentRelationService;

  public BatchDocumentController(
      BatchDocumentService batchDocumentService, DocumentRelationService documentRelationService) {
    this.batchDocumentService = batchDocumentService;
    this.documentRelationService = documentRelationService;
  }

  /** 批量操作请求体。 */
  @Data
  static class BatchReq {
    List<Long> docIds;
  }

  /**
   * 批量删除文档。
   *
   * <p>POST /api/v1/documents/batch/delete
   */
  @PostMapping("/batch/delete")
  @ResponseStatus(HttpStatus.OK)
  public Result<BatchDeleteRespDTO> batchDelete(@RequestBody BatchReq req) {
    if (req == null || req.getDocIds() == null || req.getDocIds().isEmpty()) {
      throw new IllegalArgumentException("docIds 不能为空");
    }
    return Result.success(batchDocumentService.batchDelete(req.getDocIds()));
  }

  /**
   * 批量重处理文档（异步队列，立即返回 batchId）。
   *
   * <p>POST /api/v1/documents/batch/reprocess
   */
  @PostMapping("/batch/reprocess")
  @ResponseStatus(HttpStatus.OK)
  public Result<BatchReprocessRespDTO> batchReprocess(@RequestBody BatchReq req) {
    if (req == null || req.getDocIds() == null || req.getDocIds().isEmpty()) {
      throw new IllegalArgumentException("docIds 不能为空");
    }
    return Result.success(batchDocumentService.batchReprocess(req.getDocIds()));
  }

  /**
   * 查询批量重处理进度。
   *
   * <p>GET /api/v1/documents/batch/reprocess/status/{batchId}
   */
  @GetMapping("/batch/reprocess/status/{batchId}")
  public Result<BatchProgressRespDTO> getProgress(@PathVariable String batchId) {
    BatchProgressRespDTO progress = batchDocumentService.getBatchProgress(batchId);
    return Result.success(progress);
  }

  /**
   * 获取文档关联问答记录。
   *
   * <p>GET /api/v1/documents/{docId}/related-chats
   */
  @GetMapping("/{docId}/related-chats")
  public Result<List<RelatedChatRespDTO>> getRelatedChats(@PathVariable Long docId) {
    return Result.success(documentRelationService.getRelatedChats(docId));
  }
}
