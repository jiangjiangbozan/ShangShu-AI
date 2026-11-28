package com.ai.api.cs.workbench;

import com.ai.api.agent.runtime.AgentRuntimeService;
import com.ai.api.common.exception.BadRequestException;
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
 * <p>提供会话列表、详情、人工接管能力。接管时调 {@link AgentRuntimeService#pause} 暂停 Agent 执行器。 mobile-p0 新增带 {@link
 * SessionScope} 的重载：过滤条件下推到 MyBatis-Plus 包装器与 Mapper SQL，不在内存过滤。
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

  /** 按 sessionId 查询详情（不限数据范围，供内部任务调用）。 */
  public CsSession getBySessionId(Long sessionId) {
    return csSessionMapper.selectBySessionId(sessionId);
  }

  /**
   * 按当前用户数据范围查询会话详情。
   *
   * @param sessionId 会话 ID
   * @param scope 当前用户数据范围
   * @return 有权限访问的会话，否则 null
   */
  public CsSession getBySessionId(Long sessionId, SessionScope scope) {
    LambdaQueryWrapper<CsSession> wrapper =
        new LambdaQueryWrapper<CsSession>().eq(CsSession::getSessionId, sessionId);
    applyDataScope(wrapper, scope);
    return csSessionMapper.selectOne(wrapper);
  }

  /**
   * 分页查询会话列表（不限数据范围，供内部任务调用）。
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

  /**
   * 按当前用户数据范围分页查询会话列表。
   *
   * @param status 状态过滤，null 不过滤
   * @param assignedTo 接管人过滤，null 不过滤；客服传入他人 ID 时结果为空
   * @param page 页码
   * @param size 每页大小
   * @param scope 当前用户数据范围
   */
  public IPage<CsSession> listSessions(
      String status, Long assignedTo, int page, int size, SessionScope scope) {
    Page<CsSession> p = new Page<>(page, size);
    LambdaQueryWrapper<CsSession> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isBlank()) {
      wrapper.eq(CsSession::getStatus, status);
    }
    if (assignedTo != null) {
      wrapper.eq(CsSession::getAssignedTo, assignedTo);
    }
    applyDataScope(wrapper, scope);
    wrapper.orderByDesc(CsSession::getUpdateTime);
    return csSessionMapper.selectPage(p, wrapper);
  }

  /**
   * 人工接管（不限数据范围，供内部任务调用）。
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
    pauseRuntime(session);
    csSessionMapper.takeover(sessionId, userId);
    log.info("[SessionService][takeover][sessionId({}) userId({})]", sessionId, userId);
  }

  /**
   * 按当前用户数据范围人工接管：仅无归属或本人负责的会话可被接管。
   *
   * @param sessionId 会话 ID
   * @param userId 接管人 ID
   * @param scope 当前用户数据范围
   */
  @Transactional(rollbackFor = Exception.class)
  public void takeover(Long sessionId, Long userId, SessionScope scope) {
    CsSession session = getBySessionId(sessionId, scope);
    if (session == null) {
      throw new BadRequestException("会话不存在或无权接管: " + sessionId);
    }
    pauseRuntime(session);
    if (csSessionMapper.takeoverScoped(sessionId, userId, scope.admin()) == 0) {
      throw new BadRequestException("会话已被其他客服接管，请刷新后重试");
    }
    log.info("[SessionService][takeover][sessionId({}) userId({})]", sessionId, userId);
  }

  /**
   * 下推数据范围条件：管理员放行，客服仅看自己负责或无归属的会话。
   *
   * @param wrapper 查询条件包装器
   * @param scope 当前用户数据范围
   */
  private void applyDataScope(LambdaQueryWrapper<CsSession> wrapper, SessionScope scope) {
    if (scope.admin()) {
      return;
    }
    wrapper.and(
        w -> w.eq(CsSession::getAssignedTo, scope.userId()).or().isNull(CsSession::getAssignedTo));
  }

  /**
   * 暂停 Agent 执行器；暂停失败不阻塞客服接管。
   *
   * @param session 会话（runtimeId 为空时跳过）
   */
  private void pauseRuntime(CsSession session) {
    if (session.getRuntimeId() == null) {
      return;
    }
    try {
      agentRuntimeService.pause(session.getRuntimeId());
    } catch (Exception e) {
      log.warn(
          "[SessionService][takeover][pause runtime failed][runtimeId({}) error({})]",
          session.getRuntimeId(),
          e.getMessage());
    }
  }
}
