package com.example.aiagent.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 消息操作回调
 */
data class MessageActions(
    val onCopy: () -> Unit = {},
    val onQuote: () -> Unit = {},
    val onRegenerate: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/**
 * 消息操作按钮组
 * 借鉴 Operit 的消息操作设计：复制、引用、重新生成、删除
 */
@Composable
fun MessageActionBar(
    message: com.example.aiagent.data.model.MessageResp,
    actions: MessageActions,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
) {
    val context = LocalContext.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 复制按钮
        IconButton(
            onClick = {
                copyToClipboard(context, message.content)
                actions.onCopy()
            },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "复制",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 引用按钮（仅对非流式消息可用）
        if (!isStreaming) {
            IconButton(
                onClick = actions.onQuote,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "引用",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 语音播报按钮（仅对 AI 消息可用）
        if (message.role == "assistant" && !isStreaming) {
            VoicePlayButton(
                text = message.content,
                modifier = Modifier.size(28.dp),
            )
        }

        // 重新生成按钮（仅对 AI 消息可用）
        if (message.role == "assistant" && !isStreaming) {
            IconButton(
                onClick = actions.onRegenerate,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "重新生成",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 删除按钮（仅对非流式消息可用）
        if (!isStreaming) {
            IconButton(
                onClick = actions.onDelete,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * 复制文本到剪贴板
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("消息内容", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
}
