package com.ai.api.knowledge.service;

import com.ai.api.knowledge.entity.KgEntity;
import com.ai.api.knowledge.entity.KgEntityChunk;
import com.ai.api.knowledge.entity.KgRelation;
import com.ai.api.knowledge.entity.KnowledgeChunk;
import com.ai.api.knowledge.filter.RetrievalFilter;
import com.ai.api.knowledge.mapper.KgEntityChunkMapper;
import com.ai.api.knowledge.mapper.KgEntityMapper;
import com.ai.api.knowledge.mapper.KgRelationMapper;
import com.ai.api.knowledge.mapper.KnowledgeChunkMapper;
import com.ai.api.knowledge.mapper.KnowledgeDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 知识图谱召回 Service（add-3rd-4th-retrieval，第四路）。
 *
 * <p>基于 LLM 抽取的实体-关系图谱（{@code kg_entity / kg_relation / kg_entity_chunk}）做 多跳召回：把用户 query
 * 中的实体与图谱节点对齐，沿关系边做 BFS 扩展（1-N 跳）， 再经"实体-分片"关联定位到具体 chunk。擅长"关系型"问句（如"X 产品的 Y 部门负责人是谁"），
 * 与向量/关键词路的字面与语义召回互补。
 *
 * <p>流程：
 *
 * <ol>
 *   <li>加载知识库内实体（上限 {@code entity-scan-limit}），与 query 做双向包含匹配（query 含实体名 / 实体名含 query）
 *   <li>匹配到的实体作为种子，沿 {@code kg_relation} 无向扩展至多 {@code max-hops} 跳
 *   <li>取种子/邻居实体关联的分片，按（最少跳数升序，命中种子实体数降序）排序
 *   <li>返回 chunk Document（id=vectorId，与向量/关键词路可 RRF 去重），并携带 kgEntities 元数据
 * </ol>
 *
 * <p>降级：知识库无图谱数据 / query 未匹配任何实体 / 任一环节异常 → 返回空列表，RRF 该路天然免疫。
 *
 * @author add-3rd-4th-retrieval
 */
@Slf4j
@Service
public class KnowledgeGraphRetrievalService {

  private final KgEntityMapper entityMapper;
  private final KgRelationMapper relationMapper;
  private final KgEntityChunkMapper entityChunkMapper;
  private final KnowledgeChunkMapper chunkMapper;
  private final KnowledgeDocumentMapper documentMapper;

  /** 图谱召回路开关，关闭时直接返回空列表。 */
  @Value("${rag.knowledge-graph.enabled:true}")
  private boolean enabled;

  /** 图谱扩展最大跳数，默认 2（0 跳=种子实体直连分片）。 */
  @Value("${rag.knowledge-graph.max-hops:2}")
  private int maxHops;

  /** 单次召回最多扫描的实体数，防超大知识库把内存与查询拖垮。 */
  @Value("${rag.knowledge-graph.entity-scan-limit:2000}")
  private int entityScanLimit;

  public KnowledgeGraphRetrievalService(
      KgEntityMapper entityMapper,
      KgRelationMapper relationMapper,
      KgEntityChunkMapper entityChunkMapper,
      KnowledgeChunkMapper chunkMapper,
      KnowledgeDocumentMapper documentMapper) {
    this.entityMapper = entityMapper;
    this.relationMapper = relationMapper;
    this.entityChunkMapper = entityChunkMapper;
    this.chunkMapper = chunkMapper;
    this.documentMapper = documentMapper;
  }

  /**
   * 知识图谱多跳召回。
   *
   * @param query 检索问句
   * @param topK 返回分片数量
   * @param filter 结构化过滤条件
   * @return chunk Document 列表，id=vectorId（与向量/关键词路共享同一 id，RRF 可去重）
   */
  public List<Document> search(String query, int topK, RetrievalFilter filter) {
    if (!enabled || query == null || query.isBlank() || topK <= 0) {
      return List.of();
    }
    try {
      return doSearch(query, topK, filter);
    } catch (Exception e) {
      log.warn(
          "[KnowledgeGraphRetrievalService][图谱召回失败，跳过该路: query={}, error={}]",
          query,
          e.getMessage());
      return List.of();
    }
  }

  private List<Document> doSearch(String query, int topK, RetrievalFilter filter) {
    Long kbId = filter == null ? null : filter.kbId();

    // 1. 加载实体并匹配种子
    List<KgEntity> entities =
        entityMapper.selectList(
            new LambdaQueryWrapper<KgEntity>()
                .eq(kbId != null, KgEntity::getKnowledgeBaseId, kbId)
                .select(KgEntity::getId, KgEntity::getName)
                .last("LIMIT " + entityScanLimit));
    if (entities.isEmpty()) {
      return List.of();
    }
    String normalizedQuery = normalize(query);
    Map<Long, String> matchedNameById = new LinkedHashMap<>();
    for (KgEntity entity : entities) {
      String name = entity.getName();
      if (name == null || name.isBlank()) {
        continue;
      }
      String n = normalize(name);
      // 双向包含匹配：query 含实体名（"退款政策"含实体"退款"）或实体名含 query（短实体被整体提及）
      if (normalizedQuery.contains(n) || n.contains(normalizedQuery)) {
        matchedNameById.put(entity.getId(), name);
      }
    }
    if (matchedNameById.isEmpty()) {
      log.debug("[KnowledgeGraphRetrievalService][query({}) 未匹配到实体，图谱路返回空]", query);
      return List.of();
    }

    // 2. BFS 多跳扩展
    Map<Long, Integer> entityHop = new HashMap<>(); // entityId -> 最少跳数（0=种子）
    Set<Long> frontier = new LinkedHashSet<>(matchedNameById.keySet());
    for (Long seedId : frontier) {
      entityHop.put(seedId, 0);
    }
    for (int hop = 1; hop <= maxHops && !frontier.isEmpty(); hop++) {
      // 复制为 effectively-final，供 lambda 引用（frontier 本体在循环内会被重新赋值）
      final Set<Long> current = frontier;
      List<KgRelation> relations =
          relationMapper.selectList(
              new LambdaQueryWrapper<KgRelation>()
                  .eq(kbId != null, KgRelation::getKnowledgeBaseId, kbId)
                  .and(
                      w ->
                          w.in(KgRelation::getSourceEntityId, current)
                              .or()
                              .in(KgRelation::getTargetEntityId, current)));
      Set<Long> next = new LinkedHashSet<>();
      for (KgRelation rel : relations) {
        if (entityHop.containsKey(rel.getSourceEntityId())) {
          next.add(rel.getTargetEntityId());
        }
        if (entityHop.containsKey(rel.getTargetEntityId())) {
          next.add(rel.getSourceEntityId());
        }
      }
      for (Long id : next) {
        entityHop.putIfAbsent(id, hop);
      }
      frontier = next;
    }

    // 3. 实体 → 分片关联，计算分片最少跳数与种子命中数
    Set<Long> entityIds = entityHop.keySet();
    List<KgEntityChunk> links =
        entityChunkMapper.selectList(
            new LambdaQueryWrapper<KgEntityChunk>()
                .eq(kbId != null, KgEntityChunk::getKnowledgeBaseId, kbId)
                .in(KgEntityChunk::getEntityId, entityIds));
    if (links.isEmpty()) {
      return List.of();
    }
    Map<Long, Integer> chunkHop = new HashMap<>();
    Map<Long, Integer> chunkSeedHits = new HashMap<>();
    for (KgEntityChunk link : links) {
      int h = entityHop.get(link.getEntityId());
      chunkHop.merge(link.getChunkId(), h, Math::min);
      if (h == 0) {
        chunkSeedHits.merge(link.getChunkId(), 1, Integer::sum);
      }
    }

    // 4. 加载分片 + 结构化过滤 + 排序裁剪
    List<KnowledgeChunk> chunks =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeChunk>().in(KnowledgeChunk::getId, chunkHop.keySet()));
    Set<Long> allowedDocIds = filter == null ? null : filter.resolveDocumentIds(documentMapper);
    if (filter != null && filter.resolvedEmpty(allowedDocIds)) {
      return List.of();
    }
    List<KnowledgeChunk> filtered = new ArrayList<>(chunks.size());
    for (KnowledgeChunk c : chunks) {
      if (allowedDocIds != null && !allowedDocIds.contains(c.getDocumentId())) {
        continue;
      }
      filtered.add(c);
    }
    filtered.sort(
        Comparator.comparingInt((KnowledgeChunk c) -> chunkHop.get(c.getId()))
            .thenComparing(c -> -chunkSeedHits.getOrDefault(c.getId(), 0)));

    List<Document> result = new ArrayList<>(Math.min(topK, filtered.size()));
    for (int i = 0; i < Math.min(topK, filtered.size()); i++) {
      KnowledgeChunk c = filtered.get(i);
      if (c.getContent() == null || c.getContent().isBlank()) {
        continue;
      }
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("knowledgeBaseId", kbId == null ? "" : String.valueOf(kbId));
      metadata.put(
          "documentId", c.getDocumentId() == null ? "" : String.valueOf(c.getDocumentId()));
      metadata.put("isKgHit", "true");
      metadata.put("kgEntities", linkedEntityNames(matchedNameById, entityHop, c.getId()));
      result.add(
          Document.builder()
              .id(c.getVectorId() == null ? "kg:" + c.getId() : c.getVectorId())
              .text(c.getContent())
              .metadata(metadata)
              .build());
    }
    log.debug(
        "[KnowledgeGraphRetrievalService][图谱召回完成: query={}, seeds={}, hops={}, hits={}]",
        query,
        matchedNameById.size(),
        entityHop.values().stream().mapToInt(Integer::intValue).max().orElse(0),
        result.size());
    return result;
  }

  /** 取该分片关联实体中"种子实体 + 最近邻居"的名字，用于元数据与日志。 */
  private List<String> linkedEntityNames(
      Map<Long, String> matchedNameById, Map<Long, Integer> entityHop, Long chunkId) {
    List<String> names = new ArrayList<>(4);
    for (Map.Entry<Long, Integer> e : entityHop.entrySet()) {
      if (e.getValue() > 1) {
        continue;
      }
      String name = matchedNameById.get(e.getKey());
      if (name == null) {
        name = String.valueOf(e.getKey());
      }
      names.add(name);
      if (names.size() >= 4) {
        break;
      }
    }
    return names;
  }

  private String normalize(String s) {
    return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
  }
}
