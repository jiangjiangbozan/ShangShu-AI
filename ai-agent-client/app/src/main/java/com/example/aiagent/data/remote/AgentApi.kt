package com.example.aiagent.data.remote

import com.example.aiagent.core.network.ApiResult
import com.example.aiagent.data.model.AgentRuntimeResp
import com.example.aiagent.data.model.AgentTaskPageResp
import com.example.aiagent.data.model.AgentTraceResp
import com.example.aiagent.data.model.SkillDefinitionResp
import com.example.aiagent.data.model.TraceMetricsResp
import com.example.aiagent.data.model.WorkflowDefinitionResp
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AgentApi {

    // ============ Runtime ============

    /** 创建 runtime */
    @POST("agent/runtime")
    suspend fun createRuntime(
        @Query("sessionId") sessionId: Long? = null,
        @Query("agentConfigId") agentConfigId: Long? = null,
        @Query("modelId") modelId: String? = null,
    ): ApiResult<Map<String, Any>>

    /** 启动 runtime */
    @POST("agent/runtime/{id}/start")
    suspend fun startRuntime(@Path("id") id: Long): ApiResult<Unit>

    /** 暂停 runtime */
    @POST("agent/runtime/{id}/pause")
    suspend fun pauseRuntime(@Path("id") id: Long): ApiResult<Unit>

    /** 恢复 runtime */
    @POST("agent/runtime/{id}/resume")
    suspend fun resumeRuntime(@Path("id") id: Long): ApiResult<Unit>

    /** 取消 runtime */
    @POST("agent/runtime/{id}/cancel")
    suspend fun cancelRuntime(@Path("id") id: Long): ApiResult<Unit>

    /** 查询 runtime 详情 */
    @GET("agent/runtime/{id}")
    suspend fun getRuntime(@Path("id") id: Long): ApiResult<AgentRuntimeResp>

    /** 分页查询当前用户的任务 */
    @GET("agent/tasks")
    suspend fun listTasks(
        @Query("page") page: Int = DEFAULT_TASK_PAGE,
        @Query("size") size: Int = DEFAULT_TASK_PAGE_SIZE,
        @Query("status") status: String? = null,
    ): ApiResult<AgentTaskPageResp>

    // ============ Skills ============

    /** 列出所有已启用的 Skill */
    @GET("agent/skills")
    suspend fun listSkills(): ApiResult<List<SkillDefinitionResp>>

    /** 查询 Skill 最新版本 */
    @GET("agent/skills/{name}")
    suspend fun getSkill(@Path("name") name: String): ApiResult<SkillDefinitionResp>

    /** 列出所有 Skill（含已禁用的） */
    @GET("agent/skills/all")
    suspend fun listAllSkills(): ApiResult<List<SkillDefinitionResp>>

    /** 启用/禁用 Skill */
    @PUT("agent/skills/{name}/enabled")
    suspend fun setSkillEnabled(
        @Path("name") name: String,
        @Query("enabled") enabled: Boolean,
    ): ApiResult<Unit>

    /** 调用 Skill */
    @POST("agent/skills/{name}/invoke")
    suspend fun invokeSkill(
        @Path("name") name: String,
        @Query("version") version: String,
        @Body input: Map<String, @JvmSuppressWildcards Any>,
    ): ApiResult<Map<String, Any>>

    // ============ Workflow ============

    /** 查询工作流详情 */
    @GET("agent/workflows/{workflowId}")
    suspend fun getWorkflow(
        @Path("workflowId") workflowId: String,
        @Query("version") version: String = "latest",
    ): ApiResult<WorkflowDefinitionResp>

    /** 执行工作流 */
    @POST("agent/workflows/{workflowId}/execute")
    suspend fun executeWorkflow(
        @Path("workflowId") workflowId: String,
        @Body input: Map<String, @JvmSuppressWildcards Any>,
        @Query("version") version: String = "latest",
    ): ApiResult<Map<String, Any>>

    // ============ Trace ============

    /** 回放 Trace */
    @GET("agent/trace/{runtimeId}/replay")
    suspend fun replayTrace(@Path("runtimeId") runtimeId: Long): ApiResult<List<AgentTraceResp>>

    /** 性能聚合指标 */
    @GET("agent/trace/{runtimeId}/metrics")
    suspend fun traceMetrics(@Path("runtimeId") runtimeId: Long): ApiResult<TraceMetricsResp>

    /** 查询子任务 trace */
    @GET("agent/trace/parent/{parentStepId}")
    suspend fun listChildTraces(@Path("parentStepId") parentStepId: Long): ApiResult<List<AgentTraceResp>>

    companion object {
        const val DEFAULT_TASK_PAGE = 1
        const val DEFAULT_TASK_PAGE_SIZE = 20
    }
}