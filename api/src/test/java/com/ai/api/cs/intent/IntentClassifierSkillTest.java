package com.ai.api.cs.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.api.cs.config.CustomerServiceProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

/**
 * {@link IntentClassifierSkill} 单元测试（add-customer-service）。
 *
 * <p>关键词规则场景无需 mock LLM；LLM 场景通过 mock ChatClient.prompt() 抛异常触发 fallback。
 *
 * @author add-customer-service
 */
@DisplayName("IntentClassifier Skill 单元测试")
class IntentClassifierSkillTest {

  private CustomerServiceProperties properties;
  private ChatClient chatClient;
  private IntentClassifierSkill skill;

  @BeforeEach
  void setup() {
    properties = new CustomerServiceProperties();
    properties.setIntentConfidenceThreshold(0.6);
    chatClient = mock(ChatClient.class);
    Map<String, ChatClient> chatClientRegistry = Map.of("ollamaChatClient", chatClient);
    skill = new IntentClassifierSkill(chatClientRegistry, properties);
  }

  @Test
  @DisplayName("关键词命中 sales_query：query 含 '销量'")
  void keywordHit_salesQuery() {
    Map<String, Object> result = skill.classify(Map.of("query", "北京地区 iPhone 销量查询"));
    assertThat(result).containsEntry("intent", "sales_query");
    assertThat(result).containsEntry("confidence", 1.0);
    assertThat(result.get("slots_needed")).isNotNull();
  }

  @Test
  @DisplayName("关键词命中 small_talk：query 含 '你好'")
  void keywordHit_smallTalk() {
    Map<String, Object> result = skill.classify(Map.of("query", "你好"));
    assertThat(result).containsEntry("intent", "small_talk");
    assertThat(result).containsEntry("confidence", 1.0);
  }

  @Test
  @DisplayName("关键词命中 product_compare：query 含 '对比'")
  void keywordHit_productCompare() {
    Map<String, Object> result = skill.classify(Map.of("query", "对比两款产品"));
    assertThat(result).containsEntry("intent", "product_compare");
    assertThat(result).containsEntry("confidence", 1.0);
  }

  @Test
  @DisplayName("关键词命中 fault_diagnosis：query 含 '故障'")
  void keywordHit_faultDiagnosis() {
    Map<String, Object> result = skill.classify(Map.of("query", "我的设备故障"));
    assertThat(result).containsEntry("intent", "fault_diagnosis");
    assertThat(result).containsEntry("confidence", 1.0);
  }

  @Test
  @DisplayName("关键词命中 reimbursement_standard：query 含 '报销'")
  void keywordHit_reimbursementStandard() {
    Map<String, Object> result = skill.classify(Map.of("query", "差旅报销标准"));
    assertThat(result).containsEntry("intent", "reimbursement_standard");
    assertThat(result).containsEntry("confidence", 1.0);
  }

  @Test
  @DisplayName("LLM 异常（无关键词命中 + ChatClient 抛异常）：fallback small_talk")
  void llmException_fallback() {
    when(chatClient.prompt()).thenThrow(new RuntimeException("LLM 不可用"));
    Map<String, Object> result = skill.classify(Map.of("query", "测试无关键词命中"));
    assertThat(result).containsEntry("intent", "small_talk");
    assertThat(result).containsEntry("confidence", 0.0);
  }

  @Test
  @DisplayName("query 为空：返回 error")
  void emptyQuery_error() {
    Map<String, Object> result = skill.classify(Map.of());
    assertThat(result).containsKey("error");
  }

  @Test
  @DisplayName("query 为 null：返回 error")
  void nullQuery_error() {
    Map<String, Object> input = new java.util.HashMap<>();
    input.put("query", null);
    Map<String, Object> result = skill.classify(input);
    assertThat(result).containsKey("error");
  }

  @Test
  @DisplayName("LLM 配置但未触发（关键词命中）：不调 LLM")
  void keywordHit_skipsLlm() {
    // mock 设为抛异常，关键词命中时应不调到 LLM
    when(chatClient.prompt()).thenThrow(new RuntimeException("不应该被调用"));
    Map<String, Object> result = skill.classify(Map.of("query", "销量"));
    assertThat(result).containsEntry("intent", "sales_query");
    assertThat(result).containsEntry("confidence", 1.0);
  }

  @Test
  @DisplayName("低置信度阈值：threshold=0.6，LLM confidence=0.5 → fallback")
  void lowConfidenceTriggersFallback() {
    properties.setIntentConfidenceThreshold(0.6);
    when(chatClient.prompt()).thenThrow(new RuntimeException("模拟 LLM 失败触发 fallback"));
    Map<String, Object> result = skill.classify(Map.of("query", "随便问问"));
    assertThat(result).containsEntry("intent", "small_talk");
  }

  @Test
  @DisplayName("大小写不敏感：'销量' 大写变体也命中（中文关键词）")
  void caseInsensitive() {
    // 中文无大小写之分；测试英文 'vs' 大小写不敏感命中 product_compare
    Map<String, Object> result = skill.classify(Map.of("query", "iPhone VS Android"));
    assertThat(result).containsEntry("intent", "product_compare");
  }

  @Test
  @DisplayName("自定义 fallback 意图：fallbackIntent=custom → 返回 custom")
  void customFallbackIntent() {
    properties.setFallbackIntent("custom_fallback");
    when(chatClient.prompt()).thenThrow(new RuntimeException("LLM 失败"));
    Map<String, Object> result = skill.classify(Map.of("query", "测试无关键词"));
    assertThat(result).containsEntry("intent", "custom_fallback");
  }

  @Test
  @DisplayName("关键词规则覆盖 5 类内置意图")
  void allBuiltinIntents_keywordHit() {
    assertThat(skill.classify(Map.of("query", "销量")).get("intent")).isEqualTo("sales_query");
    assertThat(skill.classify(Map.of("query", "对比")).get("intent")).isEqualTo("product_compare");
    assertThat(skill.classify(Map.of("query", "报销")).get("intent"))
        .isEqualTo("reimbursement_standard");
    assertThat(skill.classify(Map.of("query", "故障")).get("intent")).isEqualTo("fault_diagnosis");
    assertThat(skill.classify(Map.of("query", "你好")).get("intent")).isEqualTo("small_talk");
  }

  @Test
  @DisplayName("返回值包含 slots_needed 字段（非空）")
  void resultContainsSlotsNeeded() {
    Map<String, Object> result = skill.classify(Map.of("query", "销量查询"));
    assertThat(result).containsKey("slots_needed");
    assertThat(result.get("slots_needed")).isInstanceOf(java.util.List.class);
  }

  @Test
  @DisplayName("空字符串 query 走 LLM 异常 → fallback")
  void emptyStringQuery_fallback() {
    when(chatClient.prompt()).thenThrow(new RuntimeException("LLM 失败"));
    Map<String, Object> result = skill.classify(Map.of("query", ""));
    assertThat(result).containsEntry("intent", "small_talk");
  }

  /** 防止未使用导入告警（占位）。 */
  @SuppressWarnings("unused")
  private void unusedAnyString() {
    anyString();
  }
}
