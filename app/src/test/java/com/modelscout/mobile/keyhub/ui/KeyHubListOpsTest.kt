package com.modelscout.mobile.keyhub.ui

import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 列表派生逻辑：分组 + 组内余额降序 + 过滤（验收清单对应项）。 */
class KeyHubListOpsTest {

    private fun record(
        id: String,
        platform: Platform,
        balance: Double? = null,
        unlimited: Boolean = false,
        status: KeyStatus = KeyStatus.AVAILABLE,
        addedAt: Long = 0L,
    ) = KeyRecord(
        id = id,
        rawKey = "sk-test-$id",
        platform = platform,
        addedAt = addedAt,
        lastStatus = status,
        lastBalance = balance,
        lastUnlimited = unlimited,
    )

    @Test
    fun `groups by platform and sorts each group by balance descending`() {
        val records = listOf(
            record("a", Platform.OPENAI, balance = 5.0),
            record("b", Platform.OPENAI, balance = 50.0),
            record("c", Platform.OPENAI, balance = null),
            record("d", Platform.GROQ, balance = 1.0),
        )
        val groups = KeyHubListOps.groupByPlatform(records)

        assertEquals(listOf(Platform.GROQ, Platform.OPENAI), groups.map { it.first })
        assertEquals(listOf("b", "a", "c"), groups.first { it.first == Platform.OPENAI }.second.map { it.id })
    }

    @Test
    fun `unlimited balance ranks on top and dash sinks to the bottom`() {
        val records = listOf(
            record("dash", Platform.OPENAI, balance = null),
            record("nine", Platform.OPENAI, balance = 9.0),
            record("inf", Platform.OPENAI, unlimited = true),
        )
        val sorted = KeyHubListOps.sort(records, SortMode.PLATFORM)
        assertEquals(listOf("inf", "nine", "dash"), sorted.map { it.id })
    }

    @Test
    fun `equal balance falls back to newest first`() {
        val records = listOf(
            record("old", Platform.OPENAI, balance = 1.0, addedAt = 100),
            record("new", Platform.OPENAI, balance = 1.0, addedAt = 200),
        )
        assertEquals(listOf("new", "old"), KeyHubListOps.sort(records, SortMode.PLATFORM).map { it.id })
    }

    @Test
    fun `status sort follows the canonical status order`() {
        val records = listOf(
            record("pending", Platform.OPENAI, status = KeyStatus.PENDING),
            record("invalid", Platform.OPENAI, status = KeyStatus.INVALID),
            record("available", Platform.OPENAI, status = KeyStatus.AVAILABLE),
            record("failed", Platform.OPENAI, status = KeyStatus.CHECK_FAILED),
        )
        assertEquals(
            listOf("available", "invalid", "failed", "pending"),
            KeyHubListOps.sort(records, SortMode.STATUS).map { it.id },
        )
    }

    @Test
    fun `filters by status and platform`() {
        val records = listOf(
            record("a", Platform.OPENAI, status = KeyStatus.AVAILABLE),
            record("b", Platform.OPENAI, status = KeyStatus.INVALID),
            record("c", Platform.GROQ, status = KeyStatus.AVAILABLE),
        )
        assertEquals(2, KeyHubListOps.filter(records, KeyStatus.AVAILABLE, null).size)
        assertEquals(1, KeyHubListOps.filter(records, KeyStatus.AVAILABLE, Platform.GROQ).size)
        assertEquals(3, KeyHubListOps.filter(records, null, null).size)
    }

    // ---------------------------------------------------------------- 批量编辑

    @Test
    fun `selection toggles on and off`() {
        var selected = emptySet<String>()
        selected = KeyHubListOps.toggleSelection(selected, "a")
        selected = KeyHubListOps.toggleSelection(selected, "b")
        assertEquals(setOf("a", "b"), selected)

        selected = KeyHubListOps.toggleSelection(selected, "a")
        assertEquals(setOf("b"), selected)
    }

    @Test
    fun `select all and is all selected`() {
        val records = listOf(record("a", Platform.OPENAI), record("b", Platform.GROQ))
        assertEquals(setOf("a", "b"), KeyHubListOps.selectAll(records))
        assertTrue(KeyHubListOps.isAllSelected(records, setOf("a", "b")))
        assertFalse(KeyHubListOps.isAllSelected(records, setOf("a")))
        // 空列表不算「全选」，否则空列表上会显示「取消全选」
        assertFalse(KeyHubListOps.isAllSelected(emptyList(), emptySet()))
    }

    @Test
    fun `prune selection drops ids that no longer exist`() {
        val alive = listOf(record("a", Platform.OPENAI), record("b", Platform.OPENAI))
        assertEquals(setOf("a"), KeyHubListOps.pruneSelection(setOf("a", "ghost"), alive))
        // 没变化时返回同一个实例，避免无谓的 StateFlow 重发
        val unchanged = setOf("a", "b")
        assertSame(unchanged, KeyHubListOps.pruneSelection(unchanged, alive))
        assertEquals(emptySet<String>(), KeyHubListOps.pruneSelection(emptySet(), alive))
    }

    @Test
    fun `dominant platform of a selection is the most frequent one`() {
        val records = listOf(
            record("a", Platform.DEEPSEEK),
            record("b", Platform.OPENAI),
            record("c", Platform.DEEPSEEK),
        )
        assertEquals(Platform.DEEPSEEK, KeyHubListOps.dominantPlatform(records))
        assertEquals(null, KeyHubListOps.dominantPlatform(emptyList()))
    }
}
