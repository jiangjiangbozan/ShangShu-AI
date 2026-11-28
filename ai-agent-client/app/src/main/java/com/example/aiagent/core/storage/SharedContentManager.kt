package com.example.aiagent.core.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 系统分享内容的进程内通道。
 *
 * 只承载文本和链接；文件 URI 仅作为文本占位，避免把外部 URI 误当成已上传文件。
 * 使用 StateFlow 让 Compose 在 Activity 收到新 intent 后重新读取一次。
 */
object SharedContentManager {

    private val mutableText = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = mutableText.asStateFlow()

    /** 读取后清空，防止旋转屏幕或重组时重复填充输入框。 */
    fun consume() {
        mutableText.value = null
    }

    /**  publish 外部文本，空白内容不覆盖既有分享。 */
    fun publish(value: String?) {
        if (!value.isNullOrBlank()) {
            mutableText.value = value
        }
    }
}
