package com.ai.api.cs.slot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.api.cs.entity.CsClarifyTemplate;
import com.ai.api.cs.mapper.CsClarifyTemplateMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ClarifyTemplateRenderer} 单元测试（add-customer-service）。
 *
 * @author add-customer-service
 */
@DisplayName("ClarifyTemplateRenderer 单元测试")
class ClarifyTemplateRendererTest {

  private CsClarifyTemplateMapper mapper;
  private ClarifyTemplateRenderer renderer;

  @BeforeEach
  void setup() {
    mapper = mock(CsClarifyTemplateMapper.class);
    renderer = new ClarifyTemplateRenderer(mapper);
  }

  @Test
  @DisplayName("missingSlots 为空 → 默认话术")
  void emptySlots_defaultMessage() {
    String result = renderer.render("sales_query", List.of());
    assertThat(result).isEqualTo("请提供更多信息");
  }

  @Test
  @DisplayName("missingSlots 为 null → 默认话术")
  void nullSlots_defaultMessage() {
    String result = renderer.render("sales_query", null);
    assertThat(result).isEqualTo("请提供更多信息");
  }

  @Test
  @DisplayName("模板未配置 → 默认话术")
  void noTemplates_defaultMessage() {
    when(mapper.selectList(any())).thenReturn(List.of());
    String result = renderer.render("unknown_intent", List.of("slot1"));
    assertThat(result).isEqualTo("请提供更多信息");
  }

  @Test
  @DisplayName("单槽位：返回该槽位模板")
  void singleSlot_returnTemplate() {
    CsClarifyTemplate t = new CsClarifyTemplate();
    t.setSlotName("region");
    t.setTemplate("请告诉我您想查询的地区");
    when(mapper.selectList(any())).thenReturn(List.of(t));
    String result = renderer.render("sales_query", List.of("region"));
    assertThat(result).isEqualTo("请告诉我您想查询的地区");
  }

  @Test
  @DisplayName("多槽位：分号拼接")
  void multiSlots_joinedBySemicolon() {
    CsClarifyTemplate t1 = new CsClarifyTemplate();
    t1.setSlotName("region");
    t1.setTemplate("请告诉我您想查询的地区");
    CsClarifyTemplate t2 = new CsClarifyTemplate();
    t2.setSlotName("product");
    t2.setTemplate("请告诉我您想查询的产品");
    when(mapper.selectList(any())).thenReturn(List.of(t1, t2));
    String result = renderer.render("sales_query", List.of("region", "product"));
    assertThat(result).isEqualTo("请告诉我您想查询的地区；请告诉我您想查询的产品");
  }

  @Test
  @DisplayName("missingSlots 中包含未配置模板的槽位 → 仅返回已配置的")
  void partialConfigured_returnConfiguredOnly() {
    CsClarifyTemplate t1 = new CsClarifyTemplate();
    t1.setSlotName("region");
    t1.setTemplate("请告诉我您想查询的地区");
    when(mapper.selectList(any())).thenReturn(List.of(t1));
    String result = renderer.render("sales_query", List.of("region", "unknown_slot"));
    assertThat(result).isEqualTo("请告诉我您想查询的地区");
  }
}
