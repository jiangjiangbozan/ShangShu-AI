package com.example.aiagent.ui.agent.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiagent.data.model.AgentTraceResp

/**
 * Agent Trace 步骤卡片（借鉴 Operit 的步骤可视化设计）
 *
 * 不同 stepType 使用不同的图标和颜色：
 * - thought: 灯泡（思考）
 * - action: 闪电（工具调用）
 * - observation: 眼睛（观察结果）
 * - resume: 播放（恢复执行）
 * - error: 错误（异常）
 */
@Composable
fun TraceStepCard(
    trace: AgentTraceResp,
    modifier: Modifier = Modifier,
) {
    val stepStyle = rememberStepStyle(trace.stepType)
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = stepStyle.backgroundColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // 头部：步骤类型 + 序号 + 耗时（始终可见）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 类型图标
                Icon(
                    imageVector = stepStyle.icon,
                    contentDescription = stepStyle.label,
                    modifier = Modifier.size(20.dp),
                    tint = stepStyle.accentColor,
                )
                Spacer(Modifier.width(8.dp))

                // 类型标签 + 序号
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stepStyle.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = stepStyle.accentColor,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "#${trace.stepIndex}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        trace.skillId?.let { skillId ->
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = stepStyle.accentColor.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = skillId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = stepStyle.accentColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    // 摘要预览（折叠时显示 input/output 的第一行）
                    if (!expanded) {
                        val summary = trace.output?.take(80) ?: trace.input?.take(80)
                        if (!summary.isNullOrBlank()) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                // 耗时 + 展开/折叠按钮
                Column(horizontalAlignment = Alignment.End) {
                    trace.latencyMs?.let {
                        Text(
                            text = "${it}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "折叠" else "展开",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 展开内容：完整 input/output/error
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(start = 28.dp, end = 12.dp, bottom = 12.dp)) {
                    trace.input?.let { input ->
                        if (input.isNotBlank()) {
                            TraceField(label = "输入", content = input, accentColor = stepStyle.accentColor)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    trace.output?.let { output ->
                        if (output.isNotBlank()) {
                            TraceField(label = "输出", content = output, accentColor = stepStyle.accentColor)
                        }
                    }
                    trace.error?.let { error ->
                        if (error.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            TraceField(label = "错误", content = error, accentColor = MaterialTheme.colorScheme.error)
                        }
                    }
                    // Token 统计
                    if (trace.tokenIn != null || trace.tokenOut != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            trace.tokenIn?.let {
                                Text(
                                    text = "Token 输入: $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            trace.tokenOut?.let {
                                Text(
                                    text = "Token 输出: $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 可折叠的字段展示（用于 input/output/error）。 */
@Composable
private fun TraceField(
    label: String,
    content: String,
    accentColor: Color,
) {
    var fieldExpanded by remember { mutableStateOf(false) }
    val isLong = content.length > 120

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor,
        )
        Spacer(Modifier.height(2.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isLong) Modifier.clickable { fieldExpanded = !fieldExpanded } else Modifier),
        ) {
            Text(
                text = if (isLong && !fieldExpanded) content.take(120) + "..." else content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** 步骤类型对应的视觉样式。 */
private data class StepStyle(
    val icon: ImageVector,
    val label: String,
    val accentColor: Color,
    val backgroundColor: Color,
)

@Composable
private fun rememberStepStyle(stepType: String): StepStyle {
    return remember(stepType) {
        when (stepType.lowercase()) {
            "thought" -> StepStyle(
                icon = Icons.Filled.Lightbulb,
                label = "思考",
                accentColor = Color(0xFF9C27B0), // 紫色
                backgroundColor = Color(0xFFF3E5F5).copy(alpha = 0.5f),
            )
            "action" -> StepStyle(
                icon = Icons.Filled.Bolt,
                label = "行动",
                accentColor = Color(0xFF1565C0), // 蓝色
                backgroundColor = Color(0xFFE3F2FD).copy(alpha = 0.5f),
            )
            "observation" -> StepStyle(
                icon = Icons.Filled.Visibility,
                label = "观察",
                accentColor = Color(0xFF2E7D32), // 绿色
                backgroundColor = Color(0xFFE8F5E9).copy(alpha = 0.5f),
            )
            "resume" -> StepStyle(
                icon = Icons.Filled.PlayArrow,
                label = "恢复",
                accentColor = Color(0xFFEF6C00), // 橙色
                backgroundColor = Color(0xFFFFF3E0).copy(alpha = 0.5f),
            )
            "error" -> StepStyle(
                icon = Icons.Filled.Error,
                label = "错误",
                accentColor = Color(0xFFC62828), // 红色
                backgroundColor = Color(0xFFFFEBEE).copy(alpha = 0.5f),
            )
            else -> StepStyle(
                icon = Icons.Filled.PlayArrow,
                label = stepType,
                accentColor = Color(0xFF616161),
                backgroundColor = Color(0xFFF5F5F5).copy(alpha = 0.5f),
            )
        }
    }
}
