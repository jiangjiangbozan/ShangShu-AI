package com.example.aiagent.data.model

import kotlinx.serialization.Serializable

/**
 * 消息翻译请求：targetLang 留空时由服务端按语种推断（中文→English，其余→Chinese）。
 */
@Serializable
data class TranslateReq(
    val targetLang: String? = null,
)

/** 消息翻译响应（对应 POST /chat/messages/{id}/translate）。 */
@Serializable
data class TranslateResp(
    val messageId: Long = 0,
    val sourceLang: String = "",
    val targetLang: String = "",
    val translated: String = "",
)
