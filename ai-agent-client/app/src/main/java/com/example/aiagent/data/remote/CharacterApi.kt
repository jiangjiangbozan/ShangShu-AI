package com.example.aiagent.data.remote

import com.example.aiagent.data.model.CharacterCardCreateReq
import com.example.aiagent.data.model.CharacterCardResp
import com.example.aiagent.data.model.CharacterCardUpdateReq
import com.example.aiagent.core.network.ApiResult
import retrofit2.http.*

/**
 * 角色卡接口。
 *
 * 路径不带 api/v1 前缀：Retrofit 的 baseUrl 已包含该前缀，重复拼接会得到 /api/v1/api/v1/...。
 */
interface CharacterApi {

    @POST("character-cards")
    suspend fun createCharacterCard(@Body req: CharacterCardCreateReq): ApiResult<CharacterCardResp>

    @GET("character-cards")
    suspend fun listCharacterCards(): ApiResult<List<CharacterCardResp>>

    @GET("character-cards/public")
    suspend fun listPublicCharacterCards(@Query("keyword") keyword: String? = null): ApiResult<List<CharacterCardResp>>

    @GET("character-cards/{id}")
    suspend fun getCharacterCard(@Path("id") id: Long): ApiResult<CharacterCardResp>

    @PUT("character-cards/{id}")
    suspend fun updateCharacterCard(@Path("id") id: Long, @Body req: CharacterCardUpdateReq): ApiResult<CharacterCardResp>

    @DELETE("character-cards/{id}")
    suspend fun deleteCharacterCard(@Path("id") id: Long): ApiResult<Unit>

    @PUT("character-cards/{id}/default")
    suspend fun setDefault(@Path("id") id: Long): ApiResult<CharacterCardResp>

    @GET("character-cards/default")
    suspend fun getDefault(): ApiResult<CharacterCardResp>
}
