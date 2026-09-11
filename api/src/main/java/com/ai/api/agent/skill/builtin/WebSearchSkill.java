package com.ai.api.agent.skill.builtin;

import com.ai.api.agent.skill.Skill;
import com.ai.api.agent.skill.SkillType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 网络搜索 Skill（add-agent-runtime，mock 实现）。
 *
 * <p>本 change 阶段返回固定 mock 结果，不实际调用外部搜索 API（避免外部依赖与 API Key 配置）。 真正接入由 V2 替换为 SerpAPI / Bing Search
 * API 调用。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component("webSearchSkill")
public class WebSearchSkill {

  @Skill(name = "web_search", type = SkillType.API, description = "网络搜索（mock 实现，返回固定结果）")
  public Map<String, Object> search(Map<String, Object> input) {
    Object qObj = input.get("query");
    String query = qObj == null ? "" : qObj.toString();
    log.info("[WebSearchSkill][search][query({}) mock 返回]", query);

    Map<String, Object> result = new HashMap<>();
    result.put(
        "results",
        List.of(
            Map.of(
                "title",
                "mock result for: " + query,
                "url",
                "https://example.com/mock",
                "snippet",
                "This is a mock search result for query: " + query)));
    return result;
  }
}
