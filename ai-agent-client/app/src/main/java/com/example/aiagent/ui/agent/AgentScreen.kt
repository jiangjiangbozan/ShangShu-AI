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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
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
import com.example.aiagent.data.model.AgentTaskResp
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

    LaunchedEffect(Unit) { viewModel.loadTasks() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("任务 Agent", style = MaterialTheme.typography.headlineSmall)

            PrimaryTabRow(selectedTabIndex = state.selectedTab) {
                Tab(selected = state.selectedTab == AgentViewModel.TAB_RUNTIME,
                    onClick = { viewModel.selectTab(AgentViewModel.TAB_RUNTIME) },
                    text = { Text("任务") })
                Tab(selected = state.selectedTab == AgentViewModel.TAB_SKILLS,
                    onClick = { viewModel.selectTab(AgentViewModel.TAB_SKILLS) },
                    text = { Text("Skills") })
                Tab(selected = state.selectedTab == AgentViewModel.TAB_TRACE,
                    onClick = { viewModel.selectTab(AgentViewModel.TAB_TRACE) },
                    text = { Text("执行日志") })
            }

            when (state.selectedTab) {
                AgentViewModel.TAB_RUNTIME -> TaskPanel(state, viewModel)
                AgentViewModel.TAB_SKILLS -> SkillsPanel(state)
                AgentViewModel.TAB_TRACE -> TracePanel(state, viewModel)
                else -> Unit
            }
        }
    }
}

@Composable
private fun TaskPanel(state: AgentUiState, viewModel: AgentViewModel) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { viewModel.createRuntime() }) { Text("新建任务") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { viewModel.loadTasks() }) { Text("刷新") }
        }
        TaskFilterRow(state, viewModel)
        if (state.loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (state.tasks.isEmpty()) {
            Text("暂无任务，点击“新建任务”开始", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.tasks, key = { it.runtimeId }) { task ->
                    TaskCard(task, state.selectedRuntimeId == task.runtimeId) {
                        viewModel.selectTask(task)
                    }
                }
            }
        }
        state.runtime?.let { RuntimeCard(it, viewModel) }
    }
}

@Composable
private fun TaskFilterRow(state: AgentUiState, viewModel: AgentViewModel) {
    val filters = listOf(
        "" to "全部",
        "pending" to "等待中",
        "running" to "运行中",
        "paused" to "已暂停",
        "done" to "已完成",
        "failed" to "失败",
        "cancelled" to "已取消",
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters) { (status, label) ->
            OutlinedButton(onClick = { viewModel.filterTasks(status) }) { Text(label) }
        }
    }
}

@Composable
private fun TaskCard(task: AgentTaskResp, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("任务 #${task.runtimeId}", fontWeight = FontWeight.SemiBold)
            Text("状态: ${statusLabel(task.status)}", fontSize = 13.sp)
            Text("当前步骤: ${task.nextStepIndex}", fontSize = 12.sp)
            task.errorMessage?.let {
                Text("错误: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
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
            Text("任务 #${runtime.id}", fontWeight = FontWeight.Bold)
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
                state.selectedRuntimeId?.let { id ->
                    viewModel.loadTrace(id)
                    viewModel.loadTraceMetrics(id)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Timeline, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp)); Text("刷新执行日志")
        }
        Spacer(Modifier.height(8.dp))
        if (state.traces.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("请先在任务页选择一个任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
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