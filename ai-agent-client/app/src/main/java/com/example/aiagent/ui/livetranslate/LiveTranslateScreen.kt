package com.example.aiagent.ui.livetranslate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.ui.livetranslate.LiveTranslateViewModel.UiState

/**
 * 实时同传页：目标语言选择 + 麦克风开关 + 双语字幕流。
 *
 * 使用流程：进入页面授权麦克风 → 选择目标语言 → 点击麦克风开始 → 说话即出双语字幕 →
 * 点击停止（上游吐完剩余译文后自动收尾）。
 *
 * @author add-live-translate
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTranslateScreen(onBack: () -> Unit) {
    val viewModel: LiveTranslateViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.start()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("实时同传") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // 目标语言
            Text("翻译成", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LANGUAGE_OPTIONS.forEach { (code, label) ->
                    AssistChip(
                        onClick = { viewModel.updateLang(code) },
                        label = { Text(if (state.lang == code) "✓ $label" else label) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            state.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // 字幕流
            val listState = rememberLazyListState()
            LaunchedEffect(state.captions.size) {
                if (state.captions.isNotEmpty()) {
                    listState.animateScrollToItem(state.captions.size - 1)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.captions, key = { it.id }) { caption ->
                    CaptionCard(caption)
                }
                if (state.captions.isEmpty()) {
                    item {
                        Text(
                            when (state.phase) {
                                UiState.Phase.IDLE -> "选择语言后点击麦克风开始实时翻译"
                                UiState.Phase.CONNECTING -> "正在连接同传服务…"
                                UiState.Phase.RUNNING -> "正在聆听，请开始说话…"
                                UiState.Phase.FINISHING -> "正在收尾，等待剩余译文…"
                                UiState.Phase.FINISHED -> "本次同传已结束，可再次开始",
                            },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
            }

            // 底部控制条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "已采集 ${formatDuration(state.audioSeconds)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val running = state.phase == UiState.Phase.RUNNING ||
                    state.phase == UiState.Phase.CONNECTING ||
                    state.phase == UiState.Phase.FINISHING
                Button(
                    onClick = {
                        when {
                            running -> viewModel.stop()
                            !hasMicPermission -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            else -> viewModel.start()
                        }
                    },
                    colors = if (running) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Icon(
                        if (running) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (running) "停止" else "开始",
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(96.dp))
            }
        }
    }
}

@Composable
private fun CaptionCard(caption: LiveTranslateViewModel.Caption) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (caption.source.isNotBlank()) {
                Text(
                    caption.source,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (caption.translation.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    caption.translation,
                    fontSize = 15.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

private val LANGUAGE_OPTIONS = listOf(
    "en" to "英语",
    "zh" to "中文",
    "ja" to "日语",
    "ko" to "韩语",
)
