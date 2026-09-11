package com.example.aiagent.ui.dataanalysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiagent.data.model.DataAskResp
import com.example.aiagent.data.model.DataSchemaResp
import com.example.aiagent.data.model.DataTableMeta
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.min

private val CHART_COLORS =
    listOf(
        Color(0xFF4C6FFF),
        Color(0xFF26C6DA),
        Color(0xFFFFB74D),
        Color(0xFF66BB6A),
        Color(0xFFEF5350),
        Color(0xFFAB47BC),
    )

private val SAMPLE_QUESTIONS =
    listOf(
        "近7天每天新增多少会话",
        "按供应商统计模型配置数量",
        "各角色对话消息数量占比",
        "各知识库的文档数对比",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataAnalysisScreen(
    viewModel: DataAnalysisViewModel = viewModel(factory = DataAnalysisViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("数据分析", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "用自然语言问数，自动生成 SQL 并可视化",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                InputCard(
                    question = state.question,
                    loading = state.loading,
                    onQuestionChange = { viewModel.setQuestion(it) },
                    onAsk = { viewModel.ask() },
                    onRefresh = { viewModel.loadSchema() },
                )
            }

            item {
                SchemaCard(schema = state.schema)
            }

            state.result?.let { result ->
                item {
                    ResultCard(
                        result = result,
                        report = state.report,
                        reportLoading = state.reportLoading,
                        onGenerateReport = { viewModel.generateReport() },
                        onCopy = {
                            clipboard.setPrimaryClip(ClipData.newPlainText("", state.report))
                            scope.launch { snackbarHostState.showSnackbar("报告已复制到剪贴板") }
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun InputCard(
    question: String,
    loading: Boolean,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("问数", fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新表清单")
                }
            }
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("例如：近7天每天新增多少会话？") },
                minLines = 2,
                maxLines = 4,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SAMPLE_QUESTIONS.forEach { q ->
                        TextButton(
                            onClick = { onQuestionChange(q) },
                            modifier = Modifier.padding(vertical = 0.dp),
                        ) {
                            Text(q, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                Button(onClick = onAsk, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("问数")
                }
            }
        }
    }
}

@Composable
private fun SchemaCard(schema: DataSchemaResp?) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "可分析数据表（${schema?.tables?.size ?: 0} 张）",
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展开")
                }
            }
            if (expanded) {
                schema?.tables?.forEach { table ->
                    TableMetaBlock(table)
                }
            }
        }
    }
}

@Composable
private fun TableMetaBlock(table: DataTableMeta) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(table.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(
                table.comment,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                table.columns.joinToString("、") { it.name },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun ResultCard(
    result: DataAskResp,
    report: String,
    reportLoading: Boolean,
    onGenerateReport: () -> Unit,
    onCopy: () -> Unit,
) {
    var showSql by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Q：${result.question}", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${result.chartType} · ${result.rows.size} 行 · ${result.latencyMs}ms",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = onGenerateReport, enabled = !reportLoading) {
                        if (reportLoading) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Text("生成报告")
                        }
                    }
                    if (report.isNotBlank()) {
                        IconButton(onClick = onCopy) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制报告")
                        }
                    }
                }
            }

            result.message?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }

            // 图表
            if (result.chartType != "table" && result.rows.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(result.chartTitle, fontWeight = FontWeight.SemiBold)
                ChartView(
                    chartType = result.chartType,
                    categories = result.rows.map { it[result.columns.firstOrNull().orEmpty()].cellText() },
                    seriesList = seriesOf(result),
                    seriesNames = seriesNamesOf(result),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // SQL 折叠
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showSql = !showSql }) {
                Text(if (showSql) "收起 SQL" else "查看生成的 SQL")
            }
            if (showSql) {
                Text(
                    result.sql,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp),
                    ).padding(12.dp),
                )
            }

            // 明细表
            Spacer(Modifier.height(8.dp))
            DataTable(result = result)
        }
    }

    // 报告
    if (report.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("分析报告", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Markdown(report)
            }
        }
    }
}

/** 图表系列数据（首列为分类轴，其余列为数值系列；仅一列时用首列作数值） */
private fun seriesOf(result: DataAskResp): List<List<Float>> {
    val cols = result.columns
    if (cols.isEmpty()) return emptyList()
    val xCol = cols[0]
    if (cols.size == 1) return listOf(result.rows.map { it[xCol].toFloatOr() })
    return cols.drop(1).map { col -> result.rows.map { it[col].toFloatOr() } }
}

private fun seriesNamesOf(result: DataAskResp): List<String> {
    val cols = result.columns
    if (cols.isEmpty()) return emptyList()
    return if (cols.size == 1) listOf(cols[0]) else cols.drop(1)
}

private fun JsonElement?.cellText(): String {
    if (this == null || this is JsonNull) return ""
    return (this as? JsonPrimitive)?.content ?: toString()
}

private fun JsonElement?.toFloatOr(): Float =
    (this as? JsonPrimitive)?.content?.toFloatOrNull() ?: 0f

@Composable
private fun ChartView(
    chartType: String,
    categories: List<String>,
    seriesList: List<List<Float>>,
    seriesNames: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        when (chartType) {
            "pie" -> PieChart(categories, seriesList.firstOrNull().orEmpty())
            else -> BarLineChart(chartType, categories, seriesList, seriesNames)
        }
    }
}

@Composable
private fun BarLineChart(
    type: String,
    categories: List<String>,
    seriesList: List<List<Float>>,
    seriesNames: List<String>,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.fillMaxWidth().height(240.dp)) {
        val padLeft = 8.dp.toPx()
        val padRight = 8.dp.toPx()
        val padTop = 16.dp.toPx()
        val padBottom = 30.dp.toPx()
        val w = size.width - padLeft - padRight
        val h = size.height - padTop - padBottom
        val maxValue = (seriesList.flatten().maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = if (categories.isEmpty()) w else w / categories.size
        val groupW = stepX * 0.7f
        val barW = if (seriesList.isEmpty()) groupW else groupW / seriesList.size
        val baseY = size.height - padBottom

        if (type == "bar") {
            for ((si, values) in seriesList.withIndex()) {
                val color = CHART_COLORS[si % CHART_COLORS.size]
                for ((ci, v) in values.withIndex()) {
                    val x = padLeft + ci * stepX + (stepX - groupW) / 2 + si * barW
                    val barH = (v / maxValue) * h
                    drawRect(
                        color = color,
                        topLeft = Offset(x, baseY - barH),
                        size = Size(barW, barH),
                    )
                }
            }
        } else {
            for ((si, values) in seriesList.withIndex()) {
                val color = CHART_COLORS[si % CHART_COLORS.size]
                val pts = values.mapIndexed { ci, v ->
                    Offset(padLeft + ci * stepX + stepX / 2, baseY - (v / maxValue) * h)
                }
                if (pts.size > 1) {
                    for (i in 0 until pts.size - 1) {
                        drawLine(color, pts[i], pts[i + 1], strokeWidth = 2.dp.toPx())
                    }
                }
                pts.forEach { p ->
                    drawCircle(color, radius = 3.dp.toPx(), center = p)
                }
            }
        }

        // 分类轴标签
        for ((ci, cat) in categories.withIndex()) {
            val x = padLeft + ci * stepX + stepX / 2
            val label = cat.take(8)
            val layout = textMeasurer.measure(
                AnnotatedString(label),
                style = TextStyle(fontSize = 10.sp, color = Color.Gray),
            )
            val tw = layout.size.width.toFloat()
            val labelX = (x - tw / 2).coerceIn(padLeft, size.width - padRight - tw)
            drawText(layout, topLeft = Offset(labelX, baseY + 4.dp.toPx()))
        }
    }
    ChartLegend(seriesNames)
}

@Composable
private fun PieChart(categories: List<String>, values: List<Float>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        val total = values.sum().coerceAtLeast(1f)
        val radius = min(size.width, size.height) / 2 - 20.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2)
        var start = -90f
        for ((i, v) in values.withIndex()) {
            val sweep = (v / total) * 360f
            drawArc(
                color = CHART_COLORS[i % CHART_COLORS.size],
                startAngle = start,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
            )
            start += sweep
        }
    }
    ChartLegend(categories)
}

@Composable
private fun ChartLegend(labels: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        labels.take(6).forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).background(CHART_COLORS[i % CHART_COLORS.size]),
                )
                Spacer(Modifier.width(4.dp))
                Text(label.take(10), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DataTable(result: DataAskResp) {
    val cols = result.columns
    if (cols.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Row {
            cols.forEach { col ->
                Box(
                    Modifier.weight(1f).background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp),
                    ).padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Text(col, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        result.rows.take(100).forEach { row ->
            Row {
                cols.forEach { col ->
                    Box(Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Text(
                            row[col].cellText().take(20),
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
