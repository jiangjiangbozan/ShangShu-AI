package com.ai.api.agent.mapper;

import com.ai.api.agent.entity.AgentRuntime;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Agent Runtime Mapper（add-agent-runtime）。
 *
 * @author add-agent-runtime
 */
@Mapper
public interface AgentRuntimeMapper extends BaseMapper<AgentRuntime> {

  /** 查询状态为 running 且 last_heartbeat 超 cutoff 的 runtime，用于断点续跑扫描。 */
  @Select(
      "SELECT * FROM agent_runtime WHERE status = 'running' AND last_heartbeat < #{cutoff} "
          + "ORDER BY last_heartbeat ASC LIMIT 100")
  List<AgentRuntime> selectOverdueRunning(@Param("cutoff") LocalDateTime cutoff);

  /** 更新心跳时间（用于 AgentRunner 每步执行后）。 */
  @Update("UPDATE agent_runtime SET last_heartbeat = NOW() WHERE id = #{id}")
  int touchHeartbeat(@Param("id") Long id);

  /** 更新状态（状态机转移）。 */
  @Update("UPDATE agent_runtime SET status = #{status}, update_time = NOW() WHERE id = #{id}")
  int updateStatus(@Param("id") Long id, @Param("status") String status);

  /** 更新上下文快照 + 心跳（每步落快照）。 */
  @Update(
      "UPDATE agent_runtime SET context_snapshot = #{snapshot}, next_step_index = #{stepIndex}, "
          + "last_heartbeat = NOW() WHERE id = #{id}")
  int updateContextSnapshot(
      @Param("id") Long id,
      @Param("snapshot") String snapshot,
      @Param("stepIndex") Integer stepIndex);

  /** 标记失败（含 error_message 与 dlq_message_id 关联）。 */
  @Update(
      "UPDATE agent_runtime SET status = 'failed', error_message = #{errorMsg}, "
          + "update_time = NOW() WHERE id = #{id}")
  int markFailed(@Param("id") Long id, @Param("errorMsg") String errorMsg);

  /** 标记完成 + 最终答案。 */
  @Update(
      "UPDATE agent_runtime SET status = 'done', final_answer = #{finalAnswer}, "
          + "update_time = NOW() WHERE id = #{id}")
  int markDone(@Param("id") Long id, @Param("finalAnswer") String finalAnswer);
}
