package com.example.aiagent.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.aiagent.data.model.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// 旧版明文会话数据，仅用于启动时清除，避免令牌以明文继续留在设备上
private val Context.legacySessionDataStore by preferencesDataStore(name = "session")

/**
 * 令牌与用户会话的持久化存储（Keystore + 内存缓存）。
 *
 * 内存缓存用于同步的令牌读取（如 OkHttp 拦截器），EncryptedSharedPreferences 用于持久化，
 * 密钥由 Android Keystore 托管，避免 token/CSRF/用户信息明文落盘。
 *
 * 冷启动时恢复 token 后会调用 /users/me 验证有效性，
 * 若 token 已过期/无效则自动清除登录态，确保不会以失效 token 进入主界面。
 */
object SessionManager {

    private const val ENCRYPTED_PREFERENCES_NAME = "session_encrypted"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_CSRF = "csrf_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER = "user"

    @Volatile
    var accessToken: String? = null
        private set

    val authState: StateFlow<AuthState> by lazy { mutableAuthState.asStateFlow() }

    @Volatile
    var csrfToken: String? = null
        private set

    private lateinit var encryptedPreferences: SharedPreferences

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val mutableAuthState = MutableStateFlow<AuthState>(AuthState.Loading)

    @Volatile
    var refreshToken: String? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Token 验证回调，由 [com.example.aiagent.App] 注入。
     *
     * 冷启动恢复 token 后调用此回调验证有效性。
     * 回调应调用 /users/me 接口，成功返回 UserInfo，失败返回 null。
     */
    var tokenValidator: (suspend () -> UserInfo?)? = null

    @Volatile
    var user: UserInfo? = null
        private set

    fun clear() {
        accessToken = null
        csrfToken = null
        refreshToken = null
        user = null
        mutableAuthState.value = AuthState.LoggedOut
        encryptedPreferences.edit().clear().apply()
    }

    /** 清除旧版明文 DataStore 中的遗留会话，防止升级后令牌仍以明文存在。 */
    private suspend fun clearLegacySessionData(context: Context) {
        context.applicationContext.legacySessionDataStore.edit { it.clear() }
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun init(context: Context) {
        val applicationContext = context.applicationContext
        encryptedPreferences = createEncryptedPreferences(applicationContext)
        scope.launch {
            clearLegacySessionData(applicationContext)
            restoreSession()
        }
    }

    fun isLoggedIn(): Boolean = !accessToken.isNullOrBlank()

    private fun persist() {
        val userJson = user?.let { runCatching { json.encodeToString(UserInfo.serializer(), it) }.getOrNull() }
        encryptedPreferences.edit().apply {
            putString(KEY_ACCESS, accessToken)
            putString(KEY_CSRF, csrfToken)
            putString(KEY_REFRESH, refreshToken)
            putString(KEY_USER, userJson)
            apply()
        }
    }

    private fun persistUser(userInfo: UserInfo) {
        val userJson = runCatching { json.encodeToString(UserInfo.serializer(), userInfo) }.getOrNull()
        encryptedPreferences.edit().putString(KEY_USER, userJson).apply()
    }

    private suspend fun restoreSession() {
        val access = encryptedPreferences.getString(KEY_ACCESS, null)
        if (access.isNullOrBlank()) {
            mutableAuthState.value = AuthState.LoggedOut
            return
        }
        accessToken = access
        csrfToken = encryptedPreferences.getString(KEY_CSRF, null)
        refreshToken = encryptedPreferences.getString(KEY_REFRESH, null)
        user = encryptedPreferences.getString(KEY_USER, null)
            ?.let { runCatching { json.decodeFromString<UserInfo>(it) }.getOrNull() }

        val validator = tokenValidator
        if (validator == null) {
            mutableAuthState.value = AuthState.LoggedIn(user)
            return
        }
        val validUser = runCatching { validator() }.getOrNull()
        if (validUser != null) {
            user = validUser
            persistUser(validUser)
            mutableAuthState.value = AuthState.LoggedIn(validUser)
        } else {
            clear()
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
        mutableAuthState.value = AuthState.LoggedIn(user)
        persist()
    }

    fun saveUser(userInfo: UserInfo?) {
        user = userInfo
        if (accessToken != null) {
            mutableAuthState.value = AuthState.LoggedIn(userInfo)
        }
        persist()
    }
}

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val user: UserInfo?) : AuthState
}
