package com.modelscout.mobile.keyhub.ui

import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform
import org.junit.Assert.assertEquals
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
}
