package com.example.aiagent.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aiagent.data.model.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "session")

/**
 * 令牌与用户会话的持久化存储（DataStore + 内存缓存）。
 *
 * 内存缓存用于同步的令牌读取（如 OkHttp 拦截器），DataStore 用于持久化，
 * 保证冷启动后可恢复登录态。
 *
 * 冷启动时从 DataStore 恢复 token 后会调用 /users/me 验证有效性，
 * 若 token 已过期/无效则自动清除登录态，确保不会以失效 token 进入主界面。
 */
object SessionManager {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val CSRF = stringPreferencesKey("csrf_token")
        val USER = stringPreferencesKey("user")
    }

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    @Volatile
    var csrfToken: String? = null
        private set

    @Volatile
    var user: UserInfo? = null
        private set

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * Token 验证回调，由 [com.example.aiagent.App] 注入。
     *
     * 冷启动恢复 token 后调用此回调验证有效性。
     * 回调应调用 /users/me 接口，成功返回 UserInfo，失败返回 null。
     */
    var tokenValidator: (suspend () -> UserInfo?)? = null

    fun init(context: Context) {
        dataStore = context.applicationContext.dataStore
        scope.launch {
            val prefs = dataStore.data
            val snapshot = prefs.first()
            val access = snapshot[Keys.ACCESS]
            if (!access.isNullOrBlank()) {
                accessToken = access
                refreshToken = snapshot[Keys.REFRESH]
                csrfToken = snapshot[Keys.CSRF]
                user = snapshot[Keys.USER]?.let { runCatching { json.decodeFromString<UserInfo>(it) }.getOrNull() }

                // 验证 token 是否仍然有效
                val validator = tokenValidator
                if (validator != null) {
                    val validUser = runCatching { validator() }.getOrNull()
                    if (validUser != null) {
                        user = validUser
                        persistUser(validUser)
                        _authState.value = AuthState.LoggedIn(validUser)
                    } else {
                        // Token 无效（过期/被吊销），清除登录态
                        clear()
                    }
                } else {
                    // 无验证器（不应发生），保持 LoggedIn 状态
                    _authState.value = AuthState.LoggedIn(user)
                }
            } else {
                _authState.value = AuthState.LoggedOut
            }
        }
    }

    fun saveTokens(
        access: String,
        refresh: String? = null,
        csrf: String? = null,
    ) {
        accessToken = access
        refresh?.let { refreshToken = it }
        csrf?.let { csrfToken = it }
        _authState.value = AuthState.LoggedIn(user)
        persist()
    }

    fun saveUser(userInfo: UserInfo?) {
        user = userInfo
        if (accessToken != null) {
            _authState.value = AuthState.LoggedIn(userInfo)
        }
        persist()
    }

    fun clear() {
        accessToken = null
        refreshToken = null
        csrfToken = null
        user = null
        _authState.value = AuthState.LoggedOut
        scope.launch {
            dataStore.edit { it.clear() }
        }
    }

    fun isLoggedIn(): Boolean = !accessToken.isNullOrBlank()

    private fun persist() {
        val access = accessToken
        val refresh = refreshToken
        val csrf = csrfToken
        val userJson = user?.let { runCatching { json.encodeToString(UserInfo.serializer(), it) }.getOrNull() }
        scope.launch {
            dataStore.edit { prefs ->
                if (access != null) prefs[Keys.ACCESS] = access else prefs.remove(Keys.ACCESS)
                if (refresh != null) prefs[Keys.REFRESH] = refresh else prefs.remove(Keys.REFRESH)
                if (csrf != null) prefs[Keys.CSRF] = csrf else prefs.remove(Keys.CSRF)
                if (userJson != null) prefs[Keys.USER] = userJson else prefs.remove(Keys.USER)
            }
        }
    }

    private fun persistUser(userInfo: UserInfo) {
        val userJson = runCatching { json.encodeToString(UserInfo.serializer(), userInfo) }.getOrNull()
        scope.launch {
            dataStore.edit { prefs ->
                if (userJson != null) prefs[Keys.USER] = userJson else prefs.remove(Keys.USER)
            }
        }
    }
}

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val user: UserInfo?) : AuthState
}
