package com.example.aiagent.core.network

import kotlinx.serialization.Serializable

/**
 * 后端统一响应结构。
 *
 * 与 Web 端 `Result<T>` 约定一致：`code == 0` 表示业务成功，非 0 为失败。
 */
@Serializable
data class ApiResult<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
    val traceId: String? = null,
)

/**
 * 业务异常，携带后端业务码与提示信息。
 */
class ApiException(
    val code: Int,
    override val message: String,
) : Exception(message)