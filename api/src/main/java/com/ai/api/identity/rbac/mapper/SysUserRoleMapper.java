package com.ai.api.identity.rbac.mapper;

import com.ai.api.identity.rbac.entity.SysUserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 用户-角色关联 Mapper
 *
 * <p>依据《企业Java后端开发规范.md》：继承 {@link BaseMapper}，复杂查询写入 XML。
 *
 * @author add-rbac-management
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {}
