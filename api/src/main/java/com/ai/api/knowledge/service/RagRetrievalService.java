package com.ai.api.knowledge.service;

import com.ai.api.knowledge.cache.RagHitsCacheService;
import com.ai.api.knowledge.feedback.ChunkFeedbackService;
import com.ai.api.knowledge.filter.RetrievalFilter;
import com.ai.api.knowledge.mapper.KnowledgeDocumentMapper;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * RAG 多路召回 + RRF 融合 + 精排 Service（add-p2-cache-search-rewrite / add-multi-retriever /
 * add-3rd-4th-retrieval）
 *
 * <p>两级多路召回： 1. 多 Query 扩展召回：QueryRewriteService 将用户原始问句，改写成多条同义/扩展问句 2. 多检索器召回：每个问句按
 * rag.query-rewrite.retrieval-methods 配置的路数召回 - semantic ：向量路（dense_vector kNN 语义召回，擅长同义改写、口语化） -
 * fulltext ：BM25 关键词路（content 字段 match 查询，擅长专有名词、精确编号） - parent ：父文档召回（命中 chunk 回溯父文档 ±window
 * 相邻分片，补全段落语义） - knowledge_graph ：知识图谱召回（实体匹配 + 多跳扩展， 擅长关系型问句） 对齐 Dify
 * RetrievalMethod.HYBRID_SEARCH 的"向量 + 全文并发召回"思想并扩展。
 *
 * <p>结构化 SQL 过滤：{@link RetrievalFilter} 支持 kbId / documentId / fileType 条件，文件级约束先经
 * MySQL（knowledge_document）解析为 documentId 集合，再下推到各检索路。
 *
 * <p>所有路的结果统一用 RRF(Reciprocal Rank Fusion) 倒数排名融合算法合并： 公式：score(d) = Σ 1 / (k + rank_i(d))
 * k：平滑系数；rank_i(d)：文档d在第i路召回结果中的排名，从1开始计数 未出现在某路召回中的文档不参与该路求和（空路天然免疫）。
 *
 * <p>精排：RRF 融合后接入 {@link RerankService} 做二次精排（配置 rag.rerank.*，关闭时跳过）， 把真正能回答 query 的片段顶到最前（对齐 Dify
 * rerank_model 的配置化语义）。
 *
 * <p>支持缓存：传入kbId知识库ID，相同原始query检索结果缓存5分钟，减少向量库压力； 异步统计知识库片段命中次数，用于后续RAG效果统计、反馈优化。
 *
 * @author add-p2-cache-search-rewrite
 */
@Slf4j
@Service
public class RagRetrievalService {

  /** 向量检索方法 */
  private static final String METHOD_SEMANTIC = "semantic";

  /** BM25 关键词检索方法 */
  private static final String METHOD_FULLTEXT = "fulltext";

  /** 父文档召回方法（add-3rd-4th-retrieval） */
  private static final String METHOD_PARENT = "parent";

  /** 知识图谱召回方法（add-3rd-4th-retrieval） */
  private static final String METHOD_KG = "knowledge_graph";

  /** 结构化过滤生效时，单路召回数量放大倍数（过滤后裁剪回 topKPerQuery）。 */
  private static final int FILTER_MARGIN = 3;

  // 向量数据库
  private final VectorStore vectorStore;
  // 查询改写服务：把1个用户问题生成多个扩展问句
  private final QueryRewriteService queryRewriteService;
  // RAG检索结果缓存服务，缓存5分钟
  private final RagHitsCacheService ragHitsCacheService;
  // 检索反馈服务（命中计数 +1）：独立 Bean，跨 Bean 调用 @Async 才会生效
  private final ChunkFeedbackService chunkFeedbackService;
  // BM25 关键词检索服务
  private final KeywordRetrievalService keywordRetrievalService;
  // 父文档召回服务（add-3rd-4th-retrieval）
  private final ParentDocumentRetrievalService parentDocumentRetrievalService;
  // 知识图谱召回服务（add-3rd-4th-retrieval）
  private final KnowledgeGraphRetrievalService knowledgeGraphRetrievalService;
  // 精排服务（add-3rd-4th-retrieval）
  private final RerankService rerankService;
  // 知识库文档 Mapper，用于结构化过滤（fileType/documentId → documentId 集合）
  private final KnowledgeDocumentMapper knowledgeDocumentMapper;

  /** RRF算法平滑系数k，配置文件读取，默认60 k越大，排名先后对分数的影响越弱 */
  @Value("${rag.query-rewrite.rrf-k:60}")
  private int rrfK;

  /** 每一条改写query，从每个检索器取回多少条候选文档 多路的每一路，各自取topN，后续再融合裁剪 */
  @Value("${rag.query-rewrite.top-k-per-query:5}")
  private int topKPerQuery;

  /**
   * 逗号分隔的检索方法列表，默认 hybrid：semantic,fulltext 可配 semantic（仅向量）/ fulltext（仅关键词）/ parent（父文档）/
   * knowledge_graph（知识图谱）任意组合
   */
  @Value("${rag.query-rewrite.retrieval-methods:semantic,fulltext}")
  private String retrievalMethods;

  public RagRetrievalService(
      @Qualifier("vectorStore") VectorStore vectorStore,
      QueryRewriteService queryRewriteService,
      RagHitsCacheService ragHitsCacheService,
      ChunkFeedbackService chunkFeedbackService,
      KeywordRetrievalService keywordRetrievalService,
      ParentDocumentRetrievalService parentDocumentRetrievalService,
      KnowledgeGraphRetrievalService knowledgeGraphRetrievalService,
      RerankService rerankService,
      KnowledgeDocumentMapper knowledgeDocumentMapper) {
    this.vectorStore = vectorStore;
    this.queryRewriteService = queryRewriteService;
    this.ragHitsCacheService = ragHitsCacheService;
    this.chunkFeedbackService = chunkFeedbackService;
    this.keywordRetrievalService = keywordRetrievalService;
    this.parentDocumentRetrievalService = parentDocumentRetrievalService;
    this.knowledgeGraphRetrievalService = knowledgeGraphRetrievalService;
    this.rerankService = rerankService;
    this.knowledgeDocumentMapper = knowledgeDocumentMapper;
  }

  /**
   * 对外入口：多路召回 + RRF融合 + 精排（带缓存）
   *
   * @param kbId 知识库ID；不为null开启缓存；null不走缓存
   * @param rawQuery 用户原始提问
   * @param topK 最终返回给上层的文档数量
   * @return RRF分数降序（精排后重排）的文档列表
   */
  public List<Document> retrieveWithRrf(Long kbId, String rawQuery, int topK) {
    return retrieveWithRrf(RetrievalFilter.of(kbId, null, null), rawQuery, topK);
  }

  /**
   * 对外入口（支持结构化过滤）：多路召回 + RRF融合 + 精排。
   *
   * <p>缓存仅在没有文档级约束（documentId/fileType）时启用，避免同 (kbId, query) 不同过滤条件串缓存。
   *
   * @param filter 结构化过滤条件
   * @param rawQuery 用户原始提问
   * @param topK 最终返回给上层的文档数量
   * @return 文档列表
   */
  public List<Document> retrieveWithRrf(RetrievalFilter filter, String rawQuery, int topK) {
    // 空query直接返回空集合
    if (rawQuery == null || rawQuery.isBlank()) {
      return List.of();
    }
    Long kbId = filter == null ? null : filter.kbId();
    boolean cacheable = kbId != null && (filter == null || !filter.hasDocumentConstraint());

    List<Document> result;
    if (cacheable) {
      // 开启缓存：优先查缓存；缓存未命中，执行真实检索
      result =
          ragHitsCacheService.getOrLoad(
              kbId, rawQuery, () -> doRetrieveWithRrf(filter, rawQuery, topK));
    } else {
      // 无知识库id 或 有文档级过滤：不走缓存，直接执行检索
      result = doRetrieveWithRrf(filter, rawQuery, topK);
    }

    // 异步统计文档命中次数：调用独立 Bean ChunkFeedbackService（跨 Bean 调用 @Async 才生效），
    // 按 vectorId（即 ES 文档 id）批量回写，规避"UUID 无法解析为数值主键"的缺陷；异常不阻塞主检索流程
    try {
      List<String> vectorIds =
          result.stream()
              .map(Document::getId)
              .filter(id -> id != null && !id.isBlank())
              // 跳过 parent:{docId} / kg:{id} 等合成 id，它们不是 vector_id
              .filter(id -> id.indexOf(':') < 0)
              .distinct()
              .toList();
      chunkFeedbackService.incrHitsByVectorIds(vectorIds);
    } catch (Exception e) {
      log.debug("[RagRetrievalService][反馈计数失败（忽略）: error={}]", e.getMessage());
    }
    return result;
  }

  /** 兼容旧接口：不传入知识库id，不启用缓存 */
  public List<Document> retrieveWithRrf(String rawQuery, int topK) {
    return retrieveWithRrf(RetrievalFilter.of(null, null, null), rawQuery, topK);
  }

  /**
   * 真实检索逻辑：查询改写、多检索器多路召回、RRF分数融合、精排
   *
   * @param filter 结构化过滤条件
   * @param rawQuery 用户原始问题
   * @param topK 最终输出文档条数
   * @return 排序后的文档（精排后裁剪 topK）
   */
  private List<Document> doRetrieveWithRrf(RetrievalFilter filter, String rawQuery, int topK) {
    // 1. 查询改写：原始问题生成多条扩展问句
    List<String> queries = queryRewriteService.rewrite(rawQuery);
    // 读取配置的检索方法列表：semantic / fulltext / parent / knowledge_graph
    List<String> methods = parseRetrievalMethods();

    // 边界：只有1条问句且只有1个检索器，不需要RRF多路融合，直接单路召回
    if (queries.size() == 1 && methods.size() == 1) {
      return retrieveSingle(rawQuery, topK, methods.get(0), filter);
    }

    // docById：保存全部文档，key=文档id，value=文档对象，做去重
    Map<String, Document> docById = new HashMap<>();
    // rrfScore：存储每个文档累计RRF分数 key=文档id value=RRF总得分
    Map<String, Double> rrfScore = new HashMap<>();

    // 2. 两级循环：每个改写问句 × 每个检索器，各自独立召回
    for (String q : queries) {
      for (String method : methods) {
        List<Document> hits = retrieveByMethod(method, q, filter);
        // 空路/失败路天然免疫：不贡献分数，不影响其他路
        if (hits.isEmpty()) {
          continue;
        }

        // 3. 计算当前这一路每个文档的RRF贡献分
        for (int i = 0; i < hits.size(); i++) {
          Document doc = hits.get(i);
          String id = doc.getId();
          // 保存文档，重复id只存一次
          docById.putIfAbsent(id, doc);
          /**
           * RRF公式：1/(k + rank) i是数组下标从0开始，rank排名从1开始，所以 i+1 delta = 当前这一路给这个文档贡献的分数 merge：多路的分数累加起来
           */
          double delta = 1.0 / (rrfK + (i + 1));
          rrfScore.merge(id, delta, Double::sum);
        }
      }
    }

    // 4. 根据RRF总分数降序排序，分数越高越靠前
    List<Map.Entry<String, Double>> sorted = new ArrayList<>(rrfScore.entrySet());
    sorted.sort(Comparator.comparingDouble(Map.Entry<String, Double>::getValue).reversed());

    // 5. 组装全量排序候选（精排在裁剪前执行，保证候选集足够）
    List<Document> ranked = new ArrayList<>(sorted.size());
    for (Map.Entry<String, Double> e : sorted) {
      ranked.add(docById.get(e.getKey()));
    }
    log.debug(
        "[RagRetrievalService][RRF 融合完成: queries={}, methods={}, rawHits={}]",
        queries.size(),
        methods,
        ranked.size());

    // 6. 精排（关闭时原序返回）后裁剪 topK
    List<Document> reranked = rerankService.rerank(rawQuery, ranked, topK);
    return truncate(reranked, topK);
  }

  /**
   * 按指定检索方法执行单路召回（add-multi-retriever / add-3rd-4th-retrieval）
   *
   * @param method semantic=向量路；fulltext=BM25 关键词路；parent=父文档召回；knowledge_graph=知识图谱召回；未知方法按向量路处理
   * @param query 检索问句
   * @param filter 结构化过滤条件
   * @return 该路召回的候选文档；失败返回空列表，不阻断整体
   */
  private List<Document> retrieveByMethod(String method, String query, RetrievalFilter filter) {
    try {
      return switch (method) {
        case METHOD_FULLTEXT -> retrieveFulltext(query, filter);
        case METHOD_PARENT -> parentDocumentRetrievalService.search(query, topKPerQuery, filter);
        case METHOD_KG -> knowledgeGraphRetrievalService.search(query, topKPerQuery, filter);
        default -> retrieveSemantic(query, filter);
      };
    } catch (Exception e) {
      // 某一路查询报错，只跳过这一路，不中断整体检索
      log.warn(
          "[RagRetrievalService][单路召回失败，跳过: method={}, query={}, error={}]",
          method,
          query,
          e.getMessage());
      return List.of();
    }
  }

  /** 向量路：无结构化约束时直接 topKPerQuery；有约束时放大召回再过滤裁剪。 */
  private List<Document> retrieveSemantic(String query, RetrievalFilter filter) {
    boolean needFilter =
        filter != null && (filter.kbId() != null || filter.hasDocumentConstraint());
    int top = needFilter ? topKPerQuery * FILTER_MARGIN : topKPerQuery;
    List<Document> hits =
        vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(top)
                .similarityThreshold(0.5) // 相似度阈值，低于0.5直接丢弃
                .build());
    return applyStructuredFilter(hits, filter);
  }

  /** BM25 关键词路：kbId 在 ES 过滤，文档级约束放大召回后客户端过滤。 */
  private List<Document> retrieveFulltext(String query, RetrievalFilter filter) {
    Long kbId = filter == null ? null : filter.kbId();
    boolean needDocFilter = filter != null && filter.hasDocumentConstraint();
    int top = needDocFilter ? topKPerQuery * FILTER_MARGIN : topKPerQuery;
    List<Document> hits = keywordRetrievalService.search(query, top, kbId);
    return applyStructuredFilter(hits, filter);
  }

  /**
   * 结构化过滤：kbId（metadata.knowledgeBaseId）+ 文档级约束（documentId/fileType → knowledge_document 解析的
   * documentId 集合）。
   */
  private List<Document> applyStructuredFilter(List<Document> hits, RetrievalFilter filter) {
    if (filter == null || (filter.kbId() == null && !filter.hasDocumentConstraint())) {
      return hits;
    }
    Set<Long> docIds =
        filter.hasDocumentConstraint() ? filter.resolveDocumentIds(knowledgeDocumentMapper) : null;
    if (filter.resolvedEmpty(docIds)) {
      return List.of();
    }
    List<Document> kept = new ArrayList<>(hits.size());
    for (Document doc : hits) {
      if (filter.kbId() != null) {
        Object kb = doc.getMetadata().get("knowledgeBaseId");
        if (kb == null || !filter.kbId().toString().equals(kb.toString())) {
          continue;
        }
      }
      if (docIds != null) {
        Object did = doc.getMetadata().get("documentId");
        if (did == null) {
          continue;
        }
        try {
          if (!docIds.contains(Long.parseLong(did.toString()))) {
            continue;
          }
        } catch (NumberFormatException e) {
          continue;
        }
      }
      kept.add(doc);
    }
    return truncate(kept, topKPerQuery);
  }

  /**
   * 仅单个检索器时直接单路召回，跳过RRF融合（add-multi-retriever）
   *
   * @param query 检索问句
   * @param topK 返回条数
   * @param method 检索方法
   * @param filter 结构化过滤条件
   * @return 单路召回结果（含精排）
   */
  private List<Document> retrieveSingle(
      String query, int topK, String method, RetrievalFilter filter) {
    List<Document> hits = retrieveByMethod(method, query, filter);
    hits = truncate(hits, topK);
    return rerankService.rerank(query, hits, topK);
  }

  /**
   * 解析逗号分隔的检索方法配置，去重保序（add-multi-retriever）
   *
   * @return 检索方法列表，如 [semantic, fulltext, parent, knowledge_graph]
   */
  private List<String> parseRetrievalMethods() {
    return Arrays.stream(retrievalMethods.split(","))
        .map(String::trim)
        .filter(m -> !m.isEmpty())
        .distinct()
        .toList();
  }

  private List<Document> truncate(List<Document> docs, int topK) {
    if (docs == null || docs.size() <= topK) {
      return docs;
    }
    return new ArrayList<>(docs.subList(0, topK));
  }

  /**
   * 工具方法：直接返回 {文档id:文档文本} map，保持排序顺序LinkedHashMap，方便上层组装prompt上下文
   *
   * @param kbId 知识库id
   * @param rawQuery 用户原始问题
   * @param topK 返回文档数量
   * @return LinkedHashMap，顺序和排序保持一致
   */
  public Map<String, String> retrieveTextsWithRrf(Long kbId, String rawQuery, int topK) {
    return retrieveTextsWithRrf(RetrievalFilter.of(kbId, null, null), rawQuery, topK);
  }

  /**
   * 工具方法（支持结构化过滤）：返回 {文档id:文档文本} map，保持排序顺序。
   *
   * @param filter 结构化过滤条件
   * @param rawQuery 用户原始问题
   * @param topK 返回文档数量
   * @return LinkedHashMap
   */
  public Map<String, String> retrieveTextsWithRrf(
      RetrievalFilter filter, String rawQuery, int topK) {
    Map<String, String> texts = new LinkedHashMap<>();
    for (Document doc : retrieveWithRrf(filter, rawQuery, topK)) {
      texts.put(doc.getId(), doc.getText());
    }
    return texts;
  }
}
