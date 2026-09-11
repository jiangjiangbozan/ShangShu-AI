package com.ai.api.agent.pause;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Agent 暂停状态 Mapper（add-agent-runner-pipeline）。
 *
 * @author add-agent-runner-pipeline
 */
@Mapper
public interface PauseStateMapper extends BaseMapper<PauseState> {

  /** 查询 runtime 当前未处理的暂停状态（WAITING，按 id 倒序取最新）。 */
  @Select(
      "SELECT * FROM agent_pause_state WHERE runtime_id = #{runtimeId} "
          + "AND status = 'WAITING' ORDER BY id DESC LIMIT 1")
  PauseState selectActive(@Param("runtimeId") Long runtimeId);
}
