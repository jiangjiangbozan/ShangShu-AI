package com.example.aiagent.ui.customerservice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.data.model.CsLabel
import com.example.aiagent.data.model.CsSession
import com.example.aiagent.data.model.CustomerServiceResp
import com.example.aiagent.data.model.KbHitStat
import com.example.aiagent.data.model.KbHitSummary
import com.example.aiagent.data.repository.CustomerServiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CsUiState(
    val loading: Boolean = false,
    val error: String? = null,
    /** 当前选中的 Tab: 0=客服编排, 1=会话管理, 2=KB统计 */
    val selectedTab: Int = 0,
    // -- 客服编排 --
    val runtimeId: Long = 0,
    val csQuery: String = "",
    val csResult: CustomerServiceResp? = null,
    val csProcessing: Boolean = false,
    // -- 会话管理 --
    val csSessions: List<CsSession> = emptyList(),
    val csSessionsTotal: Long = 0,
    val csLabels: List<CsLabel> = emptyList(),
    val showLabels: Boolean = false,
    val selectedSession: CsSession? = null,
    // -- KB 统计 --
    val kbHitStats: List<KbHitStat> = emptyList(),
    val kbHitSummary: KbHitSummary? = null,
    val statsDays: Int = 7,
)

class CustomerServiceViewModel(
    private val repository: CustomerServiceRepository = CustomerServiceRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CsUiState())
    val uiState: StateFlow<CsUiState> = _uiState.asStateFlow()

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            1 -> loadSessions()
            2 -> loadKbHitStats()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ============ 客服编排 ============

    fun setRuntimeId(id: Long) {
        _uiState.update { it.copy(runtimeId = id) }
    }

    fun setCsQuery(query: String) {
        _uiState.update { it.copy(csQuery = query) }
    }

    fun sendCsChat() {
        val runtimeId = _uiState.value.runtimeId
        val query = _uiState.value.csQuery.trim()
        if (runtimeId <= 0 || query.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(csProcessing = true, csResult = null, error = null) }
            runCatching { repository.chat(runtimeId, query) }
                .onSuccess { result ->
                    _uiState.update { it.copy(csResult = result, csProcessing = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(csProcessing = false, error = e.toUserMessage())
                    }
                }
        }
    }

    // ============ 会话管理 ============

    fun loadSessions(status: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { repository.listSessions(status = status) }
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            csSessions = list.list,
                            csSessionsTotal = list.total,
                            loading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun takeoverSession(sessionId: Long) {
        viewModelScope.launch {
            runCatching { repository.takeoverSession(sessionId) }
                .onSuccess { loadSessions() }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun loadLabels(session: CsSession) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedSession = session, showLabels = true) }
            runCatching { repository.listLabels(session.id) }
                .onSuccess { labels ->
                    _uiState.update { it.copy(csLabels = labels) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun dismissLabels() {
        _uiState.update { it.copy(showLabels = false, csLabels = emptyList(), selectedSession = null) }
    }

    // ============ KB 统计 ============

    fun setStatsDays(days: Int) {
        _uiState.update { it.copy(statsDays = days) }
        loadKbHitStats()
    }

    fun loadKbHitStats() {
        val days = _uiState.value.statsDays
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { repository.kbHitStats(days) }
                .onSuccess { stats ->
                    _uiState.update { it.copy(kbHitStats = stats, loading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
        viewModelScope.launch {
            runCatching { repository.kbHitSummary(days) }
                .onSuccess { summary ->
                    _uiState.update { it.copy(kbHitSummary = summary) }
                }
        }
    }

    companion object {
        fun factory(
            repository: CustomerServiceRepository = CustomerServiceRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CustomerServiceViewModel(repository) }
        }
    }
}