package com.ai.api.cs.slot;

import com.ai.api.cs.entity.CsClarifyTemplate;
import com.ai.api.cs.mapper.CsClarifyTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 澄清话术模板渲染器（add-customer-service）。
 *
 * <p>根据意图 + 缺失槽位列表，查 {@code cs_clarify_template} 表拼接话术。多槽位合并： "请告诉我您想查询的地区和产品"。
 *
 * @author add-customer-service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClarifyTemplateRenderer {

  private final CsClarifyTemplateMapper csClarifyTemplateMapper;

  /**
   * 渲染话术。
   *
   * @param intent 意图名
   * @param missingSlots 缺失槽位列表
   * @return 话术字符串
   */
  public String render(String intent, List<String> missingSlots) {
    if (missingSlots == null || missingSlots.isEmpty()) {
      return "请提供更多信息";
    }

    LambdaQueryWrapper<CsClarifyTemplate> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CsClarifyTemplate::getIntent, intent);
    List<CsClarifyTemplate> templates = csClarifyTemplateMapper.selectList(wrapper);

    if (templates.isEmpty()) {
      return "请提供更多信息";
    }

    List<String> parts =
        missingSlots.stream()
            .map(
                slot -> {
                  return templates.stream()
                      .filter(t -> t.getSlotName().equals(slot))
                      .map(CsClarifyTemplate::getTemplate)
                      .findFirst()
                      .orElse(null);
                })
            .filter(t -> t != null)
            .collect(Collectors.toList());

    if (parts.isEmpty()) {
      return "请提供更多信息";
    }
    if (parts.size() == 1) {
      return parts.get(0);
    }
    // 多槽位合并："请告诉我您想查询的地区" + "请告诉我您想查询的产品"
    // → "请告诉我您想查询的地区和产品"（简化为分号连接）
    return String.join("；", parts);
  }
}
