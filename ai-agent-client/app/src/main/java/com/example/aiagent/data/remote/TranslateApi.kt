package com.example.aiagent.data.remote

import com.example.aiagent.core.network.ApiResult
import com.example.aiagent.data.model.TranslateReq
import com.example.aiagent.data.model.TranslateResp
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 跨语言翻译接口：单条消息翻译走 qwen-mt-uni，服务端按语种自动推断目标语言。
 */
interface TranslateApi {

    @POST("chat/messages/{id}/translate")
    suspend fun translateMessage(
        @Path("id") messageId: Long,
        @Body req: TranslateReq = TranslateReq(),
    ): ApiResult<TranslateResp>
}
