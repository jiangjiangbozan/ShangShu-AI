package com.example.aiagent.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.data.model.AgentRuntimeResp
import com.example.aiagent.data.model.AgentTaskResp
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
    val error: String? = null,
    val loading: Boolean = false,
    val runtime: AgentRuntimeResp? = null,
    val selectedRuntimeId: Long? = null, // 从任务列表选中，不再要求用户输入
    val selectedTab: Int = 0,
    val skills: List<SkillDefinitionResp> = emptyList(),
    val statusFilter: String = "",
    val tasks: List<AgentTaskResp> = emptyList(),
    val traceMetrics: TraceMetricsResp? = null,
    val traces: List<AgentTraceResp> = emptyList(),
)

class AgentViewModel(
    private val repository: AgentRepository = AgentRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    fun cancelRuntime() {
        val id = _uiState.value.selectedRuntimeId ?: return
        viewModelScope.launch {
            runCatching { repository.cancelRuntime(id) }
                .onSuccess { loadRuntime(); loadTasks() }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun createRuntime() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.createRuntime() }
                .onSuccess { data ->
                    val id = (data["runtimeId"] as? Number)?.toLong()
                    if (id != null) {
                        _uiState.update { it.copy(selectedRuntimeId = id, loading = false) }
                        loadRuntime()
                        loadTasks()
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun filterTasks(status: String) {
        _uiState.update { it.copy(statusFilter = status) }
        loadTasks()
    }

    fun loadRuntime() {
        val id = _uiState.value.selectedRuntimeId ?: return
        viewModelScope.launch {
            runCatching { repository.getRuntime(id) }
                .onSuccess { runtime -> _uiState.update { it.copy(runtime = runtime) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun loadSkills() {
        viewModelScope.launch {
            runCatching { repository.listSkills() }
                .onSuccess { skills -> _uiState.update { it.copy(skills = skills) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun loadTasks() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                repository.listTasks(DEFAULT_TASK_PAGE, TASK_PAGE_SIZE, _uiState.value.statusFilter)
            }
                .onSuccess { page ->
                    _uiState.update { it.copy(tasks = page.list, loading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun loadTrace(runtimeId: Long) {
        viewModelScope.launch {
            runCatching { repository.replayTrace(runtimeId) }
                .onSuccess { traces -> _uiState.update { it.copy(traces = traces) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun loadTraceMetrics(runtimeId: Long) {
        viewModelScope.launch {
            runCatching { repository.traceMetrics(runtimeId) }
                .onSuccess { metrics -> _uiState.update { it.copy(traceMetrics = metrics) } }
        }
    }

    fun pauseRuntime() {
        val id = _uiState.value.selectedRuntimeId ?: return
        viewModelScope.launch {
            runCatching { repository.pauseRuntime(id) }
                .onSuccess { loadRuntime(); loadTasks() }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun resumeRuntime() {
        val id = _uiState.value.selectedRuntimeId ?: return
        viewModelScope.launch {
            runCatching { repository.resumeRuntime(id) }
                .onSuccess { loadRuntime(); loadTasks() }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            TAB_SKILLS -> loadSkills()
            TAB_TRACE -> _uiState.value.selectedRuntimeId?.let {
                loadTrace(it)
                loadTraceMetrics(it)
            }
            else -> Unit
        }
    }

    fun selectTask(task: AgentTaskResp) {
        _uiState.update { it.copy(selectedRuntimeId = task.runtimeId) }
        loadRuntime()
        loadTrace(task.runtimeId)
        loadTraceMetrics(task.runtimeId)
    }

    fun startRuntime() {
        val id = _uiState.value.selectedRuntimeId ?: return
        viewModelScope.launch {
            runCatching { repository.startRuntime(id) }
                .onSuccess { loadRuntime(); loadTasks() }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    companion object {
        const val DEFAULT_TASK_PAGE = 1
        const val TAB_RUNTIME = 0
        const val TAB_SKILLS = 1
        const val TAB_TRACE = 2
        const val TASK_PAGE_SIZE = 20

        fun factory(
            repository: AgentRepository = AgentRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AgentViewModel(repository) }
        }
    }
}
