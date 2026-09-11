package com.example.aiagent.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.data.model.AgentRuntimeResp
import com.example.aiagent.data.model.AgentTraceResp
import com.example.aiagent.data.model.SkillDefinitionResp
import com.example.aiagent.data.model.TraceMetricsResp
import com.example.aiagent.data.repository.AgentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgentUiState(
    val loading: Boolean = false,
    val error: String? = null,
    /** 当前 Runtime ID 输入 */
    val runtimeIdInput: Long? = null,
    /** 当前 Runtime 详情 */
    val runtime: AgentRuntimeResp? = null,
    /** Skill 列表 */
    val skills: List<SkillDefinitionResp> = emptyList(),
    /** Trace 时间轴 */
    val traces: List<AgentTraceResp> = emptyList(),
    /** Trace 性能指标 */
    val traceMetrics: TraceMetricsResp? = null,
    /** 当前选中的 Tab: 0=Runtime, 1=Skills, 2=Trace */
    val selectedTab: Int = 0,
)

class AgentViewModel(
    private val repository: AgentRepository = AgentRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            1 -> loadSkills()
            2 -> {
                val id = _uiState.value.runtimeIdInput
                if (id != null) {
                    loadTrace(id)
                    loadTraceMetrics(id)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ============ Runtime ============

    fun setRuntimeIdInput(id: Long?) {
        _uiState.update { it.copy(runtimeIdInput = id) }
    }

    fun createRuntime() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.createRuntime() }
                .onSuccess { data ->
                    val id = (data["runtimeId"] as? Number)?.toLong()
                    if (id != null) {
                        _uiState.update { it.copy(runtimeIdInput = id, loading = false) }
                        loadRuntime()
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun loadRuntime() {
        val id = _uiState.value.runtimeIdInput ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.getRuntime(id) }
                .onSuccess { runtime ->
                    _uiState.update { it.copy(runtime = runtime, loading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun startRuntime() {
        val id = _uiState.value.runtimeIdInput ?: return
        viewModelScope.launch {
            runCatching { repository.startRuntime(id) }
                .onSuccess { loadRuntime() }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun pauseRuntime() {
        val id = _uiState.value.runtimeIdInput ?: return
        viewModelScope.launch {
            runCatching { repository.pauseRuntime(id) }
                .onSuccess { loadRuntime() }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun resumeRuntime() {
        val id = _uiState.value.runtimeIdInput ?: return
        viewModelScope.launch {
            runCatching { repository.resumeRuntime(id) }
                .onSuccess { loadRuntime() }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun cancelRuntime() {
        val id = _uiState.value.runtimeIdInput ?: return
        viewModelScope.launch {
            runCatching { repository.cancelRuntime(id) }
                .onSuccess { loadRuntime() }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    // ============ Skills ============

    fun loadSkills() {
        viewModelScope.launch {
            runCatching { repository.listSkills() }
                .onSuccess { skills ->
                    _uiState.update { it.copy(skills = skills) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    // ============ Trace ============

    fun loadTrace(runtimeId: Long) {
        viewModelScope.launch {
            runCatching { repository.replayTrace(runtimeId) }
                .onSuccess { traces ->
                    _uiState.update { it.copy(traces = traces) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun loadTraceMetrics(runtimeId: Long) {
        viewModelScope.launch {
            runCatching { repository.traceMetrics(runtimeId) }
                .onSuccess { metrics ->
                    _uiState.update { it.copy(traceMetrics = metrics) }
                }
        }
    }

    companion object {
        fun factory(
            repository: AgentRepository = AgentRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AgentViewModel(repository) }
        }
    }
}