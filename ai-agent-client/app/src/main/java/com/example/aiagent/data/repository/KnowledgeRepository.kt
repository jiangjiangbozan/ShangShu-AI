package com.example.aiagent.data.repository

import com.example.aiagent.core.network.NetworkModule
import com.example.aiagent.core.network.safeApiCall
import com.example.aiagent.data.model.CreateKnowledgeBaseReq
import com.example.aiagent.data.model.KnowledgeBaseResp
import com.example.aiagent.data.model.KnowledgeDocumentDetailResp
import com.example.aiagent.data.model.KnowledgeDocumentResp
import com.example.aiagent.data.model.KnowledgeUploadFile
import com.example.aiagent.data.model.SearchReq
import com.example.aiagent.data.remote.KnowledgeApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class KnowledgeRepository(
    private val api: KnowledgeApi = NetworkModule.api(),
) {

    suspend fun listKnowledgeBases(page: Int = 1, size: Int = 20): List<KnowledgeBaseResp> =
        safeApiCall { api.listKnowledgeBases(page, size) }

    suspend fun createKnowledgeBase(req: CreateKnowledgeBaseReq): KnowledgeBaseResp =
        safeApiCall { api.createKnowledgeBase(req) }

    suspend fun searchKnowledge(req: SearchReq): List<String> =
        safeApiCall { api.searchKnowledge(req) }

    suspend fun listDocuments(
        kbId: Long,
        page: Int = 1,
        size: Int = 20,
        keyword: String? = null,
        status: String? = null,
    ): List<KnowledgeDocumentResp> =
        safeApiCall { api.listDocuments(kbId, page, size, keyword, status) }

    suspend fun getDocumentDetail(id: Long): KnowledgeDocumentDetailResp =
        safeApiCall { api.getDocumentDetail(id) }

    suspend fun uploadDocument(kbId: Long, file: KnowledgeUploadFile): KnowledgeDocumentResp {
        val mediaType = file.mimeType.toMediaTypeOrNull() ?: DEFAULT_MIME_TYPE.toMediaType()
        val body = file.bytes.toRequestBody(mediaType)
        val part = MultipartBody.Part.createFormData("file", file.name, body)
        return safeApiCall { api.uploadDocument(kbId, part) }
    }

    suspend fun deleteDocument(id: Long) { safeApiCall<Unit> { api.deleteDocument(id) } }

    suspend fun reprocessDocument(id: Long): KnowledgeDocumentResp =
        safeApiCall { api.reprocessDocument(id) }

    companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}