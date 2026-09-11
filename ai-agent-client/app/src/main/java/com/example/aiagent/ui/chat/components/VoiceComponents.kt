package com.example.aiagent.ui.chat.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.aiagent.core.network.toUserMessage
import com.example.aiagent.data.repository.VoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 语音录制按钮 - 按住说话，松开结束
 */
@Composable
fun VoiceRecordButton(
    onTranscriptionResult: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VoiceRepository() }

    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var recorder: MediaRecorder? by remember { mutableStateOf(null) }
    var recordingFile: File? by remember { mutableStateOf(null) }

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.webm")
            recordingFile = file

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.WEBM)
                    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.WEBM)
                    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
            }
            isRecording = true
        } catch (e: Exception) {
            onError("录音启动失败: ${e.message}")
        }
    }

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            onError("需要麦克风权限才能使用语音功能")
        }
    }

    fun stopRecordingAndTranscribe() {
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false
            isProcessing = true

            val file = recordingFile
            if (file != null && file.exists() && file.length() > 0) {
                scope.launch {
                    val bytes = file.readBytes()
                    file.delete()
                    runCatching { repository.speechToText(bytes) }
                        .onSuccess { result ->
                            val text = result["text"] ?: ""
                            if (text.isNotBlank()) {
                                onTranscriptionResult(text)
                            } else {
                                onError("未识别到语音内容")
                            }
                        }
                        .onFailure { e ->
                            onError("语音识别失败: ${e.toUserMessage()}")
                        }
                    isProcessing = false
                }
            } else {
                isProcessing = false
                onError("录音文件为空")
            }
        } catch (e: Exception) {
            isRecording = false
            isProcessing = false
            onError("停止录音失败: ${e.message}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.release()
            recordingFile?.delete()
        }
    }

    // 录音动画
    val pulseScale by animateFloatAsState(
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = if (isRecording) {
            infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            )
        } else {
            tween(200)
        },
        label = "pulse",
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isProcessing -> MaterialTheme.colorScheme.tertiary
            isRecording -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        label = "voiceBtnColor",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp).padding(8.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text("识别中...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            IconButton(
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        if (isRecording) {
                            stopRecordingAndTranscribe()
                        } else {
                            startRecording()
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(bgColor),
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "停止录音" else "按住说话",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (isRecording) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Text("录音中...", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * 语音播放按钮 - 点击播放 AI 回复的语音
 */
@Composable
fun VoicePlayButton(
    text: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VoiceRepository() }

    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var mediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    IconButton(
        onClick = {
            if (isPlaying) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                isPlaying = false
            } else {
                isLoading = true
                scope.launch {
                    try {
                        val audioBytes = withContext(Dispatchers.IO) {
                            repository.textToSpeech(text)
                        }
                        val tempFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                        FileOutputStream(tempFile).use { it.write(audioBytes) }

                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(tempFile.absolutePath)
                            prepare()
                            setOnCompletionListener {
                                it.release()
                                mediaPlayer = null
                                isPlaying = false
                                tempFile.delete()
                            }
                            start()
                        }
                        isPlaying = true
                        isLoading = false
                    } catch (e: Exception) {
                        isLoading = false
                    }
                }
            }
        },
        modifier = modifier.size(32.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (isPlaying) "停止播放" else "语音播报",
                tint = if (isPlaying) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}