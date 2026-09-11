package com.ai.api.cs.mapper;

import com.ai.api.cs.entity.CsDialogState;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客服对话状态 Mapper（add-customer-service）。
 *
 * @author add-customer-service
 */
@Mapper
public interface CsDialogStateMapper extends BaseMapper<CsDialogState> {

  /** 按 runtime_id 查询最新状态。 */
  @Select("SELECT * FROM cs_dialog_state WHERE runtime_id = #{runtimeId} ORDER BY id DESC LIMIT 1")
  CsDialogState selectByRuntimeId(@Param("runtimeId") Long runtimeId);

  /** 按 session_id 查询最新状态。 */
  @Select("SELECT * FROM cs_dialog_state WHERE session_id = #{sessionId} ORDER BY id DESC LIMIT 1")
  CsDialogState selectBySessionId(@Param("sessionId") Long sessionId);
}
