package com.example.aiagent.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * 统一 API 调用封装。
 *
 * - 业务码 401：自动刷新令牌并重放一次
 * - 业务码非 0：抛出 [ApiException]
 */
suspend fun <T> safeApiCall(block: suspend () -> ApiResult<T>): T {
    var result = block()
    if (result.code == 401) {
        val refreshed = withContext(Dispatchers.IO) { NetworkModule.refreshBlocking() }
        if (refreshed) {
            result = block()
        }
    }
    if (result.code != 0) {
        throw ApiException(result.code, result.message)
    }
    return result.data
        ?: throw ApiException(result.code, if (result.message.isBlank()) "响应数据为空" else result.message)
}

/** 将网络异常转为可展示给用户的中文提示。 */
fun Throwable.toUserMessage(): String = when (this) {
    is ApiException -> message.ifBlank { "请求失败（$code）" }
    is HttpException -> "网络请求失败（HTTP ${code()}）"
    is IOException -> "网络连接失败，请检查网络后重试"
    else -> message ?: "未知错误"
}