package com.ai.api.cs.controller;

import com.ai.api.common.result.Result;
import com.ai.api.cs.intent.IntentClassifierSkill;
import com.ai.api.cs.kb.KbFallbackService;
import com.ai.api.cs.slot.SlotFillerSkill;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服入口 Controller（add-customer-service）。
 *
 * <p>编排：意图识别 → KB 回退 → 槽位填充。业务执行由 AgentRunner（COT/FC）完成（M5 仅暴露编排入口与各阶段独立调用接口）。
 *
 * @author add-customer-service
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cs/chat")
@RequiredArgsConstructor
public class CustomerServiceController {

  private final IntentClassifierSkill intentClassifierSkill;
  private final SlotFillerSkill slotFillerSkill;
  private final KbFallbackService kbFallbackService;

  /**
   * 处理用户消息：编排意图识别 + KB 回退 + 槽位填充，返回编排中间结果。
   *
   * <p>M5 阶段不直接调用 AgentRunner（避免循环依赖），仅暴露各阶段独立调用接口， 由前端 / 业务编排层组装调用链。
   */
  @PostMapping
  public Result<Map<String, Object>> chat(
      @RequestParam(required = false, defaultValue = "0") Long runtimeId,
      @RequestBody ChatRequest req) {
    Map<String, Object> response = new HashMap<>();
    String query = req.query();
    if (query == null || query.isBlank()) {
      response.put("error", "query is required");
      return Result.success(response);
    }

    // 1. 意图识别
    Map<String, Object> intentInput = new HashMap<>();
    intentInput.put("query", query);
    Map<String, Object> intentResult = intentClassifierSkill.classify(intentInput);
    response.put("intent", intentResult);

    // 2. KB 回退
    Long kbId = kbFallbackService.resolveKbId((String) intentResult.get("intent"));
    String kbAnswer = kbFallbackService.tryFallback(runtimeId, query, kbId);
    response.put("kb_answer", kbAnswer);
    if (kbAnswer != null) {
      log.info("[CustomerService][kb hit][query({})]", query);
      return Result.success(response);
    }

    // 3. 槽位填充
    Map<String, Object> slotInput = new HashMap<>();
    slotInput.put("query", query);
    slotInput.put("intent", intentResult.get("intent"));
    slotInput.put("slots_schema", intentResult.get("slots_needed"));
    Map<String, Object> slotResult = slotFillerSkill.fill(slotInput);
    response.put("slots", slotResult);

    return Result.success(response);
  }

  /** 客服请求 DTO。 */
  public record ChatRequest(String query) {}
}
