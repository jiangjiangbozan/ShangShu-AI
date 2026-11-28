package com.example.aiagent.data.remote

import com.example.aiagent.BuildConfig
import com.example.aiagent.core.network.NetworkModule
import com.example.aiagent.core.storage.SessionManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * 实时同传 WebSocket 客户端。
 *
 * 连接本服务 `/ws/live-translate`（token 走查询参数，百炼 API Key 由后端代理持有，
 * 客户端不接触上游协议细节）。事件回调按服务端事件 type 原样下发：
 *
 * - `session.updated`：会话配置已被上游接受，可以开始说话
 * - `conversation.item.input_audio_transcription.delta/.completed`：原文识别（免费 ASR）
 * - `response.text.delta`（mode=text）或 `response.audio_transcript.delta`（mode=audio）：译文增量
 * - `error` / `session.finished`：错误与会话收尾
 *
 * 上行事件：`input_audio_buffer.append`（Base64 PCM 16kHz 单声道，建议 100ms/片）与
 * `session.finish`；会话配置由服务端代管，客户端无需发送 `session.update`。
 *
 * @author add-live-translate
 */
class LiveTranslateClient(
    private val okHttpClient: OkHttpClient = NetworkModule.okHttpClient,
) : WebSocketListener() {

    /** 事件流：Pair(type, 完整事件 JSON 对象)。 */
    private val events =
        MutableSharedFlow<Pair<String, JsonObject>>(
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val eventFlow: SharedFlow<Pair<String, JsonObject>> = events

    private val json = Json { ignoreUnknownKeys = true }
    private var webSocket: WebSocket? = null

    /**
     * 建立同传连接。
     *
     * @param lang 翻译目标语言 short code（zh/en/ja/ko/fr/de/es/ru...）
     * @param audioOutput true 时同时返回译音（PCM 24kHz，字幕走 audio_transcript 事件）
     */
    fun connect(lang: String, audioOutput: Boolean) {
        val token = SessionManager.accessToken
            ?: throw IllegalStateException("未登录，无法使用实时同传")
        val mode = if (audioOutput) "audio" else "text"
        val url = "$wsEndpoint()?token=$token&lang=$lang&mode=$mode"
        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, this)
    }

    /** 上行一段 Base64 编码的 PCM 音频。 */
    fun appendAudio(base64Pcm: String) {
        val event = buildJsonObject {
            put("type", "input_audio_buffer.append")
            put("audio", base64Pcm)
        }
        send(event)
    }

    /** 通知服务端语音输入结束，上游将把剩余译文推完后回 session.finished。 */
    fun finish() {
        send(buildJsonObject { put("type", "session.finish") })
    }

    /** 主动断开。 */
    fun close() {
        webSocket?.close(1000, "client done")
        webSocket = null
    }

    private fun send(event: JsonObject) {
        webSocket?.send(json.encodeToString(JsonObject.serializer(), event))
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return
        val obj = element as? JsonObject ?: return
        val type = obj["type"]?.toString()?.trim('"') ?: return
        events.tryEmit(type to obj)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        events.tryEmit(
            "__failure" to buildJsonObject { put("message", t.message ?: "连接失败") }
        )
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        events.tryEmit(
            "__closed" to buildJsonObject {
                put("code", code)
                put("reason", reason)
            }
        )
    }

    /**
     * 由 Retrofit 的 API_BASE_URL 推导 WS 端点：
     * `http://host:port/api/v1/` → `ws://host:port/ws/live-translate`
     */
    private val wsEndpoint: String
        get() {
            val base = BuildConfig.API_BASE_URL.trim().trimEnd('/')
            val origin = if (base.endsWith("/api/v1")) base.removeSuffix("/api/v1") else base
            val wsOrigin = origin
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
            return "$wsOrigin/ws/live-translate"
        }
}
