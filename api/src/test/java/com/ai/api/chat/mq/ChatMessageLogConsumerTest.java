package com.ai.api.chat.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.api.chat.cache.ChatHistoryCacheService;
import com.ai.api.chat.entity.ChatMessage;
import com.ai.api.chat.mapper.ChatMessageMapper;
import com.ai.api.data.config.RabbitMqConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;

/**
 * {@link ChatMessageLogConsumer} 单元测试。
 *
 * <p>重点覆盖幂等命中（DuplicateKeyException）分支： 修复前该分支只 ack 丢弃、从不发布 ES 索引任务，导致 ES 双写被幂等
 * 短路、新消息永远无法被全局检索。修复后幂等命中时反查已入库记录并复用主键继续发布 ES 索引任务。
 *
 * @author fix-chat-search-es-query
 */
@DisplayName("ChatMessageLogConsumer 幂等命中发布 ES 索引任务测试")
@ExtendWith(MockitoExtension.class)
class ChatMessageLogConsumerTest {

  @Mock private ChatMessageMapper messageMapper;
  @Mock private ChatHistoryCacheService chatHistoryCacheService;
  @Mock private RabbitTemplate rabbitTemplate;
  @Mock private Channel channel;

  @InjectMocks private ChatMessageLogConsumer consumer;

  private ChatMessageLogMessage buildPayload(String role, String mqMsgId) {
    ChatMessageLogMessage payload = new ChatMessageLogMessage();
    payload.setSessionId(100L);
    payload.setUserId(200L);
    payload.setRole(role);
    payload.setContent("你好，请介绍一下具身智能。");
    payload.setModelId("test-model");
    payload.setTotalTokens(10);
    payload.setLatencyMs(100);
    payload.setMqMsgId(mqMsgId);
    payload.setTitle("测试会话");
    return payload;
  }

  @Test
  @DisplayName("幂等命中：MQ 消息重复（assistant 已直存）时仍发布 ES 索引任务")
  void duplicateKey_shouldStillPublishEsIndexTask() throws Exception {
    // given：直存已写入 chat_message（带同一 mqMsgId），Consumer insert 必然幂等命中
    String mqMsgId = "44968bb2-1eb0-48c3-bacd-7b96c2303fef";
    when(messageMapper.selectOne(any(LambdaQueryWrapper.class)))
        .thenAnswer(
            inv -> {
              ChatMessage existing = new ChatMessage();
              existing.setId(157L);
              existing.setSessionId(100L);
              existing.setRole("assistant");
              existing.setContent("已入库内容");
              existing.setMqMsgId(mqMsgId);
              return existing;
            });
    doThrow(new DuplicateKeyException("uk_chat_message_mq_msg_id"))
        .when(messageMapper)
        .insert(any(ChatMessage.class));

    // when
    consumer.handleMessageLog(buildPayload("assistant", mqMsgId), 1L, mqMsgId, channel);

    // then：幂等命中后仍应发布 ES 索引任务到 chat.search.index 队列
    verify(rabbitTemplate, times(1))
        .convertAndSend(
            eq(RabbitMqConfig.CHAT_SEARCH_INDEX_EXCHANGE),
            eq(RabbitMqConfig.CHAT_SEARCH_INDEX_ROUTING_KEY),
            anyString());
    // 且最终正常 ack，消息不丢失
    verify(channel, times(1)).basicAck(1L, false);
  }

  @Test
  @DisplayName("幂等命中但反查无记录：跳过 ES 索引但正常 ack，不 nack 不抛错")
  void duplicateKey_withoutExisting_shouldAckWithoutPublishing() throws Exception {
    String mqMsgId = "some-uuid";
    when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
    doThrow(new DuplicateKeyException("uk_chat_message_mq_msg_id"))
        .when(messageMapper)
        .insert(any(ChatMessage.class));

    consumer.handleMessageLog(buildPayload("assistant", mqMsgId), 1L, mqMsgId, channel);

    verify(rabbitTemplate, never())
        .convertAndSend(
            eq(RabbitMqConfig.CHAT_SEARCH_INDEX_EXCHANGE),
            eq(RabbitMqConfig.CHAT_SEARCH_INDEX_ROUTING_KEY),
            anyString());
    verify(channel, times(1)).basicAck(1L, false);
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
  }

  @Test
  @DisplayName("正常路径：insert 成功后发布 ES 索引任务并 ack")
  void normalInsert_shouldPublishEsIndexTaskAndAck() throws Exception {
    String mqMsgId = "normal-uuid";
    // insert 成功：MyBatis-Plus 回填主键
    when(messageMapper.insert(any(ChatMessage.class)))
        .thenAnswer(
            inv -> {
              ChatMessage m = inv.getArgument(0);
              m.setId(999L);
              return 1;
            });

    consumer.handleMessageLog(buildPayload("user", mqMsgId), 1L, mqMsgId, channel);

    verify(rabbitTemplate, times(1))
        .convertAndSend(
            eq(RabbitMqConfig.CHAT_SEARCH_INDEX_EXCHANGE),
            eq(RabbitMqConfig.CHAT_SEARCH_INDEX_ROUTING_KEY),
            anyString());
    verify(channel, times(1)).basicAck(1L, false);
  }

  @Test
  @DisplayName("反序列化消息应包含 mqMsgId 且可被读取")
  void payloadShouldCarryMqMsgId() {
    ChatMessageLogMessage payload = buildPayload("user", "abc-123");
    assertThat(payload.getMqMsgId()).isEqualTo("abc-123");
    assertThat(payload.getSessionId()).isEqualTo(100L);
    assertThat(payload.getUserId()).isEqualTo(200L);
  }
}
