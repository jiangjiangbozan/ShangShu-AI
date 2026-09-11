package com.ai.api.agent.eval.controller;

import com.ai.api.agent.eval.entity.AgentEval;
import com.ai.api.agent.eval.service.AgentEvalService;
import com.ai.api.common.result.PageResult;
import com.ai.api.common.result.Result;
import com.ai.api.identity.security.AuthUserDetails;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.Map;
import lombok.Data;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Agent 评测面板（A3）。 */
@RestController
@RequestMapping("/api/v1/agent-eval")
public class AgentEvalController {

  private final AgentEvalService agentEvalService;

  public AgentEvalController(AgentEvalService agentEvalService) {
    this.agentEvalService = agentEvalService;
  }

  @Data
  static class EvalReq {
    private Integer score;
    private String verdict;
    private String note;
  }

  /** 运行历史分页。GET /api/v1/agent-eval/runs */
  @GetMapping("/runs")
  public Result<PageResult<Map<String, Object>>> listRuns(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String modelId) {
    IPage<Map<String, Object>> result = agentEvalService.listRuns(page, size, status, modelId);
    return Result.success(PageResult.of(result.getRecords(), result.getTotal()));
  }

  /** 运行详情（含 trace 时间轴）。GET /api/v1/agent-eval/runs/{id} */
  @GetMapping("/runs/{id}")
  public Result<Map<String, Object>> detail(@PathVariable Long id) {
    return Result.success(agentEvalService.detail(id));
  }

  /** 提交/更新评测。POST /api/v1/agent-eval/runs/{id}/eval */
  @PostMapping("/runs/{id}/eval")
  public Result<AgentEval> submitEval(@PathVariable Long id, @RequestBody EvalReq req) {
    return Result.success(
        agentEvalService.submitEval(
            id, req.getScore(), req.getVerdict(), req.getNote(), currentUserId()));
  }

  /** 评测汇总。GET /api/v1/agent-eval/stats */
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    return Result.success(agentEvalService.stats());
  }

  private Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthUserDetails details) {
      return details.userId();
    }
    return null;
  }
}
