package com.ai.api.cs.workbench;

import com.ai.api.cs.entity.CsLabel;
import com.ai.api.cs.mapper.CsLabelMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 客服标注回流 Service（add-customer-service）。
 *
 * <p>客服对会话标注（正/负/中性），写入 {@code cs_label} 表用于模型微调与回流。
 *
 * @author add-customer-service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabelService {

  private final CsLabelMapper csLabelMapper;

  /**
   * 提交标注。
   *
   * @param sessionId 会话 ID
   * @param labelType 标注类型（positive/negative/neutral）
   * @param content 标注内容
   * @param labelerId 标注人 ID
   */
  public void label(Long sessionId, String labelType, String content, Long labelerId) {
    if (!isValidType(labelType)) {
      throw new IllegalArgumentException("invalid label_type: " + labelType);
    }
    CsLabel label = new CsLabel();
    label.setSessionId(sessionId);
    label.setLabelType(labelType);
    label.setLabelContent(content);
    label.setLabelerId(labelerId);
    csLabelMapper.insert(label);
    log.info(
        "[LabelService][label][sessionId({}) type({}) labelerId({})]",
        sessionId,
        labelType,
        labelerId);
  }

  /** 查询会话所有标注。 */
  public List<CsLabel> listBySession(Long sessionId) {
    return csLabelMapper.selectBySessionId(sessionId);
  }

  private boolean isValidType(String type) {
    return "positive".equals(type) || "negative".equals(type) || "neutral".equals(type);
  }
}
