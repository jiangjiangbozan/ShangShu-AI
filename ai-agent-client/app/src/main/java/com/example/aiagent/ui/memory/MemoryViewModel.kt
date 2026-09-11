package com.example.aiagent.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.data.model.MemoryCreateReq
import com.example.aiagent.data.model.MemoryResp
import com.example.aiagent.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryUiState(
    val memories: List<MemoryResp> = emptyList(),
    val searchResults: List<MemoryResp>? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val selectedCategory: String? = null,
    val searchQuery: String = "",
)

class MemoryViewModel(
    private val repository: MemoryRepository = MemoryRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    fun loadMemories(category: String? = null) {
        _uiState.update { it.copy(loading = true, error = null, selectedCategory = category) }
        viewModelScope.launch {
            runCatching { repository.listMemories(category) }
                .onSuccess { memories ->
                    _uiState.update { it.copy(memories = memories, loading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage(), loading = false) }
                }
        }
    }

    fun searchMemories(keyword: String) {
        _uiState.update { it.copy(searchQuery = keyword) }
        if (keyword.isBlank()) {
            _uiState.update { it.copy(searchResults = null) }
            return
        }
        viewModelScope.launch {
            runCatching { repository.searchMemories(keyword) }
                .onSuccess { results ->
                    _uiState.update { it.copy(searchResults = results) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun createMemory(content: String, category: String = "general", importance: Int = 0) {
        viewModelScope.launch {
            runCatching {
                repository.createMemory(MemoryCreateReq(category = category, content = content, importance = importance))
            }
                .onSuccess { memory ->
                    _uiState.update { it.copy(memories = listOf(memory) + it.memories) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteMemory(id) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            memories = state.memories.filterNot { it.id == id },
                            searchResults = state.searchResults?.filterNot { it.id == id },
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun factory(repository: MemoryRepository = MemoryRepository()): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { MemoryViewModel(repository) }
            }
    }
}
