package com.example.aiagent.ui.skillmarket

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.data.model.SkillDefinitionResp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SkillMarketScreen(
    onNavigateBack: () -> Unit,
    viewModel: SkillMarketViewModel = viewModel(factory = SkillMarketViewModel.factory()),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.error) {
        uiState.error?.let { viewModel.clearError() }
    }

    // 技能详情对话框
    if (uiState.showDetailDialog && uiState.selectedSkill != null) {
        SkillDetailDialog(
            skill = uiState.selectedSkill!!,
            onDismiss = { viewModel.hideDetail() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("技能市场") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // 搜索框
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("搜索技能...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
            )

            // 类型筛选
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val types = listOf(
                    null to "全部",
                    "local" to "本地",
                    "api" to "API",
                    "kb" to "知识库",
                    "mcp" to "MCP",
                    "subagent" to "子Agent",
                )
                types.forEach { (type, label) ->
                    FilterChip(
                        selected = uiState.selectedType == type,
                        onClick = { viewModel.setSelectedType(type) },
                        label = { Text(label, fontSize = 12.sp) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 技能列表
            if (uiState.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredSkills.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无技能", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(uiState.filteredSkills, key = { it.id }) { skill ->
                        SkillMarketCard(
                            skill = skill,
                            onToggle = { viewModel.toggleSkillEnabled(skill) },
                            onDetail = { viewModel.showDetail(skill) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SkillMarketCard(
    skill: SkillDefinitionResp,
    onToggle: () -> Unit,
    onDetail: () -> Unit,
) {
    val isEnabled = skill.enabled == 1
    val typeIcon = when (skill.type) {
        "local" -> Icons.Default.Code
        "api" -> Icons.Default.Extension
        "kb" -> Icons.Default.Storage
        "mcp" -> Icons.Default.SmartToy
        "subagent" -> Icons.Default.SmartToy
        else -> Icons.Default.Extension
    }
    val typeColor = when (skill.type) {
        "local" -> MaterialTheme.colorScheme.primary
        "api" -> MaterialTheme.colorScheme.tertiary
        "kb" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    typeIcon,
                    contentDescription = null,
                    Modifier.size(28.dp),
                    tint = typeColor,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = skill.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "v${skill.version}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (skill.description.isNotBlank()) {
                        Text(
                            text = skill.description,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.size(width = 44.dp, height = 24.dp),
                    )
                    Text(
                        text = if (isEnabled) "已启用" else "已禁用",
                        fontSize = 10.sp,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 类型标签 + 详情按钮
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text(skill.type.uppercase(), fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp),
                    )
                    if (skill.beanName?.isNotBlank() == true) {
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(skill.beanName!!, fontSize = 10.sp) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                }
                TextButton(onClick = onDetail) {
                    Icon(Icons.Default.Info, contentDescription = null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("详情", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SkillDetailDialog(
    skill: SkillDefinitionResp,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skill.name, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "v${skill.version}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (skill.description.isNotBlank()) {
                    Text(skill.description, style = MaterialTheme.typography.bodyMedium)
                }
                Row {
                    Text("类型: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(skill.type.uppercase(), fontSize = 13.sp)
                }
                if (skill.endpoint?.isNotBlank() == true) {
                    Row {
                        Text("端点: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(skill.endpoint!!, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (skill.beanName?.isNotBlank() == true) {
                    Row {
                        Text("Bean: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${skill.beanName}#${skill.methodName}", fontSize = 12.sp)
                    }
                }
                if (skill.permissionTag?.isNotBlank() == true) {
                    Row {
                        Text("权限: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(skill.permissionTag!!, fontSize = 13.sp)
                    }
                }
                Row {
                    Text("状态: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (skill.enabled == 1) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("已启用", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    } else {
                        Text("已禁用", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
