package com.example.aiagent.ui.livetranslate

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagent.data.remote.LiveTranslateClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 实时同传 ViewModel：麦克风采集 → WS 上行 → 双语字幕状态。
 *
 * 采集参数与服务端约定一致：PCM 16kHz 16bit 单声道，100ms/片 Base64 上行。
 * 字幕分段与上游 VAD 片段对齐：原文识别 item 与译文 item 通过
 * conversation.item.created 的 previous_item_id 关联成一对。
 *
 * @author add-live-translate
 */
class LiveTranslateViewModel : ViewModel() {

    data class Caption(
        val id: String,
        val source: String,
        val translation: String,
    )

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val lang: String = "en",
        val captions: List<Caption> = emptyList(),
        val error: String? = null,
        val audioSeconds: Int = 0,
    ) {
        enum class Phase { IDLE, CONNECTING, RUNNING, FINISHING, FINISHED }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val client = LiveTranslateClient()

    private var record: AudioRecord? = null
    private var captureJob: Job? = null
    private var eventJob: Job? = null
    private var chunkCount = 0

    /** 原文 item → 分段草稿（VAD 片段内原文与译文最终合并进 captions）。 */
    private val sourceDrafts = LinkedHashMap<String, StringBuilder>()
    private val translationDrafts = LinkedHashMap<String, StringBuilder>()

    fun updateLang(lang: String) {
        if (_uiState.value.phase == UiState.Phase.IDLE) {
            _uiState.update { it.copy(lang = lang) }
        }
    }

    /** 启动同传：连 WS → 采集线程推流。调用前须已取得 RECORD_AUDIO 运行时权限。 */
    fun start() {
        if (_uiState.value.phase != UiState.Phase.IDLE && _uiState.value.phase != UiState.Phase.FINISHED) {
            return
        }
        resetDrafts()
        _uiState.update {
            it.copy(phase = UiState.Phase.CONNECTING, error = null, captions = emptyList(), audioSeconds = 0)
        }
        eventJob = viewModelScope.launch {
            client.eventFlow.collect { (type, event) -> handleEvent(type, event) }
        }
        runCatching {
            client.connect(_uiState.value.lang, audioOutput = false)
        }.onFailure { e ->
            _uiState.update { it.copy(phase = UiState.Phase.IDLE, error = e.message ?: "连接失败") }
        }
    }

    /** 停止：结束推流并等待上游吐完剩余译文后断开。 */
    fun stop() {
        if (_uiState.value.phase == UiState.Phase.IDLE) return
        _uiState.update { it.copy(phase = UiState.Phase.FINISHING) }
        runCatching { client.finish() }
        captureJob?.cancel()
        releaseRecord()
        viewModelScope.launch {
            delay(3000) // 留出接收 session.finished 与剩余字幕的时间
            client.close()
            _uiState.update { if (it.phase == UiState.Phase.FINISHING) it.copy(phase = UiState.Phase.FINISHED) else it }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        captureJob?.cancel()
        releaseRecord()
        runCatching { client.close() }
    }

    // ==================== 事件处理 ====================

    private fun handleEvent(type: String, event: JsonObject) {
        when (type) {
            "session.updated" -> {
                _uiState.update { it.copy(phase = UiState.Phase.RUNNING) }
                startCapture()
            }
            "conversation.item.created" -> {
                // 译文消息项创建：previous_item_id 即同段原文识别 item，据此配对成双语字幕
                val item = event["item"]?.jsonObject ?: return
                val role = item["role"]?.jsonPrimitive?.content ?: return
                val itemId = item["id"]?.jsonPrimitive?.content ?: return
                if (role == "assistant") {
                    val prevId = event["previous_item_id"]?.jsonPrimitive?.content
                        ?: "src-$itemId"
                    synchronized(draftLock) {
                        sourceDrafts.putIfAbsent(prevId, StringBuilder())
                        translationDrafts[itemId] = StringBuilder()
                    }
                    ensureCaption(sourceId = prevId, translationId = itemId)
                }
            }
            "conversation.item.input_audio_transcription.delta" -> {
                val itemId = event["item_id"]?.jsonPrimitive?.content ?: return
                val delta = event["delta"]?.jsonPrimitive?.content ?: return
                appendDraft(sourceId = itemId, translationId = null, sourceDelta = delta, translationDelta = null)
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val itemId = event["item_id"]?.jsonPrimitive?.content ?: return
                val transcript = event["transcript"]?.jsonPrimitive?.content ?: ""
                replaceDraft(itemId, transcript)
            }
            "response.text.delta", "response.audio_transcript.delta" -> {
                val itemId = event["item_id"]?.jsonPrimitive?.content ?: return
                val delta = event["delta"]?.jsonPrimitive?.content ?: return
                appendDraft(sourceId = null, translationId = itemId, sourceDelta = null, translationDelta = delta)
            }
            "response.text.done", "response.audio_transcript.done" -> {
                val itemId = event["item_id"]?.jsonPrimitive?.content ?: return
                val final = event["text"]?.jsonPrimitive?.content
                    ?: event["transcript"]?.jsonPrimitive?.content ?: ""
                replaceTranslationDraft(itemId, final)
            }
            "error" -> {
                val message = event["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: "上游错误"
                _uiState.update { it.copy(error = message) }
            }
            "session.finished" -> {
                _uiState.update { it.copy(phase = UiState.Phase.FINISHED) }
            }
            "__failure" -> {
                val message = event["message"]?.jsonPrimitive?.content ?: "连接失败"
                _uiState.update { it.copy(phase = UiState.Phase.IDLE, error = message) }
            }
            "__closed" -> {
                _uiState.update {
                    if (it.phase != UiState.Phase.FINISHED) it.copy(phase = UiState.Phase.FINISHED) else it
                }
            }
        }
    }

    // ==================== 字幕草稿（线程安全：事件在 OkHttp 线程回调） ====================

    private val draftLock = Any()

    private fun resetDrafts() {
        synchronized(draftLock) {
            sourceDrafts.clear()
            translationDrafts.clear()
        }
    }

    private fun ensureCaption(sourceId: String, translationId: String) {
        synchronized(draftLock) {
            translationToSource[translationId] = sourceId
        }
        _uiState.update { state ->
            if (state.captions.any { it.id == sourceId }) {
                state
            } else {
                val sourceText = synchronized(draftLock) { sourceDrafts[sourceId]?.toString() ?: "" }
                val translationText = synchronized(draftLock) { translationDrafts[translationId]?.toString() ?: "" }
                state.copy(captions = state.captions + Caption(sourceId, sourceText, translationText))
            }
        }
    }

    private fun appendDraft(sourceId: String?, translationId: String?, sourceDelta: String?, translationDelta: String?) {
        val captionId = sourceId ?: findCaptionIdByTranslationId(translationId) ?: translationId ?: return
        synchronized(draftLock) {
            if (sourceDelta != null && sourceId != null) {
                sourceDrafts.getOrPut(sourceId) { StringBuilder() }.append(sourceDelta)
            }
            if (translationDelta != null && translationId != null) {
                translationDrafts.getOrPut(translationId) { StringBuilder() }.append(translationDelta)
            }
        }
        _uiState.update { state ->
            val captions = if (state.captions.any { it.id == captionId }) {
                state.captions.map { caption ->
                    if (caption.id == captionId) {
                        caption.copy(
                            source = if (sourceId != null) synchronized(draftLock) { sourceDrafts[sourceId]?.toString() ?: caption.source } else caption.source,
                            translation = if (translationId != null) synchronized(draftLock) { translationDrafts[translationId]?.toString() ?: caption.translation } else caption.translation,
                        )
                    } else {
                        caption
                    }
                }
            } else {
                val sourceText = synchronized(draftLock) { sourceId?.let { sourceDrafts[it]?.toString() } ?: "" }
                val translationText = synchronized(draftLock) { translationId?.let { translationDrafts[it]?.toString() } ?: "" }
                state.captions + Caption(captionId, sourceText, translationText)
            }
            state.copy(captions = captions)
        }
    }

    private fun replaceDraft(sourceId: String, transcript: String) {
        synchronized(draftLock) {
            sourceDrafts[sourceId] = StringBuilder(transcript)
        }
        updateCaptionText(sourceId, source = transcript, translation = null)
    }

    private fun replaceTranslationDraft(translationId: String, text: String) {
        synchronized(draftLock) {
            translationDrafts[translationId] = StringBuilder(text)
        }
        val captionId = findCaptionIdByTranslationId(translationId) ?: translationId
        updateCaptionText(captionId, source = null, translation = text)
    }

    private fun updateCaptionText(captionId: String, source: String?, translation: String?) {
        _uiState.update { state ->
            state.copy(
                captions = state.captions.map { caption ->
                    when (caption.id) {
                        captionId -> caption.copy(
                            source = source ?: caption.source,
                            translation = translation ?: caption.translation,
                        )
                        else -> caption
                    }
                },
            )
        }
    }

    private fun findCaptionIdByTranslationId(translationId: String?): String? {
        if (translationId == null) return null
        synchronized(draftLock) {
            return translationToSource[translationId]
        }
    }

    private val translationToSource = HashMap<String, String>()

    // ==================== 音频采集 ====================

    @SuppressLint("MissingPermission") // 调用方负责运行时权限
    private fun startCapture() {
        if (captureJob?.isActive == true) return
        captureJob = viewModelScope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, CHUNK_BYTES * 4),
            )
            record = audioRecord
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                _uiState.update { it.copy(phase = UiState.Phase.IDLE, error = "麦克风初始化失败") }
                return@launch
            }
            audioRecord.startRecording()
            val buffer = ByteArray(CHUNK_BYTES)
            while (captureJob?.isActive == true) {
                val read = audioRecord.read(buffer, 0, CHUNK_BYTES)
                if (read <= 0) continue
                val base64 = Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP)
                runCatching { client.appendAudio(base64) }
                    .onFailure { _uiState.update { s -> s.copy(error = "音频上行失败：${it.message}") } }
                chunkCount++
                if (chunkCount % CHUNKS_PER_SECOND == 0) {
                    _uiState.update { it.copy(audioSeconds = chunkCount / CHUNKS_PER_SECOND) }
                }
            }
        }
    }

    private fun releaseRecord() {
        runCatching {
            record?.stop()
            record?.release()
        }
        record = null
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        /** 100ms @ 16kHz 16bit 单声道 = 3200 字节 */
        private const val CHUNK_BYTES = SAMPLE_RATE * 2 / 10
        private const val CHUNKS_PER_SECOND = 10
    }
}
