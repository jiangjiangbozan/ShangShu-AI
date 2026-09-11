package com.example.aiagent.ui.dataanalysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.data.model.DataAskResp
import com.example.aiagent.data.model.DataReportReq
import com.example.aiagent.data.model.DataSchemaResp
import com.example.aiagent.data.repository.DataAnalysisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataAnalysisUiState(
    val loading: Boolean = false,
    val error: String? = null,
    /** 可分析数据表清单 */
    val schema: DataSchemaResp? = null,
    /** 问数问题输入 */
    val question: String = "",
    /** 问数结果 */
    val result: DataAskResp? = null,
    /** 报告生成中 */
    val reportLoading: Boolean = false,
    /** 生成的 Markdown 报告 */
    val report: String = "",
)

class DataAnalysisViewModel(
    private val repository: DataAnalysisRepository = DataAnalysisRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataAnalysisUiState())
    val uiState: StateFlow<DataAnalysisUiState> = _uiState.asStateFlow()

    init {
        loadSchema()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setQuestion(q: String) {
        _uiState.update { it.copy(question = q) }
    }

    fun loadSchema() {
        viewModelScope.launch {
            runCatching { repository.fetchSchema() }
                .onSuccess { schema ->
                    _uiState.update { it.copy(schema = schema) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    /** 自然语言问数 */
    fun ask() {
        val q = _uiState.value.question.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, report = "") }
            runCatching { repository.ask(q) }
                .onSuccess { result ->
                    _uiState.update { it.copy(result = result, loading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }

    /** 生成 Markdown 分析报告 */
    fun generateReport() {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(reportLoading = true, error = null) }
            runCatching {
                repository.report(
                    DataReportReq(
                        question = result.question,
                        sql = result.sql,
                        columns = result.columns,
                        rows = result.rows,
                    )
                )
            }
                .onSuccess { report ->
                    _uiState.update { it.copy(report = report, reportLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(reportLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer { DataAnalysisViewModel() }
        }
    }
}
