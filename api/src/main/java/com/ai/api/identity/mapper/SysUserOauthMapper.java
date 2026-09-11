package com.ai.api.identity.mapper;

import com.ai.api.identity.entity.SysUserOauth;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 第三方 OAuth 绑定 Mapper
 *
 * <p>继承 {@link BaseMapper} 获取单表 CRUD，绑定关系查询通过 XML 实现。
 *
 * @author add-oauth-gitee
 */
@Mapper
public interface SysUserOauthMapper extends BaseMapper<SysUserOauth> {

  /**
   * 按平台与平台用户唯一标识查询未逻辑删除的绑定记录。
   *
   * @param provider 平台标识
   * @param openId 平台用户唯一标识
   * @return 绑定实体，未找到返回 null
   */
  SysUserOauth selectByProviderAndOpenId(
      @Param("provider") String provider, @Param("openId") String openId);
}
