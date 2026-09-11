package com.ai.api.cs.workbench;

import com.ai.api.agent.runtime.AgentRuntimeService;
import com.ai.api.cs.entity.CsSession;
import com.ai.api.cs.mapper.CsSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客服会话工作台 Service（add-customer-service）。
 *
 * <p>提供会话列表、详情、人工接管能力。接管时调 {@link AgentRuntimeService#pause} 暂停 Agent 执行器。
 *
 * @author add-customer-service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

  private final CsSessionMapper csSessionMapper;
  private final AgentRuntimeService agentRuntimeService;

  /**
   * 分页查询会话列表。
   *
   * @param status 状态过滤（ai/human/done/failed），null 不过滤
   * @param assignedTo 接管人过滤，null 不过滤
   * @param page 页码
   * @param size 每页大小
   */
  public IPage<CsSession> listSessions(String status, Long assignedTo, int page, int size) {
    Page<CsSession> p = new Page<>(page, size);
    LambdaQueryWrapper<CsSession> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isBlank()) {
      wrapper.eq(CsSession::getStatus, status);
    }
    if (assignedTo != null) {
      wrapper.eq(CsSession::getAssignedTo, assignedTo);
    }
    wrapper.orderByDesc(CsSession::getUpdateTime);
    return csSessionMapper.selectPage(p, wrapper);
  }

  /** 按 sessionId 查询详情。 */
  public CsSession getBySessionId(Long sessionId) {
    return csSessionMapper.selectBySessionId(sessionId);
  }

  /**
   * 人工接管：暂停 Agent 执行器 + 更新会话状态。
   *
   * @param sessionId 会话 ID
   * @param userId 接管人 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void takeover(Long sessionId, Long userId) {
    CsSession session = csSessionMapper.selectBySessionId(sessionId);
    if (session == null) {
      throw new IllegalArgumentException("session not found: " + sessionId);
    }
    if (session.getRuntimeId() != null) {
      try {
        agentRuntimeService.pause(session.getRuntimeId());
      } catch (Exception e) {
        log.warn(
            "[SessionService][takeover][pause runtime failed][runtimeId({}) error({})]",
            session.getRuntimeId(),
            e.getMessage());
        // 即使暂停失败也允许接管（避免阻塞客服）
      }
    }
    csSessionMapper.takeover(sessionId, userId);
    log.info("[SessionService][takeover][sessionId({}) userId({})]", sessionId, userId);
  }

  /**
   * 创建客服会话扩展记录（业务侧创建会话时调用）。
   *
   * @param sessionId 关联 chat_session ID
   * @param runtimeId 关联 runtime ID（可空）
   */
  public void createSession(Long sessionId, Long runtimeId) {
    CsSession existing = csSessionMapper.selectBySessionId(sessionId);
    if (existing != null) {
      return;
    }
    CsSession session = new CsSession();
    session.setSessionId(sessionId);
    session.setRuntimeId(runtimeId);
    session.setStatus("ai");
    csSessionMapper.insert(session);
  }
}
