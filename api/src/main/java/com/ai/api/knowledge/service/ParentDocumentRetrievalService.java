package com.ai.api.knowledge.service;

import com.ai.api.knowledge.entity.KnowledgeChunk;
import com.ai.api.knowledge.filter.RetrievalFilter;
import com.ai.api.knowledge.mapper.KnowledgeChunkMapper;
import com.ai.api.knowledge.mapper.KnowledgeDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 父文档召回 Service（add-3rd-4th-retrieval，第三路）。
 *
 * <p>对齐 Dify 的"父文档模式"：chunk 命中后不再只返回单一片段，而是回溯到其所属的父文档， 取命中片段前后若干相邻分片（chunk_index
 * 窗口）拼成父级上下文，避免"只见树木不见森林"—— 单片段往往上下文不完整，父级窗口能补全段落语义。
 *
 * <p>流程：
 *
 * <ol>
 *   <li>ES {@code content} match 召回候选 chunk（复用 {@link KeywordRetrievalService}，带 kbId 过滤）
 *   <li>按 {@code metadata.documentId} 聚合，保留每个父文档内最早命中位置（排序依据）
 *   <li>MySQL 按 documentId 批量加载分片（含 chunkIndex），以命中片段为中心取 ±window 相邻分片
 *   <li>拼装父级上下文 Document（id 形如 {@code parent:{docId}}，与 chunk 层 id 不冲突，可参与 RRF）
 * </ol>
 *
 * <p>降级：ES / MySQL 任一环节失败返回空列表，RRF 融合跳过该路，不影响其他路。
 *
 * @author add-3rd-4th-retrieval
 */
@Slf4j
@Service
public class ParentDocumentRetrievalService {

  /** 父文档上下文单条文本长度上限，防止大文档窗口拼接后撑爆 prompt。 */
  private static final int MAX_PARENT_TEXT_CHARS = 4000;

  private final KeywordRetrievalService keywordRetrievalService;
  private final KnowledgeChunkMapper chunkMapper;
  private final KnowledgeDocumentMapper documentMapper;

  /** 父文档召回开关，关闭时直接返回空列表（RRF 该路天然免疫）。 */
  @Value("${rag.parent-doc.enabled:true}")
  private boolean enabled;

  /** 命中片段两侧各取多少个相邻分片，默认 1（即前后各 1，共 3 片窗口）。 */
  @Value("${rag.parent-doc.window-size:1}")
  private int windowSize;

  public ParentDocumentRetrievalService(
      KeywordRetrievalService keywordRetrievalService,
      KnowledgeChunkMapper chunkMapper,
      KnowledgeDocumentMapper documentMapper) {
    this.keywordRetrievalService = keywordRetrievalService;
    this.chunkMapper = chunkMapper;
    this.documentMapper = documentMapper;
  }

  /**
   * 父文档召回。
   *
   * @param query 检索问句
   * @param topK 返回父文档数量
   * @param filter 结构化过滤条件
   * @return 父级上下文 Document 列表，id 为 {@code parent:{documentId}}
   */
  public List<Document> search(String query, int topK, RetrievalFilter filter) {
    if (!enabled || query == null || query.isBlank() || topK <= 0) {
      return List.of();
    }
    try {
      return doSearch(query, topK, filter);
    } catch (Exception e) {
      log.warn(
          "[ParentDocumentRetrievalService][父文档召回失败，跳过该路: query={}, error={}]",
          query,
          e.getMessage());
      return List.of();
    }
  }

  private List<Document> doSearch(String query, int topK, RetrievalFilter filter) {
    // 1. ES match 召回候选 chunk（margin 放大，为文档级过滤留余量）
    Long kbId = filter == null ? null : filter.kbId();
    int margin = Math.max(topK * 2, 10);
    List<Document> hits = keywordRetrievalService.search(query, margin, kbId);
    if (hits.isEmpty()) {
      return List.of();
    }

    // 文档级结构化约束 → documentId 集合
    Set<Long> allowedDocIds = filter == null ? null : filter.resolveDocumentIds(documentMapper);
    if (filter != null && filter.resolvedEmpty(allowedDocIds)) {
      return List.of();
    }

    // 2. 聚合：documentId -> 最早命中排名（升序越靠前，父文档越相关）
    Map<Long, Integer> docBestRank = new HashMap<>();
    Set<String> hitVectorIds = new HashSet<>();
    Map<String, Long> vectorId2Doc = new HashMap<>();
    int rank = 0;
    for (Document hit : hits) {
      Object didObj = hit.getMetadata().get("documentId");
      if (didObj == null) {
        continue;
      }
      Long docId = parseLong(didObj.toString());
      if (docId == null) {
        continue;
      }
      if (allowedDocIds != null && !allowedDocIds.contains(docId)) {
        continue;
      }
      docBestRank.putIfAbsent(docId, rank);
      String vectorId = hit.getId();
      if (vectorId != null) {
        hitVectorIds.add(vectorId);
        vectorId2Doc.put(vectorId, docId);
      }
      rank++;
    }
    if (docBestRank.isEmpty()) {
      return List.of();
    }

    // 3. MySQL 批量加载命中向量 id 对应的 chunkIndex（定位命中片段在父文档中的位置）
    Map<String, Integer> vectorId2Index = new HashMap<>();
    if (!hitVectorIds.isEmpty()) {
      List<KnowledgeChunk> hitChunks =
          chunkMapper.selectList(
              new LambdaQueryWrapper<KnowledgeChunk>()
                  .in(KnowledgeChunk::getVectorId, hitVectorIds)
                  .select(
                      KnowledgeChunk::getVectorId,
                      KnowledgeChunk::getDocumentId,
                      KnowledgeChunk::getChunkIndex));
      for (KnowledgeChunk c : hitChunks) {
        vectorId2Index.put(c.getVectorId(), c.getChunkIndex());
      }
    }

    // 按命中排名升序取 topK 个父文档
    List<Map.Entry<Long, Integer>> ranked = new ArrayList<>(docBestRank.entrySet());
    ranked.sort(Comparator.comparingInt(Map.Entry::getValue));

    List<Document> result = new ArrayList<>(Math.min(topK, ranked.size()));
    for (int i = 0; i < Math.min(topK, ranked.size()); i++) {
      Long docId = ranked.get(i).getKey();
      Document parent = buildParentContext(docId, vectorId2Doc, vectorId2Index, kbId);
      if (parent != null) {
        result.add(parent);
      }
    }
    log.debug(
        "[ParentDocumentRetrievalService][父文档召回完成: query={}, parentDocs={}]", query, result.size());
    return result;
  }

  /**
   * 构建单个父文档的窗口上下文：取该文档内所有命中片段 index 的并集窗口 {@code [minIdx-window, maxIdx+window]} 内的相邻分片，拼接为父级文本。
   */
  private Document buildParentContext(
      Long docId, Map<String, Long> vectorId2Doc, Map<String, Integer> vectorId2Index, Long kbId) {
    // 该父文档命中的片段 index 集合
    List<Integer> hitIndexes = new ArrayList<>();
    vectorId2Doc.forEach(
        (vectorId, d) -> {
          if (docId.equals(d)) {
            Integer idx = vectorId2Index.get(vectorId);
            if (idx != null) {
              hitIndexes.add(idx);
            }
          }
        });
    if (hitIndexes.isEmpty()) {
      return null;
    }
    int minIdx = hitIndexes.stream().mapToInt(Integer::intValue).min().orElse(0);
    int maxIdx = hitIndexes.stream().mapToInt(Integer::intValue).max().orElse(0);

    // 取窗口内全部分片
    List<KnowledgeChunk> chunks =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, docId)
                .between(
                    KnowledgeChunk::getChunkIndex,
                    Math.max(0, minIdx - windowSize),
                    maxIdx + windowSize)
                .orderByAsc(KnowledgeChunk::getChunkIndex));

    StringBuilder sb = new StringBuilder();
    for (KnowledgeChunk c : chunks) {
      if (c.getContent() == null || c.getContent().isBlank()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append("\n\n---\n\n");
      }
      sb.append(c.getContent());
      if (sb.length() > MAX_PARENT_TEXT_CHARS) {
        sb.setLength(MAX_PARENT_TEXT_CHARS);
        break;
      }
    }
    if (sb.isEmpty()) {
      return null;
    }

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("knowledgeBaseId", kbId == null ? "" : String.valueOf(kbId));
    metadata.put("documentId", String.valueOf(docId));
    metadata.put("isParentContext", "true");
    metadata.put("hitChunkIndexes", hitIndexes.toString());
    return Document.builder().id("parent:" + docId).text(sb.toString()).metadata(metadata).build();
  }

  private Long parseLong(String s) {
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
