package com.ai.api.agent.mapper;

import com.ai.api.agent.entity.WorkflowDefinition;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 工作流定义 Mapper（add-agent-runtime）。
 *
 * @author add-agent-runtime
 */
@Mapper
public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinition> {

  /** 按业务 ID 查询最新版本。 */
  @Select(
      "SELECT * FROM workflow_definition WHERE workflow_id = #{workflowId} AND enabled = 1 "
          + "ORDER BY version DESC LIMIT 1")
  WorkflowDefinition selectLatestByWorkflowId(@Param("workflowId") String workflowId);

  /** 按业务 ID + 版本精确查询。 */
  @Select(
      "SELECT * FROM workflow_definition WHERE workflow_id = #{workflowId} "
          + "AND version = #{version} AND enabled = 1 LIMIT 1")
  WorkflowDefinition selectByWorkflowIdAndVersion(
      @Param("workflowId") String workflowId, @Param("version") String version);
}
