package com.ai.api.cs.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.api.cs.config.CustomerServiceProperties;
import com.ai.api.knowledge.service.RagRetrievalService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.document.Document;

/**
 * {@link KbFallbackService} 单元测试（add-customer-service）。
 *
 * @author add-customer-service
 */
@DisplayName("KbFallbackService 单元测试")
class KbFallbackServiceTest {

  private RagRetrievalService ragRetrievalService;
  private KbHitLogRecorder kbHitLogRecorder;
  private CustomerServiceProperties properties;
  private KbFallbackService service;

  @BeforeEach
  void setup() {
    ragRetrievalService = mock(RagRetrievalService.class);
    kbHitLogRecorder = mock(KbHitLogRecorder.class);
    properties = new CustomerServiceProperties();
    properties.setKbHitThreshold(0.85);
    service = new KbFallbackService(ragRetrievalService, kbHitLogRecorder, properties);
    doNothing()
        .when(kbHitLogRecorder)
        .record(anyLong(), any(), any(), any(), any(), any(Boolean.class));
  }

  @Test
  @DisplayName("query 为 null → 返回 null，不调检索")
  void nullQuery_returnNull() {
    String result = service.tryFallback(1L, null, null);
    assertThat(result).isNull();
    verify(ragRetrievalService, never())
        .retrieveWithRrf(ArgumentMatchers.<Long>any(), any(), anyInt());
  }

  @Test
  @DisplayName("query 为空字符串 → 返回 null")
  void emptyQuery_returnNull() {
    String result = service.tryFallback(1L, "  ", null);
    assertThat(result).isNull();
    verify(ragRetrievalService, never())
        .retrieveWithRrf(ArgumentMatchers.<Long>any(), any(), anyInt());
  }

  @Test
  @DisplayName("检索结果为空 → 返回 null，记录未命中日志")
  void emptyDocs_returnNull() {
    when(ragRetrievalService.retrieveWithRrf(ArgumentMatchers.<Long>any(), any(), anyInt()))
        .thenReturn(List.of());
    String result = service.tryFallback(1L, "测试", null);
    assertThat(result).isNull();
    verify(kbHitLogRecorder).record(1L, "测试", null, null, java.math.BigDecimal.ZERO, false);
  }

  @Test
  @DisplayName("检索异常 → 返回 null，不记录日志")
  void retrieveException_returnNull() {
    when(ragRetrievalService.retrieveWithRrf(ArgumentMatchers.<Long>any(), any(), anyInt()))
        .thenThrow(new RuntimeException("ES 不可用"));
    String result = service.tryFallback(1L, "测试", null);
    assertThat(result).isNull();
    verify(kbHitLogRecorder, never())
        .record(anyLong(), any(), any(), any(), any(), any(Boolean.class));
  }

  @Test
  @DisplayName("检索命中 + score=1.0 >= threshold=0.85 → 返回文档文本")
  void hitReturnDocText() {
    Document doc = new Document("answer content");
    when(ragRetrievalService.retrieveWithRrf(ArgumentMatchers.<Long>any(), any(), anyInt()))
        .thenReturn(List.of(doc));
    String result = service.tryFallback(1L, "测试", null);
    assertThat(result).isEqualTo("answer content");
  }

  @Test
  @DisplayName("resolveKbId：M5 简化实现，所有意图返回 null（默认 KB）")
  void resolveKbId_returnsNull() {
    assertThat(service.resolveKbId("sales_query")).isNull();
    assertThat(service.resolveKbId("any_intent")).isNull();
  }

  @Test
  @DisplayName("getThreshold：返回 properties.kbHitThreshold")
  void getThreshold() {
    assertThat(service.getThreshold()).isEqualTo(0.85);
  }
}
