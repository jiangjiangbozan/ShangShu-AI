package com.example.aiagent.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.data.model.CharacterCardCreateReq
import com.example.aiagent.data.model.CharacterCardResp
import com.example.aiagent.data.model.CharacterCardUpdateReq
import com.example.aiagent.data.repository.CharacterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CharacterUiState(
    val myCards: List<CharacterCardResp> = emptyList(),
    val publicCards: List<CharacterCardResp> = emptyList(),
    val defaultCard: CharacterCardResp? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val editingCard: CharacterCardResp? = null,
    val searchQuery: String = "",
    val showPublicTab: Boolean = false,
)

class CharacterViewModel(
    private val repository: CharacterRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterUiState())
    val uiState: StateFlow<CharacterUiState> = _uiState.asStateFlow()

    init {
        loadCharacterCards()
    }

    fun loadCharacterCards() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { repository.listCharacterCards() }
                .onSuccess { cards ->
                    _uiState.update { it.copy(myCards = cards, loading = false) }
                    loadDefaultCard()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun loadPublicCards() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val keyword = _uiState.value.searchQuery.takeIf { it.isNotBlank() }
            runCatching { repository.listPublicCharacterCards(keyword) }
                .onSuccess { cards ->
                    _uiState.update { it.copy(publicCards = cards, loading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    private fun loadDefaultCard() {
        viewModelScope.launch {
            runCatching { repository.getDefault() }
                .onSuccess { card ->
                    _uiState.update { it.copy(defaultCard = card) }
                }
                .onFailure { /* ignore */ }
        }
    }

    fun createCharacterCard(req: CharacterCardCreateReq) {
        viewModelScope.launch {
            runCatching { repository.createCharacterCard(req) }
                .onSuccess {
                    _uiState.update { it.copy(showCreateDialog = false) }
                    loadCharacterCards()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun updateCharacterCard(id: Long, req: CharacterCardUpdateReq) {
        viewModelScope.launch {
            runCatching { repository.updateCharacterCard(id, req) }
                .onSuccess {
                    _uiState.update { it.copy(editingCard = null) }
                    loadCharacterCards()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun deleteCharacterCard(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteCharacterCard(id) }
                .onSuccess { loadCharacterCards() }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch {
            runCatching { repository.setDefault(id) }
                .onSuccess { card ->
                    _uiState.update { it.copy(defaultCard = card) }
                    loadCharacterCards()
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun toggleCreateDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateDialog = show) }
    }

    fun setEditingCard(card: CharacterCardResp?) {
        _uiState.update { it.copy(editingCard = card) }
    }

    fun togglePublicTab(show: Boolean) {
        _uiState.update { it.copy(showPublicTab = show) }
        if (show) loadPublicCards()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun factory(repository: CharacterRepository = CharacterRepository()): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CharacterViewModel(repository) as T
            }
    }
}