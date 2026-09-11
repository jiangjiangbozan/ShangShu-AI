package com.ai.api.identity.mapper;

import com.ai.api.identity.entity.SystemUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统用户 Mapper
 *
 * <p>继承 {@link BaseMapper} 获取单表 CRUD 能力，自定义查询通过 XML 实现。
 *
 * @author add-user-auth
 */
@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {

  /**
   * 按用户名查询未逻辑删除的用户（登录场景）。
   *
   * @param username 用户名
   * @return 用户实体，未找到返回 null
   */
  SystemUser selectByUsername(String username);

  /**
   * 按邮箱查询未逻辑删除的用户（登录场景）。
   *
   * @param email 邮箱
   * @return 用户实体，未找到返回 null
   */
  SystemUser selectByEmail(String email);

  /**
   * 递增用户令牌版本号（原子自增），使既有 JWT 立即失效。
   *
   * @param userId 用户 ID
   * @return 受影响行数
   */
  int incrementTokenVersion(@Param("userId") Long userId);
}
