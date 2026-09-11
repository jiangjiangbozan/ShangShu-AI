package com.ai.api.document.service;

import com.ai.api.chat.entity.ChatMessage;
import com.ai.api.chat.entity.ChatSession;
import com.ai.api.chat.mapper.ChatMessageMapper;
import com.ai.api.chat.mapper.ChatSessionMapper;
import com.ai.api.chat.search.ChatSearchIndexInitializer;
import com.ai.api.document.dto.RelatedChatRespDTO;
import com.ai.api.knowledge.entity.KnowledgeChunk;
import com.ai.api.knowledge.mapper.KnowledgeChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;

/**
 * 文档关联问答记录 Service（add-p3-rag-feedback-ops）。
 *
 * <p>展示该文档被哪些会话命中引用，辅助内容运营与调优。
 *
 * <p>实现：取文档前 N 个分片内容片段（默认 200 字符），对 ES {@code chat_message_log} 索引做 match_phrase 查询，按 createTime
 * 倒序聚合 top 20。同会话命中前的最近一条 user 消息作为 querySnippet。
 *
 * @author add-p3-rag-feedback-ops
 */
@Slf4j
@Service
public class DocumentRelationService {

  /** 单文档参与关联检索的分片上限，避免大文档触发海量 ES 查询。 */
  private static final int MAX_CHUNKS_PER_DOC = 5;

  /** 单分片检索片段长度（match_phrase 输入过长会触发 ES 解析开销）。 */
  private static final int SNIPPET_LEN = 200;

  /** 返回的关联记录条数上限。 */
  private static final int TOP_LIMIT = 20;

  private final KnowledgeChunkMapper chunkMapper;
  private final ElasticsearchOperations esOperations;
  private final ChatSearchIndexInitializer indexInitializer;
  private final ChatSessionMapper sessionMapper;
  private final ChatMessageMapper messageMapper;

  public DocumentRelationService(
      KnowledgeChunkMapper chunkMapper,
      ElasticsearchOperations esOperations,
      ChatSearchIndexInitializer indexInitializer,
      ChatSessionMapper sessionMapper,
      ChatMessageMapper messageMapper) {
    this.chunkMapper = chunkMapper;
    this.esOperations = esOperations;
    this.indexInitializer = indexInitializer;
    this.sessionMapper = sessionMapper;
    this.messageMapper = messageMapper;
  }

  /**
   * 查询文档关联的问答记录。
   *
   * @param docId 文档 ID
   * @return 关联记录列表，按 hitTime 倒序 top 20
   */
  public List<RelatedChatRespDTO> getRelatedChats(Long docId) {
    if (docId == null) {
      return List.of();
    }
    // 1. 取文档前 N 个分片内容（按 chunkIndex 升序）
    List<KnowledgeChunk> chunks =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, docId)
                .orderByAsc(KnowledgeChunk::getChunkIndex)
                .last("LIMIT " + MAX_CHUNKS_PER_DOC));
    if (chunks.isEmpty()) {
      return List.of();
    }

    // 2. 对每个分片内容做 match_phrase 查询，聚合去重
    Set<String> seenMessageIds = new HashSet<>();
    List<HitEntry> entries = new ArrayList<>();
    for (KnowledgeChunk chunk : chunks) {
      String content = chunk.getContent();
      if (content == null || content.isBlank()) {
        continue;
      }
      String snippet = content.length() > SNIPPET_LEN ? content.substring(0, SNIPPET_LEN) : content;
      List<HitEntry> hits = searchByContent(snippet);
      for (HitEntry h : hits) {
        if (h.messageId == null || !seenMessageIds.add(h.messageId)) {
          continue;
        }
        h.chunkContent = content;
        entries.add(h);
      }
    }

    if (entries.isEmpty()) {
      return List.of();
    }

    // 3. 按 hitTime 倒序取 top N
    entries.sort(Comparator.comparing(e -> e.hitTime == null ? LocalDateTime.MIN : e.hitTime));
    List<HitEntry> top = new ArrayList<>(entries.subList(0, Math.min(TOP_LIMIT, entries.size())));
    java.util.Collections.reverse(top);

    // 4. 批量查询会话标题
    Set<Long> sessionIds = new java.util.LinkedHashSet<>();
    for (HitEntry h : top) {
      if (h.sessionId != null) {
        sessionIds.add(h.sessionId);
      }
    }
    Map<Long, String> titleMap = new HashMap<>();
    if (!sessionIds.isEmpty()) {
      // selectBatchIds 直接按主键批量查询，避免 LambdaQueryWrapper.in 类型推断问题
      List<ChatSession> sessions = sessionMapper.selectBatchIds(sessionIds);
      for (ChatSession s : sessions) {
        titleMap.put(s.getId(), s.getTitle());
      }
    }

    // 5. 构造响应（querySnippet：同会话命中前的最近 user 消息）
    List<RelatedChatRespDTO> result = new ArrayList<>(top.size());
    for (HitEntry h : top) {
      RelatedChatRespDTO resp = new RelatedChatRespDTO();
      resp.setSessionId(h.sessionId);
      resp.setSessionTitle(titleMap.get(h.sessionId));
      resp.setHitTime(h.hitTime);
      resp.setChunkContent(h.chunkContent);
      resp.setMessageId(h.messageId);
      resp.setRole(h.role);
      resp.setQuerySnippet(findLatestUserQuery(h.sessionId, h.hitTime));
      result.add(resp);
    }
    return result;
  }

  /**
   * 对单条片段做 match_phrase 查询。
   *
   * <p>Spring Data Elasticsearch 6.x 的 {@link StringQuery} 会自动把传入 JSON 包在 {@code {"query":...}}
   * 信封里， 这里只需返回内层 query 子句；排序通过 {@link Pageable} 传入，框架会合并到最终 DSL。
   */
  private List<HitEntry> searchByContent(String snippet) {
    Pageable pageable = PageRequest.of(0, TOP_LIMIT, Sort.by(Sort.Direction.DESC, "createTime"));
    // 仅返回内层 match_phrase 子句，不要外层 query 信封与 sort
    String dsl = "{\"match_phrase\":{\"content\":\"" + escape(snippet) + "\"}}";
    StringQuery query = new StringQuery(dsl);
    query.setPageable(pageable);
    try {
      SearchHits<Document> hits =
          esOperations.search(query, Document.class, indexInitializer.getIndexCoordinates());
      List<HitEntry> result = new ArrayList<>();
      hits.forEach(
          hit -> {
            Document doc = hit.getContent();
            HitEntry entry = new HitEntry();
            // messageId 在 ES mapping 中为 keyword（String 类型），直接字符串化保留
            Object mid = doc.get("messageId");
            entry.messageId = mid == null ? null : mid.toString();
            Object sid = doc.get("sessionId");
            if (sid instanceof Number n) {
              entry.sessionId = n.longValue();
            } else if (sid != null) {
              try {
                entry.sessionId = Long.parseLong(sid.toString());
              } catch (NumberFormatException ignore) {
                // 非数字 ID 忽略
              }
            }
            entry.role = (String) doc.get("role");
            Object ct = doc.get("createTime");
            entry.hitTime = parseCreateTime(ct);
            result.add(entry);
          });
      return result;
    } catch (Exception e) {
      log.warn(
          "[DocumentRelationService][ES 检索失败（跳过该片段）: snippet={}, error={}]",
          snippet.length() > 80 ? snippet.substring(0, 80) + "..." : snippet,
          e.getMessage());
      return List.of();
    }
  }

  /** 查询同会话命中前的最近一条 user 消息，作为 querySnippet。 */
  private String findLatestUserQuery(Long sessionId, LocalDateTime hitTime) {
    if (sessionId == null) {
      return null;
    }
    try {
      LambdaQueryWrapper<ChatMessage> wrapper =
          new LambdaQueryWrapper<ChatMessage>()
              .eq(ChatMessage::getSessionId, sessionId)
              .eq(ChatMessage::getRole, "user")
              .le(ChatMessage::getCreateTime, hitTime == null ? LocalDateTime.now() : hitTime)
              .orderByDesc(ChatMessage::getCreateTime)
              .last("LIMIT 1");
      ChatMessage msg = messageMapper.selectOne(wrapper);
      if (msg == null || msg.getContent() == null) {
        return null;
      }
      String c = msg.getContent();
      return c.length() > 200 ? c.substring(0, 200) : c;
    } catch (Exception e) {
      log.warn(
          "[DocumentRelationService][查询 user 消息失败（忽略）: sessionId={}, error={}]",
          sessionId,
          e.getMessage());
      return null;
    }
  }

  /** ES DSL 字符串转义。 */
  private String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
  }

  /** createTime 字段解析：epoch_millis (Number) 或 yyyy-MM-dd HH:mm:ss (String)。 */
  private LocalDateTime parseCreateTime(Object ct) {
    if (ct == null) return null;
    if (ct instanceof Number n) {
      return LocalDateTime.ofInstant(
          java.time.Instant.ofEpochMilli(n.longValue()), java.time.ZoneId.systemDefault());
    }
    String s = ct.toString();
    try {
      return LocalDateTime.parse(
          s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    } catch (Exception e) {
      try {
        return LocalDateTime.parse(s);
      } catch (Exception ignore) {
        return null;
      }
    }
  }

  /** ES 检索单条命中项内部结构。 */
  private static class HitEntry {
    String messageId;
    Long sessionId;
    String role;
    LocalDateTime hitTime;
    String chunkContent;
  }
}
