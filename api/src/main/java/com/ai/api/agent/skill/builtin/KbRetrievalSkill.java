package com.ai.api.agent.skill.builtin;

import com.ai.api.agent.skill.Skill;
import com.ai.api.agent.skill.SkillType;
import com.ai.api.knowledge.service.RagRetrievalService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/**
 * 知识库检索 Skill（add-agent-runtime）。
 *
 * <p>复用 M3 的 {@link RagRetrievalService#retrieveWithRrf(Long, String, int)} 执行 RAG 检索。
 *
 * <p>输入：{@code {"query":"产品规格","kbId":2,"topK":5}} 输出：{@code
 * {"chunks":[{"content":"...","score":0.87},...]}}
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component("kbRetrievalSkill")
@RequiredArgsConstructor
public class KbRetrievalSkill {

  private final RagRetrievalService ragRetrievalService;

  @Skill(name = "kb_retrieval", type = SkillType.KB, description = "知识库检索（RRF 融合）")
  public Map<String, Object> retrieve(Map<String, Object> input) {
    String query = (String) input.get("query");
    Object kbIdObj = input.get("kbId");
    if (query == null || kbIdObj == null) {
      Map<String, Object> err = new HashMap<>();
      err.put("error", "query and kbId are required");
      return err;
    }
    Long kbId = kbIdObj instanceof Number n ? n.longValue() : Long.valueOf(kbIdObj.toString());
    int topK = input.get("topK") instanceof Number n ? n.intValue() : 5;

    List<Document> docs = ragRetrievalService.retrieveWithRrf(kbId, query, topK);
    log.info(
        "[KbRetrievalSkill][retrieve][kbId({}) query({}) 命中 {} 条]",
        kbId,
        query,
        docs == null ? 0 : docs.size());

    List<Map<String, Object>> chunks =
        docs == null
            ? List.of()
            : docs.stream()
                .map(
                    d -> {
                      Map<String, Object> m = new HashMap<>();
                      m.put("content", d.getText());
                      m.put("metadata", d.getMetadata());
                      return m;
                    })
                .toList();
    Map<String, Object> result = new HashMap<>();
    result.put("chunks", chunks);
    return result;
  }
}
