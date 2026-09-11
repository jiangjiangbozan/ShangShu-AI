package com.example.aiagent.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Context.draftDataStore by preferencesDataStore(name = "chat_drafts")

/**
 * 会话草稿持久化管理器（DataStore）。
 *
 * 每个会话独立的草稿内容按 sessionId 隔离存储，应用退出/意外关闭后可恢复。
 * Key 格式：draft_{sessionId}，Value 为草稿文本。
 */
object DraftManager {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 内存缓存，避免频繁读 DataStore。Key: sessionId, Value: 草稿文本 */
    private val cache = MutableStateFlow<Map<Long, String>>(emptyMap())
    val drafts: StateFlow<Map<Long, String>> = cache.asStateFlow()

    fun init(context: Context) {
        dataStore = context.applicationContext.draftDataStore
        scope.launch {
            val snapshot = dataStore.data.first()
            val loaded = mutableMapOf<Long, String>()
            snapshot.asMap().forEach { (key, value) ->
                val name = key.name
                if (name.startsWith("draft_") && value is String) {
                    val sessionId = name.removePrefix("draft_").toLongOrNull()
                    if (sessionId != null) {
                        loaded[sessionId] = value
                    }
                }
            }
            cache.value = loaded
        }
    }

    /** 读取指定会话的草稿。 */
    fun getDraft(sessionId: Long): String = cache.value[sessionId] ?: ""

    /** 更新指定会话的草稿（写入内存缓存 + 异步持久化）。 */
    fun setDraft(sessionId: Long, text: String) {
        val newMap = cache.value.toMutableMap()
        newMap[sessionId] = text
        cache.value = newMap
        scope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("draft_$sessionId")] = text
            }
        }
    }

    /** 清空指定会话的草稿。 */
    fun clearDraft(sessionId: Long) {
        val newMap = cache.value.toMutableMap()
        newMap.remove(sessionId)
        cache.value = newMap
        scope.launch {
            dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("draft_$sessionId"))
            }
        }
    }

    /** 清空所有草稿（调试用/退出登录时）。 */
    fun clearAll() {
        cache.value = emptyMap()
        scope.launch {
            dataStore.edit { it.clear() }
        }
    }
}
