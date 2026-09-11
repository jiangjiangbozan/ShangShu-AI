package com.ai.api.knowledge.service;

import com.ai.api.knowledge.entity.KgEntity;
import com.ai.api.knowledge.entity.KgEntityChunk;
import com.ai.api.knowledge.entity.KgRelation;
import com.ai.api.knowledge.entity.KnowledgeChunk;
import com.ai.api.knowledge.entity.KnowledgeDocument;
import com.ai.api.knowledge.mapper.KgEntityChunkMapper;
import com.ai.api.knowledge.mapper.KgEntityMapper;
import com.ai.api.knowledge.mapper.KgRelationMapper;
import com.ai.api.knowledge.mapper.KnowledgeChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 知识图谱抽取 Service（add-3rd-4th-retrieval，第四路召回的数据侧）。
 *
 * <p>文档上传/重处理完成后异步调用：把文档分片文本交给 LLM，抽取实体 + 实体间关系（三元组）， 落库到 {@code kg_entity / kg_relation /
 * kg_entity_chunk}，供 {@link KnowledgeGraphRetrievalService} 做图谱召回。
 *
 * <p>抽取策略：
 *
 * <ul>
 *   <li>LLM 输出严格 JSON：{@code {"entities":[{"name","type","description"}],
 *       "relations":[{"source","target","type"}]}}
 *   <li>实体按（知识库, 名称）去重，同名实体复用旧记录，描述取最新
 *   <li>实体与分片关联：不依赖 LLM 标注，本地用"分片内容包含实体名"判定，精度可控且零额外成本
 *   <li>重抽取前先清理该文档的旧图谱数据，避免重复与残留
 * </ul>
 *
 * <p>降级：LLM 调用/解析失败仅记录日志，不影响文档上传主流程（@Async 异步执行）。
 *
 * @author add-3rd-4th-retrieval
 */
@Slf4j
@Service
public class KnowledgeGraphExtractionService {

  private final ChatClient extractChatClient;
  private final ChatClient fallbackChatClient;
  private final KgEntityMapper entityMapper;
  private final KgRelationMapper relationMapper;
  private final KgEntityChunkMapper entityChunkMapper;
  private final KnowledgeChunkMapper chunkMapper;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 图谱抽取开关，关闭时跳过抽取（图谱召回路自然无数据）。 */
  @Value("${rag.knowledge-graph.enabled:true}")
  private boolean enabled;

  /** 单文档参与抽取的分片上限，避免大文档一次塞太多 token。 */
  @Value("${rag.knowledge-graph.extract-chunk-limit:8}")
  private int extractChunkLimit;

  /** 单分片参与抽取的字符上限。 */
  @Value("${rag.knowledge-graph.extract-chunk-chars:1200}")
  private int extractChunkChars;

  public KnowledgeGraphExtractionService(
      @Qualifier("deepSeekChatClient") ChatClient deepSeekChatClient,
      @Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
      KgEntityMapper entityMapper,
      KgRelationMapper relationMapper,
      KgEntityChunkMapper entityChunkMapper,
      KnowledgeChunkMapper chunkMapper) {
    this.extractChatClient = deepSeekChatClient;
    this.fallbackChatClient = ollamaChatClient;
    this.entityMapper = entityMapper;
    this.relationMapper = relationMapper;
    this.entityChunkMapper = entityChunkMapper;
    this.chunkMapper = chunkMapper;
  }

  /**
   * 异步抽取文档知识图谱（@Async 不阻塞上传主流程）。
   *
   * @param doc 已完成的文档
   */
  @Async
  public void extractForDocumentAsync(KnowledgeDocument doc) {
    if (!enabled || doc == null) {
      return;
    }
    try {
      doExtract(doc);
    } catch (Exception e) {
      log.warn(
          "[KnowledgeGraphExtractionService][docId({}) 抽取失败（忽略，不影响主流程）: {}]",
          doc.getId(),
          e.getMessage());
    }
  }

  private void doExtract(KnowledgeDocument doc) {
    // 1. 清理该文档旧图谱数据
    cleanByDocument(doc.getKnowledgeBaseId(), doc.getId());

    // 2. 取前 N 个分片（按 chunkIndex 升序）构造抽取输入
    List<KnowledgeChunk> chunks =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, doc.getId())
                .orderByAsc(KnowledgeChunk::getChunkIndex)
                .last("LIMIT " + extractChunkLimit));
    if (chunks.isEmpty()) {
      return;
    }

    // 3. 调 LLM 抽取
    String prompt = buildPrompt(chunks);
    String llmOutput = callModel(prompt);
    ExtractionResult parsed = parseLlmOutput(llmOutput);
    if (parsed == null) {
      log.warn("[KnowledgeGraphExtractionService][docId({}) LLM 输出解析失败，跳过抽取]", doc.getId());
      return;
    }

    // 4. 落库
    persist(doc, chunks, parsed);
    log.info(
        "[KnowledgeGraphExtractionService][docId({}) 抽取完成: entities={}, relations={}]",
        doc.getId(),
        parsed.entities().size(),
        parsed.relations().size());
  }

  /** 清理指定文档的历史图谱数据（实体/关系/关联），供重处理与重复抽取使用。 */
  private void cleanByDocument(Long kbId, Long docId) {
    try {
      List<KnowledgeChunk> docChunks =
          chunkMapper.selectList(
              new LambdaQueryWrapper<KnowledgeChunk>()
                  .eq(KnowledgeChunk::getDocumentId, docId)
                  .select(KnowledgeChunk::getId));
      List<Long> chunkIds = docChunks.stream().map(KnowledgeChunk::getId).toList();
      if (!chunkIds.isEmpty()) {
        entityChunkMapper.delete(
            new LambdaQueryWrapper<KgEntityChunk>()
                .eq(KgEntityChunk::getKnowledgeBaseId, kbId)
                .in(KgEntityChunk::getChunkId, chunkIds));
      }
      relationMapper.delete(
          new LambdaQueryWrapper<KgRelation>()
              .eq(KgRelation::getKnowledgeBaseId, kbId)
              .eq(KgRelation::getSourceDocumentId, docId));
      entityMapper.delete(
          new LambdaQueryWrapper<KgEntity>()
              .eq(KgEntity::getKnowledgeBaseId, kbId)
              .eq(KgEntity::getSourceDocumentId, docId));
    } catch (Exception e) {
      log.warn("[KnowledgeGraphExtractionService][docId({}) 清理旧图谱数据失败: {}]", docId, e.getMessage());
    }
  }

  /**
   * 删除文档时同步清理其图谱数据（实体/关系/实体-分片关联），供文档删除流程调用。
   *
   * @param kbId 知识库 ID
   * @param docId 文档 ID
   */
  public void deleteGraphByDocument(Long kbId, Long docId) {
    if (!enabled || kbId == null || docId == null) {
      return;
    }
    cleanByDocument(kbId, docId);
  }

  /** 落库：实体去重入库 → 实体-分片关联 → 关系入库。 */
  private void persist(
      KnowledgeDocument doc, List<KnowledgeChunk> chunks, ExtractionResult result) {
    Long kbId = doc.getKnowledgeBaseId();
    // name -> entityId（本次抽取会话内的查重缓存）
    Map<String, Long> entityIdByName = new HashMap<>();

    for (KgExtractedEntity e : result.entities()) {
      String name = e.name() == null ? null : e.name().trim();
      if (name == null || name.isEmpty()) {
        continue;
      }
      Long entityId = resolveEntityId(kbId, doc.getId(), name, e.type(), e.description());
      if (entityId == null) {
        continue;
      }
      entityIdByName.put(name, entityId);
      // 本地判定：实体出现在哪些分片，建立关联（用于召回时定位分片）
      for (KnowledgeChunk chunk : chunks) {
        if (chunk.getContent() != null && chunk.getContent().contains(name)) {
          linkEntityChunk(kbId, entityId, chunk.getId());
        }
      }
    }

    for (KgExtractedRelation r : result.relations()) {
      Long sourceId = entityIdByName.get(r.source() == null ? null : r.source().trim());
      Long targetId = entityIdByName.get(r.target() == null ? null : r.target().trim());
      if (sourceId == null || targetId == null || r.type() == null || r.type().isBlank()) {
        continue;
      }
      insertRelationIfAbsent(kbId, doc.getId(), sourceId, targetId, r.type().trim());
    }
  }

  /** 按（kbId, name）解析实体 id：存在复用，不存在插入。 */
  private Long resolveEntityId(
      Long kbId, Long docId, String name, String type, String description) {
    KgEntity existing =
        entityMapper.selectOne(
            new LambdaQueryWrapper<KgEntity>()
                .eq(KgEntity::getKnowledgeBaseId, kbId)
                .eq(KgEntity::getName, name)
                .last("LIMIT 1"));
    if (existing != null) {
      // 已有实体：仅当本次有更完整描述时更新
      if (description != null && !description.isBlank()) {
        existing.setDescription(description);
        entityMapper.updateById(existing);
      }
      return existing.getId();
    }
    KgEntity entity = new KgEntity();
    entity.setKnowledgeBaseId(kbId);
    entity.setName(name);
    entity.setType(type);
    entity.setDescription(description);
    entity.setSourceDocumentId(docId);
    entity.setCreateTime(LocalDateTime.now());
    try {
      entityMapper.insert(entity);
      return entity.getId();
    } catch (Exception e) {
      // 并发插入撞唯一键：回查
      KgEntity again =
          entityMapper.selectOne(
              new LambdaQueryWrapper<KgEntity>()
                  .eq(KgEntity::getKnowledgeBaseId, kbId)
                  .eq(KgEntity::getName, name)
                  .last("LIMIT 1"));
      return again == null ? null : again.getId();
    }
  }

  private void linkEntityChunk(Long kbId, Long entityId, Long chunkId) {
    Long count =
        entityChunkMapper.selectCount(
            new LambdaQueryWrapper<KgEntityChunk>()
                .eq(KgEntityChunk::getEntityId, entityId)
                .eq(KgEntityChunk::getChunkId, chunkId));
    if (count != null && count > 0) {
      return;
    }
    KgEntityChunk link = new KgEntityChunk();
    link.setKnowledgeBaseId(kbId);
    link.setEntityId(entityId);
    link.setChunkId(chunkId);
    link.setCreateTime(LocalDateTime.now());
    entityChunkMapper.insert(link);
  }

  private void insertRelationIfAbsent(
      Long kbId, Long docId, Long sourceId, Long targetId, String type) {
    Long count =
        relationMapper.selectCount(
            new LambdaQueryWrapper<KgRelation>()
                .eq(KgRelation::getSourceEntityId, sourceId)
                .eq(KgRelation::getTargetEntityId, targetId)
                .eq(KgRelation::getRelationType, type));
    if (count != null && count > 0) {
      return;
    }
    KgRelation relation = new KgRelation();
    relation.setKnowledgeBaseId(kbId);
    relation.setSourceEntityId(sourceId);
    relation.setTargetEntityId(targetId);
    relation.setRelationType(type);
    relation.setSourceDocumentId(docId);
    relation.setCreateTime(LocalDateTime.now());
    relationMapper.insert(relation);
  }

  private String callModel(String prompt) {
    String primary = null;
    try {
      primary = extractChatClient.prompt().user(prompt).call().content();
    } catch (Exception e) {
      log.warn("[KnowledgeGraphExtractionService][主模型调用失败，fallback: {}]", e.getMessage());
    }
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    try {
      return fallbackChatClient.prompt().user(prompt).call().content();
    } catch (Exception e) {
      log.warn("[KnowledgeGraphExtractionService][fallback 模型调用失败: {}]", e.getMessage());
      return "";
    }
  }

  private String buildPrompt(List<KnowledgeChunk> chunks) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      String content = chunks.get(i).getContent();
      if (content == null) {
        content = "";
      }
      if (content.length() > extractChunkChars) {
        content = content.substring(0, extractChunkChars);
      }
      sb.append("[分片").append(i + 1).append("]\n").append(content).append("\n\n");
    }
    return String.format(
        Locale.ROOT,
        "你是一个知识图谱抽取助手。请从下面的文档分片中抽取<b>业务实体</b>与<b>实体间关系</b>。\n"
            + "要求：\n"
            + "1. 实体名必须与原文完全一致，不要改写、不要翻译\n"
            + "2. 实体类型简短（如：产品/部门/流程/规则/名词/指标）\n"
            + "3. 关系类型简短（如：属于/包含/导致/适用于/位于/负责/高于）\n"
            + "4. 只输出严格 JSON，不要任何解释或前后缀：\n"
            + "{\"entities\":[{\"name\":\"<实体名>\",\"type\":\"<类型>\",\"description\":\"<一句话描述>\"}],"
            + "\"relations\":[{\"source\":\"<起点实体名>\",\"target\":\"<终点实体名>\",\"type\":\"<关系类型>\"}]}\n\n"
            + "文档分片：\n%s",
        sb);
  }

  /** 解析 LLM 输出为抽取结果；解析失败返回 null。 */
  private ExtractionResult parseLlmOutput(String output) {
    if (output == null || output.isBlank()) {
      return null;
    }
    try {
      int start = output.indexOf('{');
      int end = output.lastIndexOf('}');
      if (start < 0 || end < 0 || end < start) {
        return null;
      }
      Map<String, Object> map =
          objectMapper.readValue(output.substring(start, end + 1), new TypeReference<>() {});
      List<KgExtractedEntity> entities = new ArrayList<>();
      if (map.get("entities") instanceof List<?> entityList) {
        for (Object o : entityList) {
          if (o instanceof Map<?, ?> m) {
            entities.add(
                new KgExtractedEntity(
                    str(m.get("name")), str(m.get("type")), str(m.get("description"))));
          }
        }
      }
      List<KgExtractedRelation> relations = new ArrayList<>();
      if (map.get("relations") instanceof List<?> relationList) {
        for (Object o : relationList) {
          if (o instanceof Map<?, ?> m) {
            relations.add(
                new KgExtractedRelation(
                    str(m.get("source")), str(m.get("target")), str(m.get("type"))));
          }
        }
      }
      if (entities.isEmpty() && relations.isEmpty()) {
        return null;
      }
      return new ExtractionResult(entities, relations);
    } catch (Exception e) {
      log.warn("[KnowledgeGraphExtractionService][解析 LLM 输出失败: {}]", e.getMessage());
      return null;
    }
  }

  private String str(Object o) {
    return o == null ? null : o.toString();
  }

  private record KgExtractedEntity(String name, String type, String description) {}

  private record KgExtractedRelation(String source, String target, String type) {}

  private record ExtractionResult(
      List<KgExtractedEntity> entities, List<KgExtractedRelation> relations) {}
}
