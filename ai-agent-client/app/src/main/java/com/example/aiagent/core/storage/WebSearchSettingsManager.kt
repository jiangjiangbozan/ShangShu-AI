package com.example.aiagent.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.webSearchDataStore by preferencesDataStore(name = "web_search_settings")

/**
 * 网络搜索增强开关持久化管理器（DataStore）。
 *
 * 开关状态通过 [enabled] StateFlow 暴露给 UI，应用重启后自动恢复；
 * 切换通过 [toggle] 原子写入 DataStore。
 */
object WebSearchSettingsManager {

    private val enabledKey = booleanPreferencesKey("web_search_enabled")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var dataStore:
        androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>

    /** 内存缓存，避免频繁读 DataStore。 */
    private val cache = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = cache.asStateFlow()

    /** 在 Application.onCreate 中初始化（见 App.kt）。 */
    fun init(context: Context) {
        dataStore = context.applicationContext.webSearchDataStore
        scope.launch {
            val saved = runCatching {
                dataStore.data.first()[enabledKey]
            }.getOrNull() ?: false
            cache.value = saved
        }
    }

    /** 切换开关并持久化。 */
    fun toggle() {
        val updated = !cache.value
        cache.value = updated
        scope.launch {
            dataStore.edit { it[enabledKey] = updated }
        }
    }
}
