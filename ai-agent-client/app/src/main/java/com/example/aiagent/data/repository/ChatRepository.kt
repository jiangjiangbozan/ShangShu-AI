package com.example.aiagent.data.repository

import com.example.aiagent.BuildConfig
import com.example.aiagent.core.network.ApiException
import com.example.aiagent.core.network.NetworkModule
import com.example.aiagent.core.network.safeApiCall
import com.example.aiagent.data.model.ChatSearchHit
import com.example.aiagent.data.model.ChatSearchResult
import com.example.aiagent.data.model.CreateSessionReq
import com.example.aiagent.data.model.CursorPage
import com.example.aiagent.data.model.KnowledgeBaseResp
import com.example.aiagent.data.model.MessageResp
import com.example.aiagent.data.model.SendMessageReq
import com.example.aiagent.data.model.SessionResp
import com.example.aiagent.data.model.TokenStatsResp
import com.example.aiagent.data.model.TranslateReq
import com.example.aiagent.data.model.TranslateResp
import com.example.aiagent.data.remote.ChatApi
import com.example.aiagent.data.remote.KnowledgeApi
import com.example.aiagent.data.remote.TranslateApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/** SSE 流式事件。 */
sealed interface StreamEvent {
    data class Chunk(val text: String) : StreamEvent
    data class Done(val messageId: String, val latencyMs: Long) : StreamEvent
    data class Error(val message: String) : StreamEvent
    /** 网络搜索来源事件（webSearch 增强时先于消息块下发）。 */
    data class Search(val sources: List<String>) : StreamEvent
    data object Closed : StreamEvent
}

class ChatRepository(
    private val api: ChatApi = NetworkModule.api(),
    private val knowledgeApi: KnowledgeApi = NetworkModule.api(),
    private val translateApi: TranslateApi = NetworkModule.api(),
) {

    /**
     * 翻译单条消息：服务端按语种推断目标语言（中文→English，其余→Chinese）。
     *
     * @param messageId 消息 ID
     * @param targetLang 目标语言，留空自动推断
     */
    suspend fun translateMessage(messageId: Long, targetLang: String? = null): TranslateResp =
        safeApiCall { translateApi.translateMessage(messageId, TranslateReq(targetLang)) }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** 当前正在进行的 SSE 请求（用于手动停止流式输出）。 */
    private var activeCall: Call? = null

    // ============ 会话管理 ============

    /** 分页加载会话列表（游标分页：首屏不传 lastId，下一页传上一页最后一条 sessionId）。 */
    suspend fun listSessions(lastId: Long? = null, limit: Int = 20): CursorPage<SessionResp> =
        safeApiCall { api.listSessions(lastId, limit) }

    suspend fun createSession(modelId: String, title: String? = null): SessionResp =
        safeApiCall { api.createSession(CreateSessionReq(modelId, title)) }

    suspend fun deleteSession(id: Long) {
        val result = api.deleteSession(id)
        if (result.code != 0) throw ApiException(result.code, result.message)
    }

    /**
     * 分页加载会话消息（游标分页："先最新一屏，向上加载更早"）。
     * 首屏不传 beforeId 返回最新 limit 条（时间正序）；向上滚动加载更早时传最旧一条 messageId。
     */
    suspend fun getMessages(
        id: Long,
        beforeId: Long? = null,
        limit: Int = 50,
    ): CursorPage<MessageResp> =
        safeApiCall { api.getMessages(id, beforeId, limit) }

    // ============ Token 统计 ============

    /** 获取 Token 用量统计。 */
    suspend fun getTokenStats(period: String = "today"): TokenStatsResp =
        safeApiCall { api.getTokenStats(period) }

    // ============ 全局搜索 ============

    /** 全局搜索对话历史。 */
    suspend fun searchChatHistory(
        q: String,
        page: Int = 1,
        size: Int = 20,
        role: String? = null,
        sessionId: Long? = null,
        startTime: Long? = null,
        endTime: Long? = null,
    ): ChatSearchResult =
        safeApiCall { api.searchChatHistory(q, page, size, role, sessionId, startTime, endTime) }

    // ============ 知识库（RAG） ============

    /** 列出当前用户的知识库。 */
    suspend fun listKnowledgeBases(page: Int = 1, size: Int = 20): List<KnowledgeBaseResp> =
        safeApiCall { knowledgeApi.listKnowledgeBases(page, size) }

    // ============ SSE 流式消息 ============

    /** 停止当前正在进行的流式输出。 */
    fun stopStreaming() {
        activeCall?.cancel()
        activeCall = null
    }

    /** 通过 SSE 流式发送消息，并返回事件流。支持 RAG（kbId）与网络搜索（webSearch）增强。 */
    fun chatStream(
        sessionId: Long,
        content: String,
        kbId: Long? = null,
        webSearch: Boolean = false,
    ): Flow<StreamEvent> =
        callbackFlow {
            val json = NetworkModule.json
            val body = json.encodeToString(
                SendMessageReq.serializer(),
                SendMessageReq(content, webSearch),
            ).toRequestBody(jsonMediaType)

            val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
            val url = if (kbId != null) {
                "$baseUrl/chat/sessions/$sessionId/messages/stream?kbId=$kbId"
            } else {
                "$baseUrl/chat/sessions/$sessionId/messages/stream"
            }

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build()

            val call = NetworkModule.sseOkHttpClient.newCall(request)
            activeCall = call

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    activeCall = null
                    trySend(StreamEvent.Error(e.message ?: "连接失败"))
                    close()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            activeCall = null
                            trySend(StreamEvent.Error("服务响应异常（HTTP ${resp.code}）"))
                            close()
                            return
                        }
                        val source = resp.body?.source()
                        if (source == null) {
                            activeCall = null
                            trySend(StreamEvent.Error("响应为空"))
                            close()
                            return
                        }
                        var event = "message"
                        val data = StringBuilder()
                        var dataLineCount = 0
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.startsWith("event:") -> {
                                    event = line.substringAfter("event:").trim()
                                }

                                line.startsWith("data:") -> {
                                    // 与 Web 端 @microsoft/fetch-event-source 的 SSE 解析保持一致：
                                    // 第一节 data 行直接拼接，后续 data 行以 \n 连接（SSE 多行语义）。
                                    if (dataLineCount > 0) data.append('\n')
                                    // 仅剥离 SSE 规范中的一个可选前导空格，而非 trimStart()，
                                    // 否则会丢失流式文本中的有效空格，导致单词粘连。
                                    val raw = line.substringAfter("data:")
                                    data.append(if (raw.startsWith(" ")) raw.substring(1) else raw)
                                    dataLineCount++
                                }

                                line.isEmpty() -> {
                                    if (data.isNotEmpty()) {
                                        dispatch(event, data.toString())
                                        data.setLength(0)
                                        dataLineCount = 0
                                    }
                                    event = "message"
                                }
                            }
                        }
                        activeCall = null
                        trySend(StreamEvent.Closed)
                        close()
                    }
                }

                private fun dispatch(event: String, payload: String) {
                    when (event) {
                        "done" -> {
                            val obj = runCatching {
                                NetworkModule.json.decodeFromString<JsonObject>(payload)
                            }.getOrNull()
                            val messageId = obj?.get("messageId")?.jsonPrimitive?.content ?: ""
                            val latencyMs = obj?.get("latencyMs")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                            trySend(StreamEvent.Done(messageId, latencyMs))
                        }

                        "search" -> {
                            // 网络搜索来源事件：{"sources":["url1","url2",...]}
                            val obj = runCatching {
                                NetworkModule.json.decodeFromString<JsonObject>(payload)
                            }.getOrNull()
                            val sources = obj?.get("sources")
                                ?.let { it as? kotlinx.serialization.json.JsonArray }
                                ?.mapNotNull { el ->
                                    (el as? kotlinx.serialization.json.JsonPrimitive)?.content
                                }
                                ?: emptyList()
                            trySend(StreamEvent.Search(sources))
                        }

                        "error" -> {
                            val obj = runCatching {
                                NetworkModule.json.decodeFromString<JsonObject>(payload)
                            }.getOrNull()
                            val err = obj?.get("error")?.jsonPrimitive?.content
                                ?: obj?.get("message")?.jsonPrimitive?.content
                                ?: payload
                            trySend(StreamEvent.Error(err))
                        }

                        else -> trySend(StreamEvent.Chunk(payload))
                    }
                }
            })

            awaitClose {
                call.cancel()
                activeCall = null
            }
        }
}
