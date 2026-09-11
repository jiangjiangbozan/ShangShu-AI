package com.example.aiagent.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.data.model.CreateKnowledgeBaseReq
import com.example.aiagent.data.model.KnowledgeBaseResp
import com.example.aiagent.data.model.KnowledgeDocumentDetailResp
import com.example.aiagent.data.model.KnowledgeDocumentResp
import com.example.aiagent.data.model.SearchReq
import com.example.aiagent.data.repository.KnowledgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KnowledgeUiState(
    val loading: Boolean = false,
    val error: String? = null,
    /** 知识库列表 */
    val knowledgeBases: List<KnowledgeBaseResp> = emptyList(),
    /** 当前选中的知识库 */
    val selectedKb: KnowledgeBaseResp? = null,
    /** 文档列表 */
    val documents: List<KnowledgeDocumentResp> = emptyList(),
    /** 文档详情（含分片预览） */
    val documentDetail: KnowledgeDocumentDetailResp? = null,
    /** 检索结果 */
    val searchResults: List<String> = emptyList(),
    /** 检索关键词 */
    val searchQuery: String = "",
    /** 检索中 */
    val searching: Boolean = false,
    /** 创建知识库弹窗 */
    val showCreateDialog: Boolean = false,
    val newKbName: String = "",
    val newKbDesc: String = "",
    /** 文档详情弹窗 */
    val showDocDetail: Boolean = false,
    /** 上传中 */
    val uploading: Boolean = false,
)

class KnowledgeViewModel(
    private val repository: KnowledgeRepository = KnowledgeRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(KnowledgeUiState())
    val uiState: StateFlow<KnowledgeUiState> = _uiState.asStateFlow()

    init { loadKnowledgeBases() }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ============ 知识库 CRUD ============

    fun loadKnowledgeBases() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.listKnowledgeBases() }
                .onSuccess { kbs ->
                    val prevSelected = _uiState.value.selectedKb
                    val current = prevSelected?.let { s -> kbs.find { it.id == s.id } }
                    _uiState.update {
                        it.copy(
                            knowledgeBases = kbs,
                            selectedKb = current,
                            loading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun selectKnowledgeBase(kb: KnowledgeBaseResp) {
        _uiState.update { it.copy(selectedKb = kb, documents = emptyList()) }
        loadDocuments(kb.id)
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true, newKbName = "", newKbDesc = "") }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun setNewKbName(name: String) {
        _uiState.update { it.copy(newKbName = name) }
    }

    fun setNewKbDesc(desc: String) {
        _uiState.update { it.copy(newKbDesc = desc) }
    }

    fun createKnowledgeBase() {
        val name = _uiState.value.newKbName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                repository.createKnowledgeBase(
                    CreateKnowledgeBaseReq(name, _uiState.value.newKbDesc.ifBlank { null })
                )
            }
                .onSuccess { kb ->
                    _uiState.update {
                        it.copy(
                            showCreateDialog = false,
                            loading = false,
                            knowledgeBases = it.knowledgeBases + kb,
                            selectedKb = kb,
                        )
                    }
                    loadDocuments(kb.id)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    // ============ 文档管理 ============

    fun loadDocuments(kbId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { repository.listDocuments(kbId) }
                .onSuccess { docs ->
                    _uiState.update { it.copy(documents = docs, loading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun showDocumentDetail(doc: KnowledgeDocumentResp) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, showDocDetail = true) }
            runCatching { repository.getDocumentDetail(doc.id) }
                .onSuccess { detail ->
                    _uiState.update { it.copy(documentDetail = detail, loading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun dismissDocDetail() {
        _uiState.update { it.copy(showDocDetail = false, documentDetail = null) }
    }

    fun deleteDocument(docId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteDocument(docId) }
                .onSuccess { loadKnowledgeBases(); _uiState.value.selectedKb?.let { loadDocuments(it.id) } }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun reprocessDocument(docId: Long) {
        viewModelScope.launch {
            runCatching { repository.reprocessDocument(docId) }
                .onSuccess { _uiState.value.selectedKb?.let { loadDocuments(it.id) } }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    // ============ 检索测试 ============

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun searchKnowledge() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(searching = true, searchResults = emptyList()) }
            runCatching { repository.searchKnowledge(SearchReq(query)) }
                .onSuccess { results ->
                    _uiState.update { it.copy(searchResults = results, searching = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(searching = false, error = e.toUserMessage()) }
                }
        }
    }

    companion object {
        fun factory(
            repository: KnowledgeRepository = KnowledgeRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { KnowledgeViewModel(repository) }
        }
    }
}