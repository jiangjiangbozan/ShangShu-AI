package com.ai.api.knowledge.service;

import com.ai.api.common.lock.DistributedLockService;
import com.ai.api.knowledge.cache.RagHitsCacheService;
import com.ai.api.knowledge.entity.KnowledgeBase;
import com.ai.api.knowledge.entity.KnowledgeChunk;
import com.ai.api.knowledge.entity.KnowledgeDocument;
import com.ai.api.knowledge.mapper.KnowledgeBaseMapper;
import com.ai.api.knowledge.mapper.KnowledgeChunkMapper;
import com.ai.api.knowledge.mapper.KnowledgeDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理 Service
 *
 * <p>RAG 流程编排：上传文档 → Tika 解析 → 文本分片 → 调用 EmbeddingModel 向量化 → 写入 ElasticsearchVectorStore +
 * 关联表记录元数据。 检索：{@link #searchRelevant(String, int)} 提供基础相似检索， 集成到生成阶段通过 Spring AI
 * QuestionAnswerAdvisor 实现。
 *
 * @author add-rag-module
 */
@Slf4j
@Service
public class KnowledgeBaseService {

  private final KnowledgeBaseMapper baseMapper;
  private final KnowledgeDocumentMapper documentMapper;
  private final KnowledgeChunkMapper chunkMapper;
  private final DocumentParser documentParser;
  private final ChunkSplitter chunkSplitter;
  private final VectorStore vectorStore;
  private final DistributedLockService distributedLockService;
  private final RagHitsCacheService ragHitsCacheService;
  private final EsIndexTuneService esIndexTuneService;
  // 知识图谱抽取服务（add-3rd-4th-retrieval）
  private final KnowledgeGraphExtractionService knowledgeGraphExtractionService;

  @Value("${app.knowledge.upload-dir:./data/knowledge}")
  private String uploadDir;

  @Value("${spring.ai.vectorstore.elasticsearch.index-name:ai_agent_knowledge}")
  private String vectorIndexName;

  public KnowledgeBaseService(
      KnowledgeBaseMapper baseMapper,
      KnowledgeDocumentMapper documentMapper,
      KnowledgeChunkMapper chunkMapper,
      DocumentParser documentParser,
      ChunkSplitter chunkSplitter,
      @Qualifier("vectorStore") VectorStore vectorStore,
      DistributedLockService distributedLockService,
      RagHitsCacheService ragHitsCacheService,
      EsIndexTuneService esIndexTuneService,
      KnowledgeGraphExtractionService knowledgeGraphExtractionService) {
    this.baseMapper = baseMapper;
    this.documentMapper = documentMapper;
    this.chunkMapper = chunkMapper;
    this.documentParser = documentParser;
    this.chunkSplitter = chunkSplitter;
    this.vectorStore = vectorStore;
    this.distributedLockService = distributedLockService;
    this.ragHitsCacheService = ragHitsCacheService;
    this.esIndexTuneService = esIndexTuneService;
    this.knowledgeGraphExtractionService = knowledgeGraphExtractionService;
  }

  /**
   * 创建知识库。
   *
   * @param name 名称
   * @param description 描述
   * @param ownerId 所有者 ID
   * @return 知识库对象
   */
  @Transactional
  public KnowledgeBase createKnowledgeBase(String name, String description, Long ownerId) {
    KnowledgeBase kb = new KnowledgeBase();
    kb.setName(name);
    kb.setDescription(description);
    kb.setOwnerId(ownerId);
    kb.setEmbeddingModel("ollama");
    kb.setChunkSize(800);
    kb.setChunkOverlap(200);
    kb.setDocumentCount(0);
    kb.setChunkCount(0);
    kb.setStatus("active");
    baseMapper.insert(kb);
    return kb;
  }

  /**
   * 列出用户的知识库。
   *
   * @param ownerId 用户 ID
   * @param page 页码
   * @param size 每页大小
   */
  public Page<KnowledgeBase> listKnowledgeBases(Long ownerId, int page, int size) {
    LambdaQueryWrapper<KnowledgeBase> wrapper =
        new LambdaQueryWrapper<KnowledgeBase>()
            .eq(KnowledgeBase::getOwnerId, ownerId)
            .orderByDesc(KnowledgeBase::getUpdateTime);
    return baseMapper.selectPage(Page.of(page, size), wrapper);
  }

  /** 上传并处理文档：保存文件 → 解析 → 分片 → 向量化 → 持久化元数据。 失败时记录错误状态，事务回滚 DB 部分（ES 已写入数据由 id 关联，下次重试可清理）。 */
  @Transactional
  public KnowledgeDocument uploadAndProcess(Long kbId, MultipartFile file) throws IOException {
    // 1. 保存物理文件
    String originalName = file.getOriginalFilename();
    String storedName = UUID.randomUUID() + "_" + originalName;
    // 使用绝对路径：Spring 的 MultipartFile.transferTo(File) 在接收相对路径 File 时，
    // 会以 spring.servlet.multipart.location（或临时目录）为基准解析，而非 JVM 工作目录，
    // 导致 "...\.data\knowledge\1\xxx.docx 系统找不到路径" 异常。
    // 先显式创建多级父目录（createDirectories 是幂等的，存在不报错），
    // 再调用 toAbsolutePath() 保证 transferTo 写到我们期望的位置。
    Path dir = Paths.get(uploadDir, String.valueOf(kbId)).toAbsolutePath();
    Files.createDirectories(dir);
    Path target = dir.resolve(storedName);
    file.transferTo(target.toFile());

    // 2. 解析文本
    KnowledgeDocument doc = new KnowledgeDocument();
    doc.setKnowledgeBaseId(kbId);
    doc.setFileName(originalName);
    doc.setFilePath(target.toString());
    doc.setFileType(detectType(originalName));
    doc.setFileSize(file.getSize());
    doc.setContentHash(sha256(target.toFile()));
    doc.setStatus("parsing");
    documentMapper.insert(doc);

    try (FileInputStream fis = new FileInputStream(target.toFile())) {
      String text = documentParser.parse(fis);
      doc.setStatus("vectorizing");
      documentMapper.updateById(doc);

      KnowledgeBase kb = baseMapper.selectById(kbId);
      List<String> chunks = chunkSplitter.split(text, kb.getChunkSize(), kb.getChunkOverlap());
      int idx = 0;
      for (String chunkText : chunks) {
        // ES 写入：Document 携带 metadata，便于检索后过滤
        String vectorId = UUID.randomUUID().toString();
        Document vectorDoc =
            Document.builder()
                .id(vectorId)
                .text(chunkText)
                .metadata(
                    java.util.Map.of(
                        "knowledgeBaseId", String.valueOf(kbId),
                        "documentId", String.valueOf(doc.getId())))
                .build();
        vectorStore.add(List.of(vectorDoc));

        // 记录 chunk 元数据
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setKnowledgeBaseId(kbId);
        chunk.setDocumentId(doc.getId());
        chunk.setChunkIndex(idx++);
        chunk.setContent(chunkText);
        chunk.setTokenCount(chunkText.length() / 4);
        chunk.setVectorId(vectorId);
        chunkMapper.insert(chunk);
      }
      doc.setChunkCount(chunks.size());
      doc.setStatus("completed");
      documentMapper.updateById(doc);

      // 更新知识库统计
      kb.setDocumentCount(kb.getDocumentCount() + 1);
      kb.setChunkCount(kb.getChunkCount() + chunks.size());
      baseMapper.updateById(kb);
      // 失效该知识库的 RAG 命中缓存（新文档加入后旧缓存失效）
      ragHitsCacheService.evictByKb(kbId);
      // 异步抽取知识图谱（add-3rd-4th-retrieval，best-effort 不阻塞上传主流程）
      knowledgeGraphExtractionService.extractForDocumentAsync(doc);
      return doc;
    } catch (Exception e) {
      log.error("[uploadAndProcess][docId({}) failed({})]", doc.getId(), e.getMessage(), e);
      doc.setStatus("failed");
      doc.setErrorMessage(e.getMessage());
      documentMapper.updateById(doc);
      throw new IllegalStateException("文档处理失败: " + e.getMessage(), e);
    }
  }

  /**
   * 按 ID 查询知识库（含状态校验，供 RAG 调用前判断是否可用）。
   *
   * @param kbId 知识库 ID
   * @return 知识库对象，不存在返回 null
   */
  public KnowledgeBase getKnowledgeBase(Long kbId) {
    if (kbId == null) return null;
    return baseMapper.selectById(kbId);
  }

  /** 基础相似检索：供 Service 调试 / API 直接返回上下文。 集成到生成阶段通过 QuestionAnswerAdvisor 在 ChatClient 层完成。 */
  public List<Document> searchRelevant(String query, int topK) {
    return vectorStore.similaritySearch(
        org.springframework.ai.vectorstore.SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(0.5)
            .build());
  }

  // ---------- 文档管理（list/detail/delete/reprocess） ----------

  /**
   * 分页列出指定知识库下的文档。
   *
   * @param kbId 知识库 ID
   * @param page 页码
   * @param size 每页大小
   * @param keyword 文件名模糊匹配关键字（可选，为 null/空串时不过滤）
   * @param status 状态精确过滤（可选，为 null/空串时不过滤）
   */
  public Page<KnowledgeDocument> listDocuments(
      Long kbId, int page, int size, String keyword, String status) {
    LambdaQueryWrapper<KnowledgeDocument> wrapper =
        new LambdaQueryWrapper<KnowledgeDocument>()
            .eq(KnowledgeDocument::getKnowledgeBaseId, kbId)
            .orderByDesc(KnowledgeDocument::getCreateTime);
    if (keyword != null && !keyword.isBlank()) {
      wrapper.like(KnowledgeDocument::getFileName, keyword);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq(KnowledgeDocument::getStatus, status);
    }
    return documentMapper.selectPage(Page.of(page, size), wrapper);
  }

  /**
   * 查询文档详情，附带前 5 个分片预览（避免一次性返回过多内容）。
   *
   * @param docId 文档 ID
   * @return 文档对象；不存在返回 null
   */
  public KnowledgeDocument getDocumentDetail(Long docId) {
    return documentMapper.selectById(docId);
  }

  /**
   * 查询文档的分片列表（用于详情页预览/管理）。
   *
   * @param docId 文档 ID
   * @param limit 最多返回条数，&lt;=0 表示全部
   */
  public List<KnowledgeChunk> listDocumentChunks(Long docId, int limit) {
    LambdaQueryWrapper<KnowledgeChunk> wrapper =
        new LambdaQueryWrapper<KnowledgeChunk>()
            .eq(KnowledgeChunk::getDocumentId, docId)
            .orderByAsc(KnowledgeChunk::getChunkIndex);
    if (limit > 0) wrapper.last("LIMIT " + limit);
    return chunkMapper.selectList(wrapper);
  }

  /**
   * 更新分片内容（B3 知识库文档分片编辑）。
   *
   * <p>同步 MySQL 分片元信息 + ES 向量数据：删除旧向量文档，用新文本重新向量化后写入， 并更新关联的 vectorId；最后失效该知识库的 RAG 命中缓存，避免检索命中旧内容。
   *
   * @param chunkId 分片 ID
   * @param content 新分片内容
   * @return 更新后的分片
   */
  @Transactional
  public KnowledgeChunk updateChunkContent(Long chunkId, String content) {
    KnowledgeChunk chunk = chunkMapper.selectById(chunkId);
    if (chunk == null) {
      throw new IllegalArgumentException("分片不存在: " + chunkId);
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("分片内容不能为空");
    }

    // 1. 删除旧 ES 向量文档（尽力执行，失败不影响 DB 更新，ES 可通过重处理补偿）
    try {
      if (chunk.getVectorId() != null) {
        vectorStore.delete(java.util.List.of(chunk.getVectorId()));
      }
    } catch (Exception e) {
      log.warn(
          "[updateChunkContent][chunkId({}) ES delete old failed({})]", chunkId, e.getMessage());
    }

    // 2. 新文本重新向量化写入
    String newVectorId = UUID.randomUUID().toString();
    Document vectorDoc =
        Document.builder()
            .id(newVectorId)
            .text(content)
            .metadata(
                java.util.Map.of(
                    "knowledgeBaseId", String.valueOf(chunk.getKnowledgeBaseId()),
                    "documentId", String.valueOf(chunk.getDocumentId())))
            .build();
    try {
      vectorStore.add(java.util.List.of(vectorDoc));
    } catch (Exception e) {
      log.warn(
          "[updateChunkContent][chunkId({}) ES add failed({}), DB update still applied]",
          chunkId,
          e.getMessage());
    }

    // 3. 更新 MySQL 分片元信息
    chunk.setContent(content);
    chunk.setTokenCount(content.length() / 4);
    chunk.setVectorId(newVectorId);
    chunkMapper.updateById(chunk);

    // 4. 失效该知识库的 RAG 命中缓存
    ragHitsCacheService.evictByKb(chunk.getKnowledgeBaseId());

    return chunk;
  }

  /**
   * 删除文档（含物理文件、ES 向量数据、MySQL 元数据）。
   *
   * <p>事务保证 DB 部分原子性；ES 删除放在事务内尽力执行，失败仅记录日志不回滚（ES 数据 可后续通过清理任务补偿，避免一次网络抖动导致 DB 删除被阻塞）。
   *
   * @param docId 文档 ID
   */
  @Transactional
  public void deleteDocument(Long docId) {
    KnowledgeDocument doc = documentMapper.selectById(docId);
    if (doc == null) {
      throw new IllegalArgumentException("文档不存在: " + docId);
    }

    // 1. 删除 ES 向量数据
    List<KnowledgeChunk> chunks =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, docId)
                .select(KnowledgeChunk::getVectorId));
    List<String> vectorIds =
        chunks.stream()
            .map(KnowledgeChunk::getVectorId)
            .filter(java.util.Objects::nonNull)
            .toList();
    if (!vectorIds.isEmpty()) {
      try {
        // Spring AI VectorStore.delete(List<String>) 按 doc id 删除 ES 中的向量文档
        vectorStore.delete(vectorIds);
      } catch (Exception e) {
        // ES 删除失败不阻塞 DB 清理，记录日志后由后续清理任务兜底
        log.warn(
            "[deleteDocument][docId({}) ES delete failed ({}), will skip ES cleanup]",
            docId,
            e.getMessage());
      }
    }

    // 2. 删除 MySQL 元数据
    chunkMapper.delete(
        new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocumentId, docId));
    documentMapper.deleteById(docId);

    // 3. 更新知识库统计
    KnowledgeBase kb = baseMapper.selectById(doc.getKnowledgeBaseId());
    if (kb != null) {
      kb.setDocumentCount(Math.max(0, kb.getDocumentCount() - 1));
      kb.setChunkCount(Math.max(0, kb.getChunkCount() - chunks.size()));
      baseMapper.updateById(kb);
    }

    // 4. 删除物理文件
    try {
      java.nio.file.Path filePath = java.nio.file.Paths.get(doc.getFilePath());
      java.nio.file.Files.deleteIfExists(filePath);
    } catch (Exception e) {
      log.warn("[deleteDocument][docId({}) file delete failed({})]", docId, e.getMessage());
    }
    // 5. 失效该知识库的 RAG 命中缓存（add-p2-cache-search-rewrite）
    ragHitsCacheService.evictByKb(doc.getKnowledgeBaseId());
  }

  /**
   * 批量上传并处理文档（add-p2-cache-search-rewrite）。
   *
   * <p>通过 Redisson 分布式锁 {@code lock:kb:import:{kbId}} 串行化同一知识库的批量导入， 避免并发操作导致的数据重复与配置覆盖。
   *
   * @param kbId 知识库 ID
   * @param files 文件列表
   * @return 每个文件的处理结果（成功 / 失败原因）
   */
  public List<BatchUploadResult> batchUploadAndProcess(Long kbId, List<MultipartFile> files) {
    String lockKey = "lock:kb:import:" + kbId;
    return distributedLockService.tryLockOrElse(
        lockKey,
        30,
        () -> {
          // add-p3-rag-feedback-ops: 批量导入前暂停 ES 索引刷新，提升写入吞吐
          esIndexTuneService.pauseRefresh(vectorIndexName);
          try {
            List<BatchUploadResult> results = new ArrayList<>(files.size());
            for (MultipartFile file : files) {
              BatchUploadResult result = new BatchUploadResult();
              result.setFileName(file.getOriginalFilename());
              try {
                KnowledgeDocument doc = uploadAndProcess(kbId, file);
                result.setSuccess(true);
                result.setDocId(doc.getId());
              } catch (Exception e) {
                result.setSuccess(false);
                result.setError(e.getMessage());
              }
              results.add(result);
            }
            return results;
          } finally {
            // 无论成功失败均恢复刷新，避免索引长时间不可见
            esIndexTuneService.resumeRefresh(vectorIndexName);
          }
        },
        () -> {
          // 未获取到锁：返回单条失败结果，调用方据此返回 409
          List<BatchUploadResult> results = new ArrayList<>(files.size());
          for (MultipartFile file : files) {
            BatchUploadResult r = new BatchUploadResult();
            r.setFileName(file.getOriginalFilename());
            r.setSuccess(false);
            r.setError("知识库正在导入中，请稍后重试");
            results.add(r);
          }
          return results;
        });
  }

  /** 批量上传结果 DTO。 */
  @lombok.Data
  public static class BatchUploadResult {
    private String fileName;
    private Boolean success;
    private Long docId;
    private String error;
  }

  /**
   * 重新处理文档（失败重试）：删除旧分片与 ES 向量，重新解析+向量化。
   *
   * <p>适用于 status=failed 的文档。流程与 uploadAndProcess 一致，但跳过文件保存步骤 （复用原 filePath）。
   *
   * @param docId 文档 ID
   */
  @Transactional
  public KnowledgeDocument reprocessDocument(Long docId) throws IOException {
    KnowledgeDocument doc = documentMapper.selectById(docId);
    if (doc == null) {
      throw new IllegalArgumentException("文档不存在: " + docId);
    }

    // 1. 清理旧分片与 ES 向量（保留 document 记录，复用 filePath）
    List<KnowledgeChunk> oldChunks =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, docId)
                .select(KnowledgeChunk::getVectorId, KnowledgeChunk::getId));
    List<String> oldVectorIds =
        oldChunks.stream()
            .map(KnowledgeChunk::getVectorId)
            .filter(java.util.Objects::nonNull)
            .toList();
    if (!oldVectorIds.isEmpty()) {
      try {
        vectorStore.delete(oldVectorIds);
      } catch (Exception e) {
        log.warn("[reprocessDocument][docId({}) old ES delete failed({})]", docId, e.getMessage());
      }
    }
    chunkMapper.delete(
        new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocumentId, docId));

    // 2. 状态重置
    doc.setStatus("parsing");
    doc.setErrorMessage(null);
    documentMapper.updateById(doc);

    // 3. 重新解析 + 向量化
    java.io.File file = new java.io.File(doc.getFilePath());
    if (!file.exists()) {
      doc.setStatus("failed");
      doc.setErrorMessage("物理文件不存在: " + doc.getFilePath());
      documentMapper.updateById(doc);
      throw new IllegalStateException("物理文件不存在: " + doc.getFilePath());
    }

    try (FileInputStream fis = new FileInputStream(file)) {
      String text = documentParser.parse(fis);
      doc.setStatus("vectorizing");
      documentMapper.updateById(doc);

      KnowledgeBase kb = baseMapper.selectById(doc.getKnowledgeBaseId());
      List<String> chunks = chunkSplitter.split(text, kb.getChunkSize(), kb.getChunkOverlap());
      int idx = 0;
      int prevChunkCount = doc.getChunkCount();
      for (String chunkText : chunks) {
        String vectorId = UUID.randomUUID().toString();
        Document vectorDoc =
            Document.builder()
                .id(vectorId)
                .text(chunkText)
                .metadata(
                    java.util.Map.of(
                        "knowledgeBaseId", String.valueOf(kb.getId()),
                        "documentId", String.valueOf(doc.getId())))
                .build();
        vectorStore.add(List.of(vectorDoc));

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setKnowledgeBaseId(kb.getId());
        chunk.setDocumentId(doc.getId());
        chunk.setChunkIndex(idx++);
        chunk.setContent(chunkText);
        chunk.setTokenCount(chunkText.length() / 4);
        chunk.setVectorId(vectorId);
        chunkMapper.insert(chunk);
      }
      doc.setChunkCount(chunks.size());
      doc.setStatus("completed");
      documentMapper.updateById(doc);

      // 同步知识库统计（替换旧分片数）
      kb.setChunkCount(kb.getChunkCount() - prevChunkCount + chunks.size());
      baseMapper.updateById(kb);
      // 失效该知识库的 RAG 命中缓存（add-p2-cache-search-rewrite）
      ragHitsCacheService.evictByKb(kb.getId());
      // 异步重抽取知识图谱（add-3rd-4th-retrieval，内部先清理旧图谱再抽取）
      knowledgeGraphExtractionService.extractForDocumentAsync(doc);
      return doc;
    } catch (Exception e) {
      log.error("[reprocessDocument][docId({}) failed({})]", docId, e.getMessage(), e);
      doc.setStatus("failed");
      doc.setErrorMessage(e.getMessage());
      documentMapper.updateById(doc);
      throw new IllegalStateException("文档重处理失败: " + e.getMessage(), e);
    }
  }

  // ---------- 工具方法 ----------

  private String detectType(String fileName) {
    String lower = fileName.toLowerCase();
    if (lower.endsWith(".pdf")) return "pdf";
    if (lower.endsWith(".docx") || lower.endsWith(".doc")) return "docx";
    if (lower.endsWith(".md")) return "md";
    if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
    if (lower.endsWith(".txt")) return "txt";
    return "unknown";
  }

  private String sha256(File file) throws IOException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      try (FileInputStream fis = new FileInputStream(file)) {
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) != -1) md.update(buf, 0, n);
      }
      StringBuilder sb = new StringBuilder();
      for (byte b : md.digest()) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      // SHA-256 是 JRE 标准算法，理论不会缺失
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
