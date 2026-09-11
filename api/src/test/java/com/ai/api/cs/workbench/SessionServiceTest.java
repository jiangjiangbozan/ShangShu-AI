package com.ai.api.cs.workbench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.api.agent.runtime.AgentRuntimeService;
import com.ai.api.cs.entity.CsSession;
import com.ai.api.cs.mapper.CsSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SessionService} 单元测试（add-customer-service）。
 *
 * @author add-customer-service
 */
@DisplayName("SessionService 单元测试")
class SessionServiceTest {

  private CsSessionMapper csSessionMapper;
  private AgentRuntimeService agentRuntimeService;
  private SessionService sessionService;

  @BeforeEach
  void setup() {
    csSessionMapper = mock(CsSessionMapper.class);
    agentRuntimeService = mock(AgentRuntimeService.class);
    sessionService = new SessionService(csSessionMapper, agentRuntimeService);
  }

  @Test
  @DisplayName("getBySessionId：返回会话")
  void getBySessionId_returnSession() {
    CsSession session = new CsSession();
    session.setSessionId(10L);
    session.setStatus("ai");
    when(csSessionMapper.selectBySessionId(10L)).thenReturn(session);
    CsSession result = sessionService.getBySessionId(10L);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo("ai");
  }

  @Test
  @DisplayName("takeover：会话不存在 → 抛 IllegalArgumentException")
  void takeover_sessionNotFound_throwsException() {
    when(csSessionMapper.selectBySessionId(99L)).thenReturn(null);
    assertThatThrownBy(() -> sessionService.takeover(99L, 100L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("session not found");
    verify(agentRuntimeService, never()).pause(99L);
  }

  @Test
  @DisplayName("takeover：runtimeId 为 null → 直接更新状态，不调 pause")
  void takeover_runtimeIdNull_skipPause() {
    CsSession session = new CsSession();
    session.setSessionId(10L);
    session.setRuntimeId(null);
    session.setStatus("ai");
    when(csSessionMapper.selectBySessionId(10L)).thenReturn(session);
    when(csSessionMapper.takeover(10L, 100L)).thenReturn(1);
    sessionService.takeover(10L, 100L);
    verify(agentRuntimeService, never()).pause(org.mockito.ArgumentMatchers.anyLong());
    verify(csSessionMapper).takeover(10L, 100L);
  }

  @Test
  @DisplayName("takeover：runtimeId 存在 → 调 pause + 更新状态")
  void takeover_runtimeIdExists_callsPause() {
    CsSession session = new CsSession();
    session.setSessionId(10L);
    session.setRuntimeId(200L);
    session.setStatus("ai");
    when(csSessionMapper.selectBySessionId(10L)).thenReturn(session);
    when(csSessionMapper.takeover(10L, 100L)).thenReturn(1);
    sessionService.takeover(10L, 100L);
    verify(agentRuntimeService).pause(200L);
    verify(csSessionMapper).takeover(10L, 100L);
  }

  @Test
  @DisplayName("takeover：pause 失败仍允许接管（避免阻塞客服）")
  void takeover_pauseFailed_stillUpdate() {
    CsSession session = new CsSession();
    session.setSessionId(10L);
    session.setRuntimeId(200L);
    session.setStatus("ai");
    when(csSessionMapper.selectBySessionId(10L)).thenReturn(session);
    doThrow(new RuntimeException("runtime 已结束")).when(agentRuntimeService).pause(200L);
    when(csSessionMapper.takeover(10L, 100L)).thenReturn(1);
    sessionService.takeover(10L, 100L);
    verify(csSessionMapper).takeover(10L, 100L);
  }

  @Test
  @DisplayName("createSession：已存在 → 跳过插入")
  void createSession_alreadyExists_skipInsert() {
    CsSession existing = new CsSession();
    existing.setSessionId(10L);
    when(csSessionMapper.selectBySessionId(10L)).thenReturn(existing);
    sessionService.createSession(10L, 200L);
    verify(csSessionMapper, never()).insert(org.mockito.ArgumentMatchers.<CsSession>any());
  }

  @Test
  @DisplayName("createSession：不存在 → 插入新记录")
  void createSession_notExists_insertNew() {
    when(csSessionMapper.selectBySessionId(10L)).thenReturn(null);
    sessionService.createSession(10L, 200L);
    verify(csSessionMapper).insert(org.mockito.ArgumentMatchers.<CsSession>any());
  }
}
