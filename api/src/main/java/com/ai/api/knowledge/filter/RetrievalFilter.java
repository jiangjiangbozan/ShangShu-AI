package com.ai.api.knowledge.filter;

import com.ai.api.knowledge.entity.KnowledgeDocument;
import com.ai.api.knowledge.mapper.KnowledgeDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * RAG 结构化过滤条件（add-3rd-4th-retrieval）。
 *
 * <p>对齐 Dify 的"结构化筛选"（knowledge_base_id / document_id / file_type 等元数据条件）：
 *
 * <ul>
 *   <li>{@link #kbId()}：知识库 ID，直接下推给各检索路（ES bool filter / MySQL where）
 *   <li>{@link #documentId()}：文档 ID 精确过滤
 *   <li>{@link #fileType()}：文档类型过滤（pdf / docx / md / txt …），因 chunk 的 ES metadata 仅含
 *       knowledgeBaseId + documentId，fileType 属于 knowledge_document 表，故先走 MySQL（结构化 SQL）解析出命中的
 *       documentId 集合，再下推到 ES / KG 检索
 * </ul>
 *
 * <p>通过 {@link #resolveDocumentIds(KnowledgeDocumentMapper)} 统一把文档级条件解析为 documentId 集合（无文档级约束返回
 * {@code null}），各检索路据此做过滤。
 *
 * @author add-3rd-4th-retrieval
 */
public record RetrievalFilter(Long kbId, Long documentId, String fileType) {

  public static RetrievalFilter of(Long kbId, Long documentId, String fileType) {
    return new RetrievalFilter(kbId, documentId, fileType);
  }

  /** 是否存在文档级约束（documentId / fileType）。 */
  public boolean hasDocumentConstraint() {
    return documentId != null || (fileType != null && !fileType.isBlank());
  }

  /**
   * 将文档级约束解析为 documentId 集合。
   *
   * @param documentMapper knowledge_document Mapper
   * @return 命中的 documentId 集合；无文档级约束返回 null（表示不过滤）
   */
  public Set<Long> resolveDocumentIds(KnowledgeDocumentMapper documentMapper) {
    if (!hasDocumentConstraint()) {
      return null;
    }
    LambdaQueryWrapper<KnowledgeDocument> wrapper =
        new LambdaQueryWrapper<KnowledgeDocument>().select(KnowledgeDocument::getId);
    if (kbId != null) {
      wrapper.eq(KnowledgeDocument::getKnowledgeBaseId, kbId);
    }
    if (documentId != null) {
      wrapper.eq(KnowledgeDocument::getId, documentId);
    }
    if (fileType != null && !fileType.isBlank()) {
      wrapper.eq(KnowledgeDocument::getFileType, fileType);
    }
    List<KnowledgeDocument> docs = documentMapper.selectList(wrapper);
    Set<Long> ids = new LinkedHashSet<>(docs.size());
    for (KnowledgeDocument doc : docs) {
      ids.add(doc.getId());
    }
    return ids;
  }

  /** 解析后的 documentId 集合是否为空（有约束但没有任何文档命中）。 */
  public boolean resolvedEmpty(Set<Long> documentIds) {
    return hasDocumentConstraint() && (documentIds == null || documentIds.isEmpty());
  }
}
