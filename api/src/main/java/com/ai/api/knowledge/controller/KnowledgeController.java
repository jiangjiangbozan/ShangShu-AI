package com.ai.api.knowledge.controller;

import com.ai.api.audit.annotation.OperationLog;
import com.ai.api.common.result.Result;
import com.ai.api.identity.security.AuthUserDetails;
import com.ai.api.knowledge.entity.KnowledgeBase;
import com.ai.api.knowledge.entity.KnowledgeChunk;
import com.ai.api.knowledge.entity.KnowledgeDocument;
import com.ai.api.knowledge.feedback.ChunkFeedbackService;
import com.ai.api.knowledge.service.KnowledgeBaseService;
import com.ai.api.knowledge.service.KnowledgeBaseService.BatchUploadResult;
import com.ai.api.knowledge.service.RagRetrievalService;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理 REST API。
 *
 * @author add-rag-module
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

  private final KnowledgeBaseService knowledgeBaseService;
  private final RagRetrievalService ragRetrievalService;
  private final ChunkFeedbackService chunkFeedbackService;

  public KnowledgeController(
      KnowledgeBaseService knowledgeBaseService,
      RagRetrievalService ragRetrievalService,
      ChunkFeedbackService chunkFeedbackService) {
    this.knowledgeBaseService = knowledgeBaseService;
    this.ragRetrievalService = ragRetrievalService;
    this.chunkFeedbackService = chunkFeedbackService;
  }

  @Data
  static class CreateReq {
    String name;
    String description;
  }

  @Data
  static class SearchReq {
    String query;
    int topK = 5;
  }

  /**
   * 创建知识库。
   *
   * <p>POST /api/knowledge
   */
  @PostMapping
  @OperationLog(module = "knowledge", action = "create-kb")
  public Result<KnowledgeBase> create(@RequestBody CreateReq req) {
    // TODO: 从 SecurityContext 获取当前用户 ID，此处先用占位 1L
    Long userId = 1L;
    KnowledgeBase kb = knowledgeBaseService.createKnowledgeBase(req.name, req.description, userId);
    return Result.success(kb);
  }

  /**
   * 列出当前用户的知识库。
   *
   * <p>GET /api/knowledge
   */
  @GetMapping
  public Result<List<KnowledgeBase>> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    Long userId = 1L;
    return Result.success(knowledgeBaseService.listKnowledgeBases(userId, page, size).getRecords());
  }

  /**
   * 上传文档到指定知识库。
   *
   * <p>POST /api/knowledge/{kbId}/upload
   */
  @PostMapping("/{kbId}/upload")
  @OperationLog(module = "knowledge", action = "upload")
  public Result<KnowledgeDocument> upload(
      @PathVariable Long kbId, @RequestParam("file") MultipartFile file) throws IOException {
    return Result.success(knowledgeBaseService.uploadAndProcess(kbId, file));
  }

  /**
   * 批量上传文档到指定知识库（add-p2-cache-search-rewrite）。
   *
   * <p>同一知识库 ID 的并发批量导入会被 Redisson 锁串行化，未获取锁时返回 409。
   *
   * <p>POST /api/v1/knowledge/{kbId}/batch-upload
   */
  @PostMapping("/{kbId}/batch-upload")
  @ResponseStatus(HttpStatus.OK)
  @OperationLog(module = "knowledge", action = "batch-upload")
  public Result<List<BatchUploadResult>> batchUpload(
      @PathVariable Long kbId, @RequestParam("files") List<MultipartFile> files)
      throws IOException {
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("文件列表不能为空");
    }
    return Result.success(knowledgeBaseService.batchUploadAndProcess(kbId, files));
  }

  /**
   * 测试检索：返回匹配的分片文档（仅调试用，正式生成由 ChatClient 内 QuestionAnswerAdvisor 集成）。
   *
   * <p>POST /api/knowledge/search
   *
   * <p>add-p2-cache-search-rewrite：启用 RAG Query 重写 + RRF 融合 + 命中缓存（kbId 非空时）。
   */
  @PostMapping("/search")
  public Result<List<String>> search(@RequestBody SearchReq req) {
    List<Document> docs = ragRetrievalService.retrieveWithRrf((Long) null, req.query, req.topK);
    return Result.success(docs.stream().map(Document::getText).toList());
  }

  // ============ 文档管理（P0-5） ============

  /**
   * 列出指定知识库下的文档（支持文件名模糊搜索 + 状态过滤）。
   *
   * <p>GET /api/v1/knowledge/{kbId}/documents?page=1&size=20&keyword=&status=
   */
  @GetMapping("/{kbId}/documents")
  public Result<List<KnowledgeDocument>> listDocuments(
      @PathVariable Long kbId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status) {
    return Result.success(
        knowledgeBaseService.listDocuments(kbId, page, size, keyword, status).getRecords());
  }

  /**
   * 查询文档详情，附带前 5 个分片预览。
   *
   * <p>GET /api/v1/knowledge/documents/{id}
   */
  @GetMapping("/documents/{id}")
  public Result<Map<String, Object>> getDocumentDetail(@PathVariable Long id) {
    KnowledgeDocument doc = knowledgeBaseService.getDocumentDetail(id);
    if (doc == null) {
      return Result.success(null);
    }
    List<KnowledgeChunk> chunks = knowledgeBaseService.listDocumentChunks(id, 5);
    Map<String, Object> result = new HashMap<>();
    result.put("document", doc);
    result.put("chunkPreview", chunks);
    return Result.success(result);
  }

  /**
   * 查询文档的全部分片（B3 分片预览/编辑）。
   *
   * <p>GET /api/v1/knowledge/documents/{id}/chunks
   */
  @GetMapping("/documents/{id}/chunks")
  public Result<List<KnowledgeChunk>> listDocumentChunks(@PathVariable Long id) {
    return Result.success(knowledgeBaseService.listDocumentChunks(id, 0));
  }

  /**
   * 编辑分片内容（B3）：同步更新 MySQL 元信息与 ES 向量数据。
   *
   * <p>PUT /api/v1/knowledge/chunks/{id}
   */
  @PutMapping("/chunks/{id}")
  @OperationLog(module = "knowledge", action = "update-chunk")
  public Result<KnowledgeChunk> updateChunk(
      @PathVariable Long id, @RequestBody UpdateChunkReq req) {
    return Result.success(knowledgeBaseService.updateChunkContent(id, req.getContent()));
  }

  @Data
  static class UpdateChunkReq {
    private String content;
  }

  /**
   * 删除文档（含物理文件、ES 向量、MySQL 元数据）。
   *
   * <p>DELETE /api/v1/knowledge/documents/{id}
   */
  @DeleteMapping("/documents/{id}")
  @OperationLog(module = "knowledge", action = "delete-document")
  public Result<Void> deleteDocument(@PathVariable Long id) {
    knowledgeBaseService.deleteDocument(id);
    return Result.success(null);
  }

  /**
   * 重新处理文档（失败重试，复用原物理文件重新解析+向量化）。
   *
   * <p>POST /api/v1/knowledge/documents/{id}/reprocess
   */
  @PostMapping("/documents/{id}/reprocess")
  @OperationLog(module = "knowledge", action = "reprocess-document")
  public Result<KnowledgeDocument> reprocessDocument(@PathVariable Long id) throws IOException {
    return Result.success(knowledgeBaseService.reprocessDocument(id));
  }

  /**
   * 反馈：对某条 chunk 点踩（add-p3-rag-feedback-ops）。
   *
   * <p>POST /api/v1/knowledge/chunks/{id}/dislike
   *
   * <p>命中计数 -3（下限 0），通过 chunk_feedback_log 唯一索引保证幂等； 定时任务定期重新处理这些 chunk。
   */
  @PostMapping("/chunks/{id}/dislike")
  public Result<Void> dislikeChunk(@PathVariable Long id) {
    chunkFeedbackService.dislike(id, getCurrentUserId());
    return Result.success(null);
  }

  private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthUserDetails details) {
      return details.userId();
    }
    return 0L;
  }
}
