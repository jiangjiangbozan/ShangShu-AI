package com.example.aiagent.ui.character

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.data.model.CharacterCardCreateReq
import com.example.aiagent.data.model.CharacterCardResp
import com.example.aiagent.data.model.CharacterCardUpdateReq

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterScreen(
    onNavigateBack: () -> Unit,
    viewModel: CharacterViewModel = viewModel(factory = CharacterViewModel.factory()),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<CharacterCardResp?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { viewModel.clearError() }
    }

    // 删除确认对话框
    deleteTarget?.let { card ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除角色卡") },
            text = { Text("确定要删除「${card.name}」吗？此操作不可撤销。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteCharacterCard(card.id)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    // 创建/编辑对话框
    if (uiState.showCreateDialog || uiState.editingCard != null) {
        CharacterCardEditorDialog(
            existingCard = uiState.editingCard,
            onDismiss = {
                viewModel.toggleCreateDialog(false)
                viewModel.setEditingCard(null)
            },
            onSave = { name, avatar, description, systemPrompt, openingStatement, tags, isPublic ->
                if (uiState.editingCard != null) {
                    viewModel.updateCharacterCard(
                        uiState.editingCard!!.id,
                        CharacterCardUpdateReq(
                            name = name, avatar = avatar, description = description,
                            systemPrompt = systemPrompt, openingStatement = openingStatement,
                            tags = tags, isPublic = isPublic,
                        )
                    )
                } else {
                    viewModel.createCharacterCard(
                        CharacterCardCreateReq(
                            name = name, avatar = avatar, description = description,
                            systemPrompt = systemPrompt, openingStatement = openingStatement,
                            tags = tags, isPublic = isPublic,
                        )
                    )
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色卡") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.showPublicTab) {
                FloatingActionButton(onClick = { viewModel.toggleCreateDialog(true) }) {
                    Icon(Icons.Default.Add, contentDescription = "创建角色卡")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tab 切换
            PrimaryTabRow(selectedTabIndex = if (uiState.showPublicTab) 1 else 0) {
                Tab(
                    selected = !uiState.showPublicTab,
                    onClick = { viewModel.togglePublicTab(false) },
                    text = { Text("我的角色") },
                )
                Tab(
                    selected = uiState.showPublicTab,
                    onClick = { viewModel.togglePublicTab(true) },
                    text = { Text("角色广场") },
                )
            }

            if (uiState.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val cards = if (uiState.showPublicTab) uiState.publicCards else uiState.myCards
                if (cards.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (uiState.showPublicTab) "暂无公开角色卡" else "还没有角色卡，点击 + 创建",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Spacer(Modifier.height(8.dp)) }
                        items(cards, key = { it.id }) { card ->
                            CharacterCardItem(
                                card = card,
                                isDefault = uiState.defaultCard?.id == card.id,
                                isOwner = !uiState.showPublicTab && (card.userId == 0L || true),
                                onSetDefault = { viewModel.setDefault(card.id) },
                                onEdit = { viewModel.setEditingCard(card) },
                                onDelete = { deleteTarget = card },
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCardItem(
    card: CharacterCardResp,
    isDefault: Boolean,
    isOwner: Boolean,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefault)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDefault) 4.dp else 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(
                            if (card.avatar.length <= 4 && card.avatar.isNotEmpty())
                                Modifier
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                    ) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            if (card.avatar.length <= 4 && card.avatar.isNotEmpty()) {
                                Text(card.avatar, fontSize = 24.sp)
                            } else {
                                Text(
                                    card.name.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = card.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isDefault) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (card.isPublic) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Public,
                                contentDescription = null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (card.description.isNotBlank()) {
                        Text(
                            text = card.description,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 标签
            if (card.tags.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    card.tags.split(",").filter { it.isNotBlank() }.take(5).forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(tag.trim(), fontSize = 11.sp) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                }
            }

            // 统计 & 操作
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "使用 ${card.useCount}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "点赞 ${card.likeCount}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    if (!isDefault && isOwner) {
                        TextButton(onClick = onSetDefault) {
                            Text("设为默认", fontSize = 12.sp)
                        }
                    }
                    if (isOwner) {
                        IconButton(onClick = onEdit, Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑", Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Delete, contentDescription = "删除",
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterCardEditorDialog(
    existingCard: CharacterCardResp?,
    onDismiss: () -> Unit,
    onSave: (name: String, avatar: String, description: String, systemPrompt: String, openingStatement: String, tags: String, isPublic: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(existingCard?.name ?: "") }
    var avatar by remember { mutableStateOf(existingCard?.avatar ?: "") }
    var description by remember { mutableStateOf(existingCard?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(existingCard?.systemPrompt ?: "") }
    var openingStatement by remember { mutableStateOf(existingCard?.openingStatement ?: "") }
    var tags by remember { mutableStateOf(existingCard?.tags ?: "") }
    var isPublic by remember { mutableStateOf(existingCard?.isPublic ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingCard != null) "编辑角色卡" else "创建角色卡") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("角色名称 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = avatar,
                    onValueChange = { avatar = it },
                    label = { Text("头像 (emoji 或 URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("角色描述") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("角色设定 *") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = openingStatement,
                    onValueChange = { openingStatement = it },
                    label = { Text("开场白") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("标签 (逗号分隔)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("公开到角色广场")
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        onSave(name, avatar, description, systemPrompt, openingStatement, tags, isPublic)
                    }
                },
                enabled = name.isNotBlank() && systemPrompt.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
