package com.example.aiagent.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown

/**
 * 消息内容解析结果
 * 借鉴 Operit 的 ThinkToolsXmlNodeGrouper，将消息内容按标签分组
 */
data class ParsedMessageContent(
    val thinkingBlocks: List<String> = emptyList(),
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val mainContent: String,
)

data class ToolCallInfo(
    val name: String,
    val arguments: String,
    val result: String? = null,
)

/**
 * 解析消息内容，提取思考过程和工具调用
 * 借鉴 Operit 的 XML 解析思路
 */
fun parseMessageContent(raw: String): ParsedMessageContent {
    val thinkingBlocks = mutableListOf<String>()
    val toolCalls = mutableListOf<ToolCallInfo>()
    var content = raw

    // 提取 <thinking>...</thinking> 块
    val thinkingRegex = Regex("""<thinking>([\s\S]*?)</thinking>""", RegexOption.DOT_MATCHES_ALL)
    thinkingRegex.findAll(raw).forEach { match ->
        thinkingBlocks.add(match.groupValues[1].trim())
    }
    content = thinkingRegex.replace(content, "").trim()

    // 提取工具调用标记
    // 格式: [TOOL_CALL:name]{args} 或 [TOOL_RESULT:name]{result}
    val toolCallRegex = Regex("""\[TOOL_CALL:(\w+)\]\{([\s\S]*?)\}""")
    val toolResultRegex = Regex("""\[TOOL_RESULT:(\w+)\]\{([\s\S]*?)\}""")

    // 收集工具调用和结果
    val toolResults = mutableMapOf<String, String>()
    toolResultRegex.findAll(raw).forEach { match ->
        toolResults[match.groupValues[1]] = match.groupValues[2]
    }

    toolCallRegex.findAll(raw).forEach { match ->
        val name = match.groupValues[1]
        val args = match.groupValues[2]
        toolCalls.add(ToolCallInfo(
            name = name,
            arguments = args,
            result = toolResults[name],
        ))
    }

    content = toolCallRegex.replace(content, "").trim()
    content = toolResultRegex.replace(content, "").trim()

    return ParsedMessageContent(
        thinkingBlocks = thinkingBlocks,
        toolCalls = toolCalls,
        mainContent = content,
    )
}

/**
 * 消息内容渲染器
 * 支持思考折叠、工具卡片、主内容 Markdown 渲染
 */
@Composable
fun MessageContentRenderer(
    content: String,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(content) { parseMessageContent(content) }

    Column(modifier = modifier) {
        // 思考过程块（可折叠）
        parsed.thinkingBlocks.forEach { thinking ->
            ThinkingBlock(
                content = thinking,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        // 工具调用卡片
        parsed.toolCalls.forEach { toolCall ->
            ToolCallCard(
                toolCall = toolCall,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        // 主内容（Markdown 渲染）
        if (parsed.mainContent.isNotBlank()) {
            Markdown(
                content = parsed.mainContent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 思考过程折叠块
 * 借鉴 Operit 的 ThinkToolsXmlNodeGrouper
 */
@Composable
fun ThinkingBlock(
    content: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "thinking_rotation",
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            // 标题栏（可点击展开/折叠）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "思考过程",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "折叠" else "展开",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            // 展开的内容
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
                        .padding(12.dp),
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * 工具调用卡片
 * 借鉴 Operit 的 ToolDisplayComponents
 */
@Composable
fun ToolCallCard(
    toolCall: ToolCallInfo,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "调用工具: ${toolCall.name}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            // 展开的详情
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 参数
                    if (toolCall.arguments.isNotBlank()) {
                        Text(
                            "参数:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(8.dp),
                        ) {
                            Text(
                                text = toolCall.arguments,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // 结果
                    toolCall.result?.let { result ->
                        Text(
                            "结果:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(8.dp),
                        ) {
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
