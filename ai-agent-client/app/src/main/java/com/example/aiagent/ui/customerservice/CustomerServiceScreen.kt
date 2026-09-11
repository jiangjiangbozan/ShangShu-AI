package com.example.aiagent.ui.customerservice

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
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.data.model.CsSession
import com.example.aiagent.data.model.CustomerServiceResp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerServiceScreen(
    viewModel: CustomerServiceViewModel = viewModel(factory = CustomerServiceViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("智能客服", style = MaterialTheme.typography.headlineSmall)

            PrimaryTabRow(selectedTabIndex = state.selectedTab) {
                Tab(selected = state.selectedTab == 0, onClick = { viewModel.selectTab(0) },
                    text = { Text("编排") })
                Tab(selected = state.selectedTab == 1, onClick = { viewModel.selectTab(1) },
                    text = { Text("会话") })
                Tab(selected = state.selectedTab == 2, onClick = { viewModel.selectTab(2) },
                    text = { Text("统计") })
            }

            when (state.selectedTab) {
                0 -> OrchestrationPanel(state, viewModel)
                1 -> SessionsPanel(state, viewModel)
                2 -> StatsPanel(state, viewModel)
            }
        }
    }

    // 标注弹窗
    if (state.showLabels) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLabels() },
            title = { Text("会话标注") },
            text = {
                Column {
                    Text("会话 #${state.selectedSession?.id}", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    if (state.csLabels.isEmpty()) {
                        Text("暂无标注记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.csLabels.forEach { label ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("${labelTypeLabel(label.labelType)}: ${label.labelContent}", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissLabels() }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun OrchestrationPanel(state: CsUiState, viewModel: CustomerServiceViewModel) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        OutlinedTextField(
            value = state.runtimeId.toString(),
            onValueChange = { viewModel.setRuntimeId(it.toLongOrNull() ?: 0) },
            label = { Text("Runtime ID") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.csQuery,
                onValueChange = { viewModel.setCsQuery(it) },
                label = { Text("用户问题") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { viewModel.sendCsChat() },
                enabled = !state.csProcessing,
            ) {
                if (state.csProcessing) CircularProgressIndicator(Modifier.size(20.dp))
                else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
        Spacer(Modifier.height(16.dp))
        state.csResult?.let { CsResultCard(it) }
    }
}

@Composable
private fun CsResultCard(result: CustomerServiceResp) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("意图识别", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("意图: ${result.intent.intent}", fontSize = 13.sp)
            Text("置信度: ${"%.2f".format(result.intent.confidence)}", fontSize = 12.sp)
            if (result.intent.slotsNeeded.isNotEmpty()) {
                Text("所需槽位: ${result.intent.slotsNeeded.joinToString(", ")}", fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            result.kbAnswer?.let {
                Text("KB 回答", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(it, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }
            Text("槽位填充", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("完成: ${if (result.slots.isComplete) "是" else "否"}", fontSize = 12.sp)
            if (result.slots.filledSlots.isNotEmpty()) {
                Text("已填: ${result.slots.filledSlots.entries.joinToString { "${it.key}=${it.value}" }}", fontSize = 12.sp)
            }
            if (result.slots.missingSlots.isNotEmpty()) {
                Text("缺失: ${result.slots.missingSlots.joinToString(", ")}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SessionsPanel(state: CsUiState, viewModel: CustomerServiceViewModel) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text("共 ${state.csSessionsTotal} 个会话", fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (state.csSessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无客服会话", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(state.csSessions) { session ->
                    CsSessionCard(session, viewModel)
                }
            }
        }
    }
}

@Composable
private fun CsSessionCard(session: CsSession, viewModel: CustomerServiceViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("会话 #${session.id}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "状态: ${sessionStatusLabel(session.status)} | 会话: ${session.sessionId}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (session.status == "ai") {
                OutlinedButton(
                    onClick = { viewModel.takeoverSession(session.id) },
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Icon(Icons.Default.Handshake, contentDescription = null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp)); Text("接管", fontSize = 11.sp)
                }
            }
            IconButton(onClick = { viewModel.loadLabels(session) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "标注", Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StatsPanel(state: CsUiState, viewModel: CustomerServiceViewModel) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        state.kbHitSummary?.let { summary ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${summary.total}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("总请求", fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${summary.hit}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("命中", fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${"%.1f".format(summary.hitRate * 100)}%", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("命中率", fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("统计天数: ", fontSize = 13.sp)
            listOf(7, 14, 30).forEach { days ->
                TextButton(
                    onClick = { viewModel.setStatsDays(days) },
                ) {
                    Text(
                        "${days}天",
                        fontWeight = if (state.statsDays == days) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        if (state.kbHitStats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无统计数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(state.kbHitStats) { stat ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stat.date, fontSize = 13.sp)
                            Text("${stat.hit}/${stat.total}", fontSize = 13.sp)
                            Text(
                                "${"%.1f".format(stat.hitRate * 100)}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sessionStatusLabel(status: String): String = when (status) {
    "ai" -> "AI 处理中"
    "human" -> "人工接管"
    "done" -> "已完成"
    "failed" -> "失败"
    else -> status
}

private fun labelTypeLabel(type: String): String = when (type) {
    "positive" -> "正向"
    "negative" -> "负向"
    "neutral" -> "中性"
    else -> type
}