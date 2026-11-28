package com.example.aiagent.data.model

import kotlinx.serialization.Serializable

/** 待上传文档：由系统文件选择器读取后交给 Repository 构造 multipart。 */
data class KnowledgeUploadFile(
    val bytes: ByteArray,
    val mimeType: String,
    val name: String,
)

/** 知识库文档响应 */
@Serializable
data class KnowledgeDocumentResp(
    val id: Long = 0,
    val knowledgeBaseId: Long = 0,
    val fileName: String = "",
    val filePath: String = "",
    val fileType: String = "",
    val fileSize: Long = 0,
    val contentHash: String? = null,
    val chunkCount: Int = 0,
    val status: String = "", // pending|parsing|vectorizing|completed|failed
    val errorMessage: String? = null,
    val createTime: String = "",
    val updateTime: String = "",
)

/** 知识库分片响应 */
@Serializable
data class KnowledgeChunkResp(
    val id: Long = 0,
    val knowledgeBaseId: Long = 0,
    val documentId: Long = 0,
    val chunkIndex: Int = 0,
    val content: String = "",
    val tokenCount: Int = 0,
    val vectorId: String? = null,
)

/** 文档详情响应 */
@Serializable
data class KnowledgeDocumentDetailResp(
    val document: KnowledgeDocumentResp = KnowledgeDocumentResp(),
    val chunkPreview: List<KnowledgeChunkResp> = emptyList(),
)

/** 创建知识库请求 */
@Serializable
data class CreateKnowledgeBaseReq(
    val name: String,
    val description: String? = null,
)

/** 检索请求 */
@Serializable
data class SearchReq(
    val query: String,
    val topK: Int? = null,
)