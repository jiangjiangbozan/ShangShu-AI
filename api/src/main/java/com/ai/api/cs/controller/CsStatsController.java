package com.ai.api.cs.controller;

import com.ai.api.common.result.Result;
import com.ai.api.cs.mapper.CsKbHitLogMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服统计 Controller（add-customer-service）。
 *
 * <p>提供 KB 命中率统计接口。
 *
 * @author add-customer-service
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cs/stats")
@RequiredArgsConstructor
public class CsStatsController {

  private final CsKbHitLogMapper csKbHitLogMapper;

  /** 按天统计 KB 命中率。 */
  @GetMapping("/kb-hit")
  public Result<List<Map<String, Object>>> kbHitStats(@RequestParam(defaultValue = "7") int days) {
    List<Map<String, Object>> stats = csKbHitLogMapper.selectStatsByDays(days);
    return Result.success(stats);
  }

  /** 汇总 KB 命中率（total/hit/hit_rate）。 */
  @GetMapping("/kb-hit/summary")
  public Result<Map<String, Object>> kbHitSummary(@RequestParam(defaultValue = "7") int days) {
    List<Map<String, Object>> stats = csKbHitLogMapper.selectStatsByDays(days);
    long total = 0;
    long hit = 0;
    for (Map<String, Object> row : stats) {
      Object t = row.get("total");
      Object h = row.get("hit");
      if (t instanceof Number n) total += n.longValue();
      if (h instanceof Number n) hit += n.longValue();
    }
    double rate = total == 0 ? 0.0 : (double) hit / total;
    Map<String, Object> result = new HashMap<>();
    result.put("total", total);
    result.put("hit", hit);
    result.put("hit_rate", Math.round(rate * 10000) / 10000.0);
    result.put("by_day", stats);
    return Result.success(result);
  }
}
