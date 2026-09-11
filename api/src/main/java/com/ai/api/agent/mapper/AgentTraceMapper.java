package com.ai.api.agent.mapper;

import com.ai.api.agent.entity.AgentTrace;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Agent 全链路 Trace Mapper（add-agent-runtime）。
 *
 * @author add-agent-runtime
 */
@Mapper
public interface AgentTraceMapper extends BaseMapper<AgentTrace> {

  /** 按 step_index 升序返回指定 runtime 的所有 trace（同 step 内按 id 升序，保证重建顺序稳定）。 */
  @Select(
      "SELECT * FROM agent_trace WHERE runtime_id = #{runtimeId} "
          + "ORDER BY step_index ASC, id ASC")
  List<AgentTrace> selectByRuntimeId(@Param("runtimeId") Long runtimeId);

  /** 查询子任务 trace（parent_step_id 不为空）。 */
  @Select(
      "SELECT * FROM agent_trace WHERE parent_step_id = #{parentStepId} "
          + "ORDER BY step_index ASC")
  List<AgentTrace> selectByParentStepId(@Param("parentStepId") Long parentStepId);

  /** 聚合指定 runtime 的成本（latency / token）。 */
  @Select(
      "SELECT COALESCE(SUM(latency_ms),0) AS total_latency, "
          + "COALESCE(SUM(token_in),0) AS total_token_in, "
          + "COALESCE(SUM(token_out),0) AS total_token_out "
          + "FROM agent_trace WHERE runtime_id = #{runtimeId}")
  java.util.Map<String, Object> selectMetricsByRuntimeId(@Param("runtimeId") Long runtimeId);
}
