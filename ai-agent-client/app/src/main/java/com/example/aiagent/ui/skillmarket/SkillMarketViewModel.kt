package com.example.aiagent.ui.skillmarket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.data.model.SkillDefinitionResp
import com.example.aiagent.data.repository.AgentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillMarketUiState(
    val allSkills: List<SkillDefinitionResp> = emptyList(),
    val filteredSkills: List<SkillDefinitionResp> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedType: String? = null,
    val showDetailDialog: Boolean = false,
    val selectedSkill: SkillDefinitionResp? = null,
)

class SkillMarketViewModel(
    private val repository: AgentRepository = AgentRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillMarketUiState())
    val uiState: StateFlow<SkillMarketUiState> = _uiState.asStateFlow()

    init {
        loadSkills()
    }

    fun loadSkills() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { repository.listAllSkills() }
                .onSuccess { skills ->
                    _uiState.update { it.copy(allSkills = skills, loading = false) }
                    applyFilter()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun toggleSkillEnabled(skill: SkillDefinitionResp) {
        viewModelScope.launch {
            val newEnabled = skill.enabled == 0
            runCatching { repository.setSkillEnabled(skill.name, newEnabled) }
                .onSuccess {
                    _uiState.update { state ->
                        val updated = state.allSkills.map {
                            if (it.name == skill.name) it.copy(enabled = if (newEnabled) 1 else 0) else it
                        }
                        state.copy(allSkills = updated)
                    }
                    applyFilter()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter()
    }

    fun setSelectedType(type: String?) {
        _uiState.update { it.copy(selectedType = type) }
        applyFilter()
    }

    fun showDetail(skill: SkillDefinitionResp) {
        _uiState.update { it.copy(showDetailDialog = true, selectedSkill = skill) }
    }

    fun hideDetail() {
        _uiState.update { it.copy(showDetailDialog = false, selectedSkill = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun applyFilter() {
        _uiState.update { state ->
            var filtered = state.allSkills
            if (state.searchQuery.isNotBlank()) {
                val q = state.searchQuery.lowercase()
                filtered = filtered.filter {
                    it.name.lowercase().contains(q) ||
                    it.description.lowercase().contains(q)
                }
            }
            if (state.selectedType != null) {
                filtered = filtered.filter { it.type == state.selectedType }
            }
            state.copy(filteredSkills = filtered)
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SkillMarketViewModel() as T
        }
    }
}