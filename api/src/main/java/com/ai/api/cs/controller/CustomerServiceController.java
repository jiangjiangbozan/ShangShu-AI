package com.ai.api.cs.controller;

import com.ai.api.common.result.Result;
import com.ai.api.cs.config.CustomerServiceProperties;
import com.ai.api.cs.entity.CsDialogState;
import com.ai.api.cs.intent.IntentClassifierSkill;
import com.ai.api.cs.kb.KbFallbackService;
import com.ai.api.cs.mapper.CsDialogStateMapper;
import com.ai.api.cs.slot.ClarifyTemplateRenderer;
import com.ai.api.cs.slot.DialogStateMachine;
import com.ai.api.cs.slot.SlotFillerSkill;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * 客服入口 Controller（add-customer-service）。
 *
 * <p>编排：意图识别 → KB 回退 → 槽位填充 → 多轮澄清状态机（{@link DialogStateMachine}）→ 状态持久化。
 *
 * <p>对话状态按 {@code runtimeId} 持久化到 {@code cs_dialog_state}，支持跨会话恢复；槽位缺失时经 {@link
 * ClarifyTemplateRenderer} 渲染澄清话术，超过 {@code cs.clarify-max-rounds} 触发兜底意图。业务执行仍由
 * AgentRunner（COT/FC） 完成，本入口只负责编排与状态维护。
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
  private final DialogStateMachine dialogStateMachine;
  private final ClarifyTemplateRenderer clarifyTemplateRenderer;
  private final CsDialogStateMapper csDialogStateMapper;
  private final CustomerServiceProperties properties;
  private final ObjectMapper objectMapper;

  /** 用户肯定回复（用于 CONFIRMING → EXECUTING 转移）。 */
  private static final List<String> AFFIRMATIVE =
      List.of("是", "是的", "确认", "对", "好的", "yes", "y", "ok");

  /**
   * 处理用户消息：编排意图识别 + KB 回退 + 槽位填充 + 多轮澄清状态机，并持久化对话状态。
   *
   * @param runtimeId 运行时 ID（用于加载/持久化对话状态）
   * @param sessionId 会话 ID（可空，缺省时以 runtimeId 兜底，满足 {@code cs_dialog_state.session_id} 非空约束）
   * @param req 用户消息
   */
  @PostMapping
  public Result<Map<String, Object>> chat(
      @RequestParam(required = false, defaultValue = "0") Long runtimeId,
      @RequestParam(required = false) Long sessionId,
      @RequestBody ChatRequest req) {
    Map<String, Object> response = new HashMap<>();
    String query = req.query();
    if (query == null || query.isBlank()) {
      response.put("error", "query is required");
      return Result.success(response);
    }

    // 0. 加载已存在的对话状态（跨会话恢复）
    CsDialogState dialog =
        runtimeId != null && runtimeId > 0
            ? csDialogStateMapper.selectByRuntimeId(runtimeId)
            : null;
    String currentState = dialog == null ? DialogStateMachine.COLLECTING : dialog.getState();
    int clarifyRounds =
        dialog == null || dialog.getClarifyRounds() == null ? 0 : dialog.getClarifyRounds();
    String stateIntent = dialog == null ? null : dialog.getIntent();

    // 0.1 确认分支：CONFIRMING + 用户肯定回复 → EXECUTING（业务 Skill 由 AgentRunner 执行）
    if (DialogStateMachine.CONFIRMING.equals(currentState) && isAffirmative(query)) {
      persistDialogState(
          dialog,
          runtimeId,
          sessionId,
          stateIntent,
          null,
          DialogStateMachine.EXECUTING,
          clarifyRounds);
      response.put("intent", stateIntent);
      response.put("state", DialogStateMachine.EXECUTING);
      response.put("confirmed", true);
      log.info("[CustomerService][confirmed][runtimeId({}) intent({})]", runtimeId, stateIntent);
      return Result.success(response);
    }

    // 1. 意图识别：续接收集中对话时沿用已识别意图，避免"补充槽位"被误识别为新意图
    Map<String, Object> intentInput = new HashMap<>();
    intentInput.put("query", query);
    Map<String, Object> intentResult = intentClassifierSkill.classify(intentInput);
    String intent =
        stateIntent != null
                && !stateIntent.isBlank()
                && DialogStateMachine.COLLECTING.equals(currentState)
            ? stateIntent
            : String.valueOf(intentResult.get("intent"));
    response.put("intent", intentResult);

    // 2. KB 回退
    Long kbId = kbFallbackService.resolveKbId(intent);
    String kbAnswer = kbFallbackService.tryFallback(runtimeId, query, kbId);
    response.put("kb_answer", kbAnswer);
    if (kbAnswer != null) {
      log.info("[CustomerService][kb hit][query({})]", query);
      return Result.success(response);
    }

    // 3. 槽位填充
    Map<String, Object> slotInput = new HashMap<>();
    slotInput.put("query", query);
    slotInput.put("intent", intent);
    slotInput.put("slots_schema", intentResult.get("slots_needed"));
    Map<String, Object> slotResult = slotFillerSkill.fill(slotInput);
    response.put("slots", slotResult);

    boolean isComplete = Boolean.TRUE.equals(slotResult.get("is_complete"));
    List<String> missingSlots = toStringList(slotResult.get("missing_slots"));

    // 4. 状态机推进（COLLECTING → CONFIRMING / COLLECTING）
    String nextState =
        dialogStateMachine.next(currentState, isComplete, false, false, clarifyRounds);

    // 5. 槽位缺失 → 渲染澄清话术；超过最大澄清轮数 → 兜底意图
    if (!isComplete && DialogStateMachine.COLLECTING.equals(nextState)) {
      clarifyRounds++;
      if (dialogStateMachine.shouldFallback(clarifyRounds)) {
        intent = properties.getFallbackIntent();
        response.put("fallback", true);
        log.info("[CustomerService][clarify exceed max, fallback][runtimeId({})]", runtimeId);
      } else {
        response.put("clarify", clarifyTemplateRenderer.render(intent, missingSlots));
      }
    }
    response.put("state", nextState);

    // 6. 持久化对话状态
    persistDialogState(dialog, runtimeId, sessionId, intent, slotResult, nextState, clarifyRounds);

    return Result.success(response);
  }

  /** 持久化对话状态：无则新增，有则更新（保持同一 runtime 只有一条最新状态）。 */
  private void persistDialogState(
      CsDialogState existing,
      Long runtimeId,
      Long sessionId,
      String intent,
      Map<String, Object> slotResult,
      String state,
      int clarifyRounds) {
    CsDialogState entity = existing == null ? new CsDialogState() : existing;
    entity.setRuntimeId(runtimeId);
    if (sessionId != null) {
      entity.setSessionId(sessionId);
    } else if (entity.getSessionId() == null) {
      entity.setSessionId(runtimeId);
    }
    entity.setIntent(intent);
    entity.setState(state);
    entity.setClarifyRounds(clarifyRounds);
    entity.setFallbackTriggered(0);
    if (slotResult != null) {
      try {
        entity.setSlots(objectMapper.writeValueAsString(slotResult.get("filled_slots")));
      } catch (Exception e) {
        log.warn("[CustomerService][serialize slots failed][{}]", e.getMessage());
      }
    }
    if (existing == null) {
      csDialogStateMapper.insert(entity);
    } else {
      csDialogStateMapper.updateById(entity);
    }
  }

  private boolean isAffirmative(String query) {
    String q = query.trim().toLowerCase();
    return AFFIRMATIVE.contains(q);
  }

  private List<String> toStringList(Object obj) {
    if (obj instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  /** 客服请求 DTO。 */
  public record ChatRequest(String query) {}
}
