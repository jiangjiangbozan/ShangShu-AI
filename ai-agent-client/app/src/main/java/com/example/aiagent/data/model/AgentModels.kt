package com.example.aiagent.data.model

import kotlinx.serialization.Serializable

/** AgentRuntime 响应 */
@Serializable
data class AgentRuntimeResp(
    val id: Long = 0,
    val sessionId: Long = 0,
    val agentConfigId: Long? = null,
    val modelId: String? = null,
    val userId: Long = 0,
    val status: String = "", // pending|running|paused|done|failed|cancelled
    val finalAnswer: String? = null,
    val errorMessage: String? = null,
    val nextStepIndex: Int = 0,
    val lastHeartbeat: String = "",
    val ttlSeconds: Int = 0,
    val createTime: String = "",
    val updateTime: String = "",
)

/** 用户任务分页响应 */
@Serializable
data class AgentTaskPageResp(
    val list: List<AgentTaskResp> = emptyList(),
    val total: Long = 0,
)

/** 用户任务列表项 */
@Serializable
data class AgentTaskResp(
    val agentConfigId: Long? = null,
    val createTime: String = "",
    val errorMessage: String? = null,
    val modelId: String? = null,
    val nextStepIndex: Int = 0,
    val runtimeId: Long = 0,
    val sessionId: Long? = null,
    val status: String = "",
    val strategy: String? = null,
    val updateTime: String = "",
)

/** Skill 元数据响应 */
@Serializable
data class SkillDefinitionResp(
    val id: Long = 0,
    val name: String = "",
    val version: String = "",
    val type: String = "", // api|local|kb|subagent|mcp
    val description: String = "",
    val endpoint: String? = null,
    val beanName: String? = null,
    val methodName: String? = null,
    val permissionTag: String? = null,
    val enabled: Int = 0,
    val inputSchema: String? = null,
    val outputSchema: String? = null,
    val createTime: String = "",
    val updateTime: String = "",
)

/** Workflow 定义响应 */
@Serializable
data class WorkflowDefinitionResp(
    val id: Long = 0,
    val workflowId: String = "",
    val name: String = "",
    val version: String = "",
    val triggerType: String = "",
    val owner: String = "",
    val enabled: Int = 0,
    val dag: String = "", // JSON
    val createTime: String = "",
    val updateTime: String = "",
)

/** Trace 步骤响应 */
@Serializable
data class AgentTraceResp(
    val id: Long = 0,
    val traceId: String = "",
    val runtimeId: Long = 0,
    val stepIndex: Int = 0,
    val stepType: String = "", // thought|action|observation|resume|error
    val skillId: String? = null,
    val input: String? = null, // JSON
    val output: String? = null, // JSON
    val tokenIn: Long? = null,
    val tokenOut: Long? = null,
    val latencyMs: Long? = null,
    val parentStepId: Long? = null,
    val error: String? = null,
    val dlqMessageId: String? = null,
    val createTime: String = "",
)

/** 性能指标响应 */
@Serializable
data class TraceMetricsResp(
    val totalLatencyMs: Long = 0,
    val totalTokenIn: Long = 0,
    val totalTokenOut: Long = 0,
    val stepCount: Int = 0,
)