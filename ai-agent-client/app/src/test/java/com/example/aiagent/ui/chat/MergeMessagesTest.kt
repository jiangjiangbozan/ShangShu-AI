package com.example.aiagent.ui.chat

import com.example.aiagent.data.model.MessageResp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [mergeMessagesForSession] 单元测试。
 *
 * 重点验证：用户中止第一个流式回答后，继续提出新问题，被中止的回答1
 * 不会错误地显示在对话列表最下方，所有消息严格按时间顺序排列。
 */
class MergeMessagesTest {

    private fun msg(id: Long, role: String, content: String, createTime: String = "") =
        MessageResp(
            messageId = id,
            sessionId = 1L,
            role = role,
            content = content,
            createTime = createTime,
        )

    /**
     * 核心场景：中止 A1 后发送 Q2，切换会话再切回时 A1 应保留在正确位置。
     *
     * 本地缓存（内存中正确顺序）: [Q1, A1(被中止), Q2, A2]
     * 后端返回（A1 未持久化）    : [Q1, Q2, A2]
     * 期望合并结果              : [Q1, A1, Q2, A2]
     * 修复前实际结果（BUG）      : [Q1, Q2, A2, A1]  ← A1 被追加到末尾
     */
    @Test
    fun abortedAnswerStaysInChronologicalPosition() {
        val local = listOf(
            msg(1, "user", "Q1", "2026-08-22T10:00:00.000"),
            msg(-1, "assistant", "A1 partial", "2026-08-22T10:00:01.000"),
            msg(2, "user", "Q2", "2026-08-22T10:00:05.000"),
            msg(-2, "assistant", "A2", "2026-08-22T10:00:06.000"),
        )
        // 后端尚未持久化 A1 partial
        val server = listOf(
            msg(101, "user", "Q1", "2026-08-22T10:00:00.000"),
            msg(102, "user", "Q2", "2026-08-22T10:00:05.000"),
            msg(103, "assistant", "A2", "2026-08-22T10:00:06.000"),
        )

        val merged = mergeMessagesForSession(local, server)

        assertEquals(4, merged.size)
        assertEquals("Q1", merged[0].content)
        assertEquals("A1 partial", merged[1].content)
        assertEquals("Q2", merged[2].content)
        assertEquals("A2", merged[3].content)
        // 顺序应为 user, assistant, user, assistant 交替
        assertEquals(listOf("user", "assistant", "user", "assistant"), merged.map { it.role })
    }

    /**
     * 正常完成的流式回答：后端已持久化所有消息，本地无额外消息。
     */
    @Test
    fun normalCompletedStreamReturnsServerAsIs() {
        val local = listOf(
            msg(1, "user", "Q1", "2026-08-22T10:00:00.000"),
            msg(-1, "assistant", "A1", "2026-08-22T10:00:01.000"),
        )
        val server = listOf(
            msg(101, "user", "Q1", "2026-08-22T10:00:00.000"),
            msg(102, "assistant", "A1", "2026-08-22T10:00:01.000"),
        )

        val merged = mergeMessagesForSession(local, server)

        // 后端已包含全部消息，去重后 local 无额外内容
        assertEquals(2, merged.size)
        assertEquals("Q1", merged[0].content)
        assertEquals("A1", merged[1].content)
        // 应使用后端版本（权威 messageId）
        assertEquals(101L, merged[0].messageId)
        assertEquals(102L, merged[1].messageId)
    }

    /**
     * 本地为空时直接返回后端数据。
     */
    @Test
    fun emptyLocalReturnsServerDirectly() {
        val server = listOf(
            msg(1, "user", "Q1", "2026-08-22T10:00:00.000"),
            msg(2, "assistant", "A1", "2026-08-22T10:00:01.000"),
        )

        val merged = mergeMessagesForSession(emptyList(), server)

        assertEquals(server, merged)
    }

    /**
     * 连续多次中止：每次中止的回答都应按时间插入正确位置。
     * 场景：Q1→A1(中止)→Q2→A2(中止)→Q3→A3(完成)
     */
    @Test
    fun multipleAbortsAllStayInOrder() {
        val local = listOf(
            msg(1, "user", "Q1", "2026-08-22T10:00:00.000"),
            msg(-1, "assistant", "A1 partial", "2026-08-22T10:00:01.000"),
            msg(2, "user", "Q2", "2026-08-22T10:00:10.000"),
            msg(-2, "assistant", "A2 partial", "2026-08-22T10:00:11.000"),
            msg(3, "user", "Q3", "2026-08-22T10:00:20.000"),
            msg(-3, "assistant", "A3", "2026-08-22T10:00:21.000"),
        )
        // 后端只持久化了 user 消息和最后完成的 A3
        val server = listOf(
            msg(101, "user", "Q1", "2026-08-22T10:00:00.000"),
            msg(102, "user", "Q2", "2026-08-22T10:00:10.000"),
            msg(103, "user", "Q3", "2026-08-22T10:00:20.000"),
            msg(104, "assistant", "A3", "2026-08-22T10:00:21.000"),
        )

        val merged = mergeMessagesForSession(local, server)

        assertEquals(6, merged.size)
        assertEquals(listOf("user", "assistant", "user", "assistant", "user", "assistant"), merged.map { it.role })
        assertEquals(
            listOf("Q1", "A1 partial", "Q2", "A2 partial", "Q3", "A3"),
            merged.map { it.content },
        )
    }

    /**
     * createTime 为空的旧消息应排到末尾，不破坏已有消息顺序。
     */
    @Test
    fun blankCreateTimeSortsToEnd() {
        val local = listOf(
            msg(-1, "assistant", "legacy partial without timestamp", createTime = ""),
        )
        val server = listOf(
            msg(1, "user", "Q1", "2026-08-22T10:00:00.000"),
            msg(2, "assistant", "A1", "2026-08-22T10:00:01.000"),
        )

        val merged = mergeMessagesForSession(local, server)

        assertEquals(3, merged.size)
        // 有时间戳的排前面，空时间戳的排末尾
        assertEquals("Q1", merged[0].content)
        assertEquals("A1", merged[1].content)
        assertEquals("legacy partial without timestamp", merged[2].content)
    }

    /**
     * 切换会话返回：后端最终持久化了 A1 partial，合并后应去重。
     */
    @Test
    fun deduplicatesWhenServerFinallyPersistsPartial() {
        val local = listOf(
            msg(-1, "assistant", "A1 partial", "2026-08-22T10:00:01.000"),
        )
        val server = listOf(
            msg(1, "user", "Q1", "2026-08-22T10:00:00.000"),
            msg(2, "assistant", "A1 partial", "2026-08-22T10:00:01.000"),
            msg(3, "user", "Q2", "2026-08-22T10:00:05.000"),
        )

        val merged = mergeMessagesForSession(local, server)

        // A1 partial 在后端已存在，本地版本被去重
        assertEquals(3, merged.size)
        assertEquals("Q1", merged[0].content)
        assertEquals("A1 partial", merged[1].content)
        assertEquals("Q2", merged[2].content)
        // 使用后端权威版本
        assertEquals(2L, merged[1].messageId)
    }
}
