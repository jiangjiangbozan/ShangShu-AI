package com.example.aiagent.ui.agent

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.example.aiagent.data.model.AgentRuntimeResp
import com.example.aiagent.data.model.AgentTraceResp
import com.example.aiagent.data.model.SkillDefinitionResp
import com.example.aiagent.data.model.TraceMetricsResp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: AgentViewModel = viewModel(factory = AgentViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("任务 Agent", style = MaterialTheme.typography.headlineSmall)

            PrimaryTabRow(selectedTabIndex = state.selectedTab) {
                Tab(selected = state.selectedTab == 0, onClick = { viewModel.selectTab(0) },
                    text = { Text("Runtime") })
                Tab(selected = state.selectedTab == 1, onClick = { viewModel.selectTab(1) },
                    text = { Text("Skills") })
                Tab(selected = state.selectedTab == 2, onClick = { viewModel.selectTab(2) },
                    text = { Text("Trace") })
            }

            when (state.selectedTab) {
                0 -> RuntimePanel(state, viewModel)
                1 -> SkillsPanel(state)
                2 -> TracePanel(state, viewModel)
            }
        }
    }
}

@Composable
private fun RuntimePanel(state: AgentUiState, viewModel: AgentViewModel) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.runtimeIdInput?.toString() ?: "",
                onValueChange = { viewModel.setRuntimeIdInput(it.toLongOrNull()) },
                label = { Text("Runtime ID") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { viewModel.loadRuntime() }) { Text("加载") }
            TextButton(onClick = { viewModel.createRuntime() }) { Text("创建") }
        }
        Spacer(Modifier.height(16.dp))

        if (state.loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.runtime?.let { runtime ->
            RuntimeCard(runtime, viewModel)
        }
    }
}

@Composable
private fun RuntimeCard(runtime: AgentRuntimeResp, viewModel: AgentViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Runtime #${runtime.id}", fontWeight = FontWeight.Bold)
            Text("状态: ${statusLabel(runtime.status)}", fontSize = 14.sp)
            runtime.finalAnswer?.let {
                Text("回答: $it", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            runtime.errorMessage?.let {
                Text("错误: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.startRuntime() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("启动")
                }
                OutlinedButton(onClick = { viewModel.pauseRuntime() }) {
                    Icon(Icons.Default.Pause, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("暂停")
                }
                OutlinedButton(onClick = { viewModel.resumeRuntime() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("恢复")
                }
                OutlinedButton(onClick = { viewModel.cancelRuntime() }) {
                    Icon(Icons.Default.Stop, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("取消")
                }
            }
        }
    }
}

@Composable
private fun SkillsPanel(state: AgentUiState) {
    if (state.skills.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("暂无 Skill 数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
        items(state.skills) { skill ->
            SkillCard(skill)
        }
    }
}

@Composable
private fun SkillCard(skill: SkillDefinitionResp) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(skill.name, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("v${skill.version}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(skill.description, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Text("类型: ${skill.type}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TracePanel(state: AgentUiState, viewModel: AgentViewModel) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        state.traceMetrics?.let { MetricsCard(it) }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                state.runtimeIdInput?.let { id ->
                    viewModel.loadTrace(id)
                    viewModel.loadTraceMetrics(id)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Timeline, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp)); Text("加载 Trace")
        }
        Spacer(Modifier.height(8.dp))
        if (state.traces.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("请输入 Runtime ID 并加载 Trace", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // 步骤类型统计
            val typeCounts = state.traces.groupBy { it.stepType }.mapValues { it.value.size }
            if (typeCounts.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    typeCounts.forEach { (type, count) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "$type: $count",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            LazyColumn {
                items(state.traces) { trace ->
                    com.example.aiagent.ui.agent.components.TraceStepCard(trace)
                }
            }
        }
    }
}

@Composable
private fun MetricsCard(metrics: TraceMetricsResp) {
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
            MetricItem("总延迟", "${metrics.totalLatencyMs}ms")
            MetricItem("输入 Token", "${metrics.totalTokenIn}")
            MetricItem("输出 Token", "${metrics.totalTokenOut}")
            MetricItem("步骤数", "${metrics.stepCount}")
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TraceCard(trace: AgentTraceResp) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(modifier = Modifier.padding(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Step ${trace.stepIndex}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(trace.stepType, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                trace.input?.let { Text("输入: $it", fontSize = 11.sp, maxLines = 2) }
                trace.output?.let { Text("输出: $it", fontSize = 11.sp, maxLines = 2) }
                trace.error?.let { Text("错误: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
            }
            trace.latencyMs?.let { Text("${it}ms", fontSize = 11.sp) }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "pending" -> "等待中"
    "running" -> "运行中"
    "paused" -> "已暂停"
    "done" -> "已完成"
    "failed" -> "失败"
    "cancelled" -> "已取消"
    else -> status
}