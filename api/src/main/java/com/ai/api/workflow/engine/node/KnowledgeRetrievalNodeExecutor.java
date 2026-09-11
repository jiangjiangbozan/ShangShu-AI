package com.ai.api.workflow.engine.node;

import com.ai.api.knowledge.service.RagRetrievalService;
import com.ai.api.workflow.engine.NodeContext;
import com.ai.api.workflow.engine.NodeExecutor;
import com.ai.api.workflow.engine.NodeResult;
import com.ai.api.workflow.engine.VariablePool;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识检索节点执行器。
 *
 * <p>调用 RAG 检索服务，根据 query 从指定知识库中检索相关文档片段。 输出：results（检索结果列表）、count（结果数量）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRetrievalNodeExecutor implements NodeExecutor {

  private final RagRetrievalService ragRetrievalService;

  @Override
  public String type() {
    return "knowledge-retrieval";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(NodeContext context) throws Exception {
    Map<String, Object> data = context.data();
    VariablePool vars = context.variables();

    String knowledgeBaseId = (String) data.getOrDefault("knowledgeBaseId", "");
    Map<String, Object> querySelector = (Map<String, Object>) data.getOrDefault("query", Map.of());
    int topK = ((Number) data.getOrDefault("topK", 5)).intValue();
    double scoreThreshold = ((Number) data.getOrDefault("scoreThreshold", 0.5)).doubleValue();

    // 解析查询变量
    String queryText;
    String queryNodeId = (String) querySelector.getOrDefault("nodeId", "");
    String queryKey = (String) querySelector.getOrDefault("outputKey", "");
    if (!queryNodeId.isBlank() && !queryKey.isBlank()) {
      Object resolved = vars.get(queryNodeId, queryKey);
      queryText = resolved != null ? String.valueOf(resolved) : "";
    } else {
      queryText = "";
    }

    if (queryText.isBlank()) {
      throw new IllegalArgumentException("Knowledge Retrieval node: query is empty");
    }

    // 调用 RAG 检索
    List<?> results;
    try {
      results =
          ragRetrievalService.retrieveWithRrf(
              knowledgeBaseId.isBlank() ? null : Long.parseLong(knowledgeBaseId), queryText, topK);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Knowledge Retrieval node: invalid knowledgeBaseId: " + knowledgeBaseId);
    }

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("results", results);
    outputs.put("count", results.size());
    outputs.put("query", queryText);

    log.info(
        "[KnowledgeRetrievalNode][{}][kb={} query={} count={}]",
        context.nodeId(),
        knowledgeBaseId,
        queryText.substring(0, Math.min(queryText.length(), 50)),
        results.size());
    return NodeResult.of(outputs);
  }
}
