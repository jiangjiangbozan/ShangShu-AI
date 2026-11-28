package com.example.aiagent.data.remote

import com.example.aiagent.core.network.ApiResult
import com.example.aiagent.data.model.CreateKnowledgeBaseReq
import com.example.aiagent.data.model.KnowledgeBaseResp
import com.example.aiagent.data.model.KnowledgeDocumentDetailResp
import com.example.aiagent.data.model.KnowledgeDocumentResp
import com.example.aiagent.data.model.SearchReq
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface KnowledgeApi {

    /** 列出当前用户的知识库 */
    @GET("knowledge")
    suspend fun listKnowledgeBases(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): ApiResult<List<KnowledgeBaseResp>>

    /** 创建知识库 */
    @POST("knowledge")
    suspend fun createKnowledgeBase(@Body req: CreateKnowledgeBaseReq): ApiResult<KnowledgeBaseResp>

    /** 测试检索 */
    @POST("knowledge/search")
    suspend fun searchKnowledge(@Body req: SearchReq): ApiResult<List<String>>

    /** 列出指定知识库下的文档 */
    @GET("knowledge/{kbId}/documents")
    suspend fun listDocuments(
        @Path("kbId") kbId: Long,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("status") status: String? = null,
    ): ApiResult<List<KnowledgeDocumentResp>>

    /** 查询文档详情（含前 5 个分片预览） */
    @GET("knowledge/documents/{id}")
    suspend fun getDocumentDetail(@Path("id") id: Long): ApiResult<KnowledgeDocumentDetailResp>

    /** 上传文档到指定知识库 */
    @Multipart
    @POST("knowledge/{kbId}/upload")
    suspend fun uploadDocument(
        @Path("kbId") kbId: Long,
        @Part file: MultipartBody.Part,
    ): ApiResult<KnowledgeDocumentResp>

    /** 删除文档 */
    @DELETE("knowledge/documents/{id}")
    suspend fun deleteDocument(@Path("id") id: Long): ApiResult<Unit>

    /** 重新处理文档 */
    @POST("knowledge/documents/{id}/reprocess")
    suspend fun reprocessDocument(@Path("id") id: Long): ApiResult<KnowledgeDocumentResp>
}