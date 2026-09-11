package com.ai.api.agent.mapper;

import com.ai.api.agent.entity.AgentTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 任务步骤 Mapper（add-agent-runtime）。
 *
 * @author add-agent-runtime
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {}
