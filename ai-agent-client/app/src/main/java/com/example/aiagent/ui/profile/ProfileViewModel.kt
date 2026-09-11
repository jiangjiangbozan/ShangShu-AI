package com.example.aiagent.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val tokenPeriod: String = "today",
    val totalTokens: Long = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val sessionCount: Int = 0,
    val totalMessages: Int = 0,
    val showApiKey: Boolean = false,
)

class ProfileViewModel(
    private val chatRepository: ChatRepository = ChatRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val period = _uiState.value.tokenPeriod
                // 并行加载 Token 统计和会话统计（游标分页：首屏不传 lastId，limit 拉最新一屏）
                val stats = chatRepository.getTokenStats(period)
                val sessions = chatRepository.listSessions(limit = 100)

                _uiState.update {
                    it.copy(
                        loading = false,
                        totalTokens = stats.totalTokens,
                        promptTokens = stats.promptTokens,
                        completionTokens = stats.completionTokens,
                        sessionCount = sessions.data.size,
                        totalMessages = sessions.data.sumOf { s -> s.messageCount },
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun setPeriod(period: String) {
        _uiState.update { it.copy(tokenPeriod = period) }
        loadStats()
    }

    fun toggleApiKey() {
        _uiState.update { it.copy(showApiKey = !it.showApiKey) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileViewModel() as T
        }
    }
}