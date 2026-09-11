package com.ai.api.chat.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.api.chat.entity.ChatMessage;
import com.ai.api.chat.entity.ChatSession;
import com.ai.api.chat.mapper.ChatMessageMapper;
import com.ai.api.chat.mapper.ChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ChatSearchReindexService} 单元测试。
 *
 * <p>验证存量回填：从 MySQL 全量读取消息，按会话补 userId/title，并逐条写入 ES。
 *
 * @author add-p2-cache-search-rewrite
 */
@DisplayName("ChatSearchReindexService 存量回填测试")
@ExtendWith(MockitoExtension.class)
class ChatSearchReindexServiceTest {

  @Mock private ChatMessageMapper messageMapper;
  @Mock private ChatSessionMapper sessionMapper;
  @Mock private ChatSearchIndexConsumer indexConsumer;

  @InjectMocks private ChatSearchReindexService reindexService;

  private ChatSession session(Long id, Long userId, String title) {
    ChatSession s = new ChatSession();
    s.setId(id);
    s.setUserId(userId);
    s.setTitle(title);
    return s;
  }

  private ChatMessage message(
      Long id, Long sessionId, String role, String content, String modelId) {
    ChatMessage m = new ChatMessage();
    m.setId(id);
    m.setSessionId(sessionId);
    m.setRole(role);
    m.setContent(content);
    m.setModelId(modelId);
    m.setCreateTime(LocalDateTime.of(2026, 8, 1, 12, 0, 0));
    return m;
  }

  @Test
  @DisplayName("回填: 按会话补 userId/title 后逐条调用 indexMessage 写入 ES")
  void reindexAll_shouldIndexAllMessages() {
    // given
    when(sessionMapper.selectList(any())).thenReturn(List.of(session(10L, 200L, "测试会话")));
    // 第一页 2 条（小于 pageSize 触发终止）
    when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(
            List.of(
                message(1L, 10L, "user", "什么是具身智能？", "test-model"),
                message(2L, 10L, "assistant", "具身智能是一种……", "test-model")));

    // when
    int total = reindexService.reindexAll();

    // then
    assertThat(total).isEqualTo(2);
    // user 消息：补 userId/title
    ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Long> sessionCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<String> roleCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
    verify(indexConsumer, times(2))
        .indexMessage(
            idCaptor.capture(),
            sessionCaptor.capture(),
            userIdCaptor.capture(),
            roleCaptor.capture(),
            contentCaptor.capture(),
            titleCaptor.capture(),
            eq("test-model"),
            anyLong());

    assertThat(idCaptor.getAllValues()).containsExactly("1", "2");
    assertThat(roleCaptor.getAllValues()).containsExactly("user", "assistant");
    assertThat(userIdCaptor.getAllValues()).containsExactly(200L, 200L);
    assertThat(titleCaptor.getAllValues()).containsExactly("测试会话", "测试会话");
  }

  @Test
  @DisplayName("回填: 会话不存在时 userId 传 null、title 传 null")
  void reindexAll_sessionMissing_shouldPassNulls() {
    when(sessionMapper.selectList(any())).thenReturn(List.of());
    when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(message(1L, 999L, "user", "孤立消息", null)));

    reindexService.reindexAll();

    verify(indexConsumer, times(1))
        .indexMessage(
            eq("1"), eq(999L), eq(null), eq("user"), eq("孤立消息"), eq(null), eq(null), anyLong());
  }

  @Test
  @DisplayName("回填: 多页数据按 lastId 游标推进直至取尽")
  void reindexAll_multiPage_shouldScanAll() {
    when(sessionMapper.selectList(any())).thenReturn(List.of());
    // 按游标分页模拟：第一页满 200 条，第二页 1 条，第三页空 → 取尽即终止
    when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(
            java.util.stream.IntStream.rangeClosed(1, 200)
                .mapToObj(i -> message((long) i, 10L, "user", "msg-" + i, null))
                .toList())
        .thenReturn(List.of(message(201L, 10L, "user", "msg-201", null)))
        .thenReturn(List.of());

    int total = reindexService.reindexAll();

    assertThat(total).isEqualTo(201);
    verify(indexConsumer, times(201))
        .indexMessage(
            anyString(), anyLong(), any(), anyString(), anyString(), any(), any(), anyLong());
  }
}
