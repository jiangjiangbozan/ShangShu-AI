package com.example.aiagent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aiagent.core.storage.SharedContentManager
import com.example.aiagent.ui.navigation.AppRoot
import com.example.aiagent.ui.theme.AiAgentTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SharedContentManager.publish(extractSharedText(intent))
        setContent {
            AiAgentTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SharedContentManager.publish(extractSharedText(intent))
    }

    /**
     * 从系统分享 intent 中提取可直接放进输入框的内容。
     *
     * - EXTRA_TEXT：文本或网页链接，直接预填
     * - EXTRA_STREAM：文件 URI 仅作为占位文本，后续版本再接入上传链路
     */
    private fun extractSharedText(intent: Intent?): String? {
        val action = intent?.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return null
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            return text
        }
        val uris = if (action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            val parcelables: ArrayList<Parcelable>? =
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            parcelables?.filterIsInstance<Uri>()?.takeIf { it.isNotEmpty() }
        } else {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { listOf(it) }
        }
        return uris?.joinToString(separator = "\n") { it.toString() }
    }
}
