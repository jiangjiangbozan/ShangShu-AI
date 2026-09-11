package com.example.aiagent.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.core.storage.AuthState
import com.example.aiagent.core.storage.SessionManager
import com.example.aiagent.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository(),
) : ViewModel() {

    /** 全局认证状态，驱动根导航在登录页与主界面间切换。 */
    val authState: StateFlow<AuthState> = SessionManager.authState

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(account: String, password: String) = submit {
        repository.login(account, password)
    }

    fun register(username: String, email: String, password: String) = submit {
        repository.register(username, email, password)
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { repository.logout() }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun submit(block: suspend () -> Unit) {
        if (_uiState.value.loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                block()
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    companion object {
        fun factory(repository: AuthRepository = AuthRepository()): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { AuthViewModel(repository) }
            }
    }
}