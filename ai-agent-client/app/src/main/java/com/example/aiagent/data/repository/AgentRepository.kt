package com.example.aiagent.data.repository

import com.example.aiagent.core.network.NetworkModule
import com.example.aiagent.core.network.safeApiCall
import com.example.aiagent.data.model.AgentRuntimeResp
import com.example.aiagent.data.model.AgentTaskPageResp
import com.example.aiagent.data.model.AgentTraceResp
import com.example.aiagent.data.model.SkillDefinitionResp
import com.example.aiagent.data.model.TraceMetricsResp
import com.example.aiagent.data.model.WorkflowDefinitionResp
import com.example.aiagent.data.remote.AgentApi

class AgentRepository(
    private val api: AgentApi = NetworkModule.api(),
) {

    // ============ Runtime ============

    suspend fun createRuntime(
        sessionId: Long? = null,
        agentConfigId: Long? = null,
        modelId: String? = null,
    ): Map<String, Any> = safeApiCall { api.createRuntime(sessionId, agentConfigId, modelId) }

    suspend fun startRuntime(id: Long) { safeApiCall<Unit> { api.startRuntime(id) } }

    suspend fun pauseRuntime(id: Long) { safeApiCall<Unit> { api.pauseRuntime(id) } }

    suspend fun resumeRuntime(id: Long) { safeApiCall<Unit> { api.resumeRuntime(id) } }

    suspend fun cancelRuntime(id: Long) { safeApiCall<Unit> { api.cancelRuntime(id) } }

    suspend fun getRuntime(id: Long): AgentRuntimeResp = safeApiCall { api.getRuntime(id) }

    suspend fun listTasks(
        page: Int,
        size: Int,
        status: String? = null,
    ): AgentTaskPageResp = safeApiCall { api.listTasks(page, size, status) }

    // ============ Skills ============

    suspend fun listSkills(): List<SkillDefinitionResp> = safeApiCall { api.listSkills() }

    suspend fun listAllSkills(): List<SkillDefinitionResp> = safeApiCall { api.listAllSkills() }

    suspend fun setSkillEnabled(name: String, enabled: Boolean) {
        safeApiCall<Unit> { api.setSkillEnabled(name, enabled) }
    }

    suspend fun getSkill(name: String): SkillDefinitionResp = safeApiCall { api.getSkill(name) }

    suspend fun invokeSkill(
        name: String,
        version: String,
        input: Map<String, Any>,
    ): Map<String, Any> = safeApiCall { api.invokeSkill(name, version, input) }

    // ============ Workflow ============

    suspend fun getWorkflow(
        workflowId: String,
        version: String = "latest",
    ): WorkflowDefinitionResp = safeApiCall { api.getWorkflow(workflowId, version) }

    suspend fun executeWorkflow(
        workflowId: String,
        input: Map<String, Any>,
        version: String = "latest",
    ): Map<String, Any> = safeApiCall { api.executeWorkflow(workflowId, input, version) }

    // ============ Trace ============

    suspend fun replayTrace(runtimeId: Long): List<AgentTraceResp> =
        safeApiCall { api.replayTrace(runtimeId) }

    suspend fun traceMetrics(runtimeId: Long): TraceMetricsResp =
        safeApiCall { api.traceMetrics(runtimeId) }

    suspend fun listChildTraces(parentStepId: Long): List<AgentTraceResp> =
        safeApiCall { api.listChildTraces(parentStepId) }
}