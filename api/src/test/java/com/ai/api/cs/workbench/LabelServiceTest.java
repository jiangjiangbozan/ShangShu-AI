package com.ai.api.cs.workbench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.api.cs.entity.CsLabel;
import com.ai.api.cs.mapper.CsLabelMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LabelService} 单元测试（add-customer-service）。
 *
 * @author add-customer-service
 */
@DisplayName("LabelService 单元测试")
class LabelServiceTest {

  private CsLabelMapper csLabelMapper;
  private LabelService labelService;

  @BeforeEach
  void setup() {
    csLabelMapper = mock(CsLabelMapper.class);
    labelService = new LabelService(csLabelMapper);
  }

  @Test
  @DisplayName("正面标注：插入成功")
  void positiveLabel_insertSuccess() {
    labelService.label(1L, "positive", "回答准确", 100L);
    verify(csLabelMapper).insert(org.mockito.ArgumentMatchers.<CsLabel>any());
  }

  @Test
  @DisplayName("负面标注：插入成功")
  void negativeLabel_insertSuccess() {
    labelService.label(1L, "negative", "理解错误", 100L);
    verify(csLabelMapper).insert(org.mockito.ArgumentMatchers.<CsLabel>any());
  }

  @Test
  @DisplayName("中性标注：插入成功")
  void neutralLabel_insertSuccess() {
    labelService.label(1L, "neutral", null, 100L);
    verify(csLabelMapper).insert(org.mockito.ArgumentMatchers.<CsLabel>any());
  }

  @Test
  @DisplayName("非法标注类型：抛 IllegalArgumentException")
  void invalidType_throwsException() {
    assertThatThrownBy(() -> labelService.label(1L, "invalid_type", "测试", 100L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid label_type");
    verify(csLabelMapper, never()).insert(org.mockito.ArgumentMatchers.<CsLabel>any());
  }

  @Test
  @DisplayName("listBySession：返回会话所有标注")
  void listBySession_returnLabels() {
    CsLabel label1 = new CsLabel();
    label1.setId(1L);
    label1.setSessionId(10L);
    label1.setLabelType("positive");
    when(csLabelMapper.selectBySessionId(10L)).thenReturn(List.of(label1));
    List<CsLabel> result = labelService.listBySession(10L);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getLabelType()).isEqualTo("positive");
  }

  @Test
  @DisplayName("listBySession：无标注返回空列表")
  void listBySession_emptyResult() {
    when(csLabelMapper.selectBySessionId(99L)).thenReturn(List.of());
    List<CsLabel> result = labelService.listBySession(99L);
    assertThat(result).isEmpty();
  }
}
