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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

/** 搜索历史最大保存条数（超出时淘汰最旧的记录）。 */
private const val MAX_HISTORY_SIZE = 20

/**
 * 搜索历史持久化管理器（DataStore）。
 *
 * 功能：
 * - 存储：搜索执行后记录关键词，去重置顶（重复搜索移到最前），上限 [MAX_HISTORY_SIZE] 条
 * - 展示：通过 [history] StateFlow 暴露给 UI，应用重启后自动恢复
 * - 删除：支持删除单条记录 [remove]
 * - 清空：一键清空全部历史 [clear]
 *
 * 存储格式：单个 key 存 JSON 字符串数组，读写均为原子操作，避免多条目 key 的部分写入问题。
 */
object SearchHistoryManager {

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())

    private lateinit var dataStore:
        androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val historyKey = stringPreferencesKey("search_history")

    /** 内存缓存（最新在前），避免频繁读 DataStore。 */
    private val cache = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = cache.asStateFlow()

    /** 在 Application.onCreate 中初始化（见 App.kt）。 */
    fun init(context: Context) {
        dataStore = context.applicationContext.searchHistoryDataStore
        scope.launch {
            val saved = runCatching {
                val raw = dataStore.data.first()[historyKey]
                raw?.let { json.decodeFromString(listSerializer, it) }
            }.getOrNull().orEmpty()
            cache.value = saved
        }
    }

    /**
     * 记录一次搜索。
     *
     * - 空白关键词忽略
     * - 已存在则移到最前（去重置顶）
     * - 超过 [MAX_HISTORY_SIZE] 时淘汰末尾最旧记录
     */
    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val updated = (listOf(trimmed) + cache.value.filterNot { it == trimmed })
            .take(MAX_HISTORY_SIZE)
        cache.value = updated
        persist(updated)
    }

    /** 删除单条历史记录。 */
    fun remove(query: String) {
        val updated = cache.value.filterNot { it == query }
        cache.value = updated
        persist(updated)
    }

    /** 清空全部搜索历史。 */
    fun clear() {
        cache.value = emptyList()
        scope.launch {
            dataStore.edit { it.remove(historyKey) }
        }
    }

    private fun persist(list: List<String>) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[historyKey] = json.encodeToString(listSerializer, list)
            }
        }
    }
}
