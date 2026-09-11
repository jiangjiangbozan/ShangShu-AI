package com.ai.api.identity.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.identity.entity.SystemUser;
import com.ai.api.identity.mapper.SystemUserMapper;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务
 *
 * <p>依据 design.md "业务层"：封装用户 CRUD、密码哈希、唯一性校验、登录失败计数等原子操作。
 *
 * <p>注意：所有方法均为事务性原子操作，调用方（AuthService）负责跨服务的编排。
 *
 * @author add-user-auth
 */
@Service
public class UserService {

  private final SystemUserMapper SystemUserMapper;
  private final PasswordEncoder passwordEncoder;

  public UserService(SystemUserMapper SystemUserMapper, PasswordEncoder passwordEncoder) {
    this.SystemUserMapper = SystemUserMapper;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * 注册新用户。
   *
   * <p>校验用户名与邮箱唯一性，密码通过 BCrypt 哈希后存储（BCrypt 哈希串内嵌随机盐）。
   *
   * @param username 用户名
   * @param email 邮箱
   * @param rawPassword 明文密码（仅在调用栈中存在，不持久化）
   * @return 已创建的用户实体（含 id）
   */
  @Transactional(rollbackFor = Exception.class)
  public SystemUser register(String username, String email, String rawPassword) {
    // 唯一性校验
    if (SystemUserMapper.selectByUsername(username) != null) {
      throw new BusinessException(ErrorCode.AUTH_USERNAME_EXISTS);
    }
    if (SystemUserMapper.selectByEmail(email) != null) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_EXISTS);
    }

    SystemUser user = new SystemUser();
    user.setUsername(username);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setStatus(1);
    user.setFailCount(0);
    SystemUserMapper.insert(user);
    return user;
  }

  /**
   * 按账号（用户名或邮箱）查询用户。
   *
   * @param account 用户名或邮箱
   * @return 用户实体，未找到返回 null
   */
  public SystemUser getByAccount(String account) {
    if (account.contains("@")) {
      return SystemUserMapper.selectByEmail(account);
    }
    return SystemUserMapper.selectByUsername(account);
  }

  /**
   * 按主键查询用户。
   *
   * @param id 用户 ID
   * @return 用户实体，未找到抛出 {@link BusinessException}
   */
  public SystemUser getById(Long id) {
    SystemUser user = SystemUserMapper.selectById(id);
    if (user == null) {
      throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND);
    }
    return user;
  }

  /**
   * 校验明文密码与哈希是否匹配。
   *
   * @param rawPassword 明文密码
   * @param user 用户实体
   * @return 匹配返回 true
   */
  public boolean checkPassword(String rawPassword, SystemUser user) {
    return passwordEncoder.matches(rawPassword, user.getPasswordHash());
  }

  /**
   * 更新用户密码（重置密码场景）。
   *
   * @param userId 用户 ID
   * @param newRawPassword 新明文密码
   */
  @Transactional(rollbackFor = Exception.class)
  public void updatePassword(Long userId, String newRawPassword) {
    SystemUser user = getById(userId);
    user.setPasswordHash(passwordEncoder.encode(newRawPassword));
    user.setFailCount(0);
    user.setLockUntil(null);
    SystemUserMapper.updateById(user);
  }

  /**
   * 登录成功：重置失败计数、更新最近登录时间。
   *
   * @param userId 用户 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void onLoginSuccess(Long userId) {
    SystemUser user = getById(userId);
    user.setFailCount(0);
    user.setLockUntil(null);
    user.setLastLoginAt(LocalDateTime.now());
    SystemUserMapper.updateById(user);
  }

  /**
   * 登录失败：自增失败次数，达到阈值时锁定账户。
   *
   * @param userId 用户 ID
   * @param maxAttempts 最大允许失败次数
   * @param lockDuration 锁定时长
   * @return 当前失败次数（用于判断是否本次触发锁定）
   */
  @Transactional(rollbackFor = Exception.class)
  public int onLoginFail(Long userId, int maxAttempts, java.time.Duration lockDuration) {
    SystemUser user = getById(userId);
    int newCount = (user.getFailCount() == null ? 0 : user.getFailCount()) + 1;
    user.setFailCount(newCount);
    if (newCount >= maxAttempts) {
      user.setLockUntil(LocalDateTime.now().plus(lockDuration));
    }
    SystemUserMapper.updateById(user);
    return newCount;
  }

  /**
   * 判断账户是否被锁定。
   *
   * @param user 用户实体
   * @return 锁定且未到期返回 true
   */
  public boolean isLocked(SystemUser user) {
    return user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now());
  }

  /**
   * 判断账户是否被禁用。
   *
   * @param user 用户实体
   * @return status=0 返回 true
   */
  public boolean isDisabled(SystemUser user) {
    return user.getStatus() != null && user.getStatus() == 0;
  }
}
