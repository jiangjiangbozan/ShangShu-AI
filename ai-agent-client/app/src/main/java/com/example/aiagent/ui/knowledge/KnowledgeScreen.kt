package com.example.aiagent.ui.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.data.model.KnowledgeBaseResp
import com.example.aiagent.data.model.KnowledgeDocumentResp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    viewModel: KnowledgeViewModel = viewModel(factory = KnowledgeViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("知识库 RAG", style = MaterialTheme.typography.headlineSmall)
                Row {
                    IconButton(onClick = { viewModel.loadKnowledgeBases() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { viewModel.showCreateDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "新建知识库")
                    }
                }
            }

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(8.dp))

            // 知识库列表
            if (state.knowledgeBases.isEmpty() && !state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无知识库，点击 + 创建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.knowledgeBases) { kb ->
                        KnowledgeBaseCard(
                            kb = kb,
                            isSelected = state.selectedKb?.id == kb.id,
                            onClick = { viewModel.selectKnowledgeBase(kb) },
                        )
                    }
                }
            }

            // 文档列表
            state.selectedKb?.let { kb ->
                Spacer(Modifier.height(8.dp))
                Text("文档列表 (${kb.name})", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                if (state.documents.isEmpty()) {
                    Text("暂无文档", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.weight(0.5f)) {
                        items(state.documents) { doc ->
                            DocumentCard(
                                doc = doc,
                                onDetail = { viewModel.showDocumentDetail(doc) },
                                onDelete = { viewModel.deleteDocument(doc.id) },
                                onReprocess = { viewModel.reprocessDocument(doc.id) },
                            )
                        }
                    }
                }
            }

            // 检索测试
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    label = { Text("检索测试") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.searchKnowledge() },
                    enabled = !state.searching,
                ) {
                    if (state.searching) CircularProgressIndicator(Modifier.size(16.dp))
                    else Icon(Icons.Default.Search, contentDescription = null)
                }
            }
            if (state.searchResults.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Card(Modifier.fillMaxWidth().height(100.dp)) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(state.searchResults) { result ->
                            Text(result, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }

    // 创建知识库弹窗
    if (state.showCreateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreateDialog() },
            title = { Text("新建知识库") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.newKbName,
                        onValueChange = { viewModel.setNewKbName(it) },
                        label = { Text("名称") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.newKbDesc,
                        onValueChange = { viewModel.setNewKbDesc(it) },
                        label = { Text("描述（可选）") },
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.createKnowledgeBase() }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCreateDialog() }) { Text("取消") }
            },
        )
    }

    // 文档详情弹窗
    if (state.showDocDetail && state.documentDetail != null) {
        val detail = state.documentDetail!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissDocDetail() },
            title = { Text(detail.document.fileName) },
            text = {
                Column {
                    Text("状态: ${statusLabel(detail.document.status)}", fontSize = 13.sp)
                    Text("分片数: ${detail.document.chunkCount}", fontSize = 13.sp)
                    Text("文件大小: ${detail.document.fileSize / 1024}KB", fontSize = 13.sp)
                    detail.document.errorMessage?.let {
                        Text("错误: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("分片预览:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    detail.chunkPreview.forEach { chunk ->
                        Text(
                            "[${chunk.chunkIndex}] ${chunk.content.take(100)}...",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDocDetail() }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun KnowledgeBaseCard(
    kb: KnowledgeBaseResp,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(kb.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "文档 ${kb.documentCount} | 分片 ${kb.chunkCount} | ${statusLabel(kb.status)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.Visibility,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DocumentCard(
    doc: KnowledgeDocumentResp,
    onDetail: () -> Unit,
    onDelete: () -> Unit,
    onReprocess: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.fileName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${statusLabel(doc.status)} | ${doc.fileSize / 1024}KB | ${doc.chunkCount} 分片",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDetail, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Visibility, contentDescription = "详情", Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除", Modifier.size(16.dp))
            }
            if (doc.status == "failed") {
                TextButton(onClick = onReprocess) { Text("重试", fontSize = 11.sp) }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "pending" -> "等待中"
    "parsing" -> "解析中"
    "vectorizing" -> "向量化中"
    "completed" -> "已完成"
    "failed" -> "失败"
    "active" -> "活跃"
    "inactive" -> "停用"
    else -> status
}