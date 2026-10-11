package com.modelscout.mobile.keyhub.ui

import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform

enum class SortMode(val label: String) {
    PLATFORM("平台分组"),
    STATUS("按状态"),
    ADDED_AT("按添加时间"),
}

/**
 * 列表派生逻辑（纯函数）。排序/分组规则收在这里，UI 只渲染。
 * 组内排序：余额降序（`—`/null 沉底，`∞` 视为最大）→ 添加时间倒序。
 */
object KeyHubListOps {

    private val STATUS_ORDER = listOf(
        KeyStatus.AVAILABLE,
        KeyStatus.INSUFFICIENT,
        KeyStatus.INVALID,
        KeyStatus.CHECK_FAILED,
        KeyStatus.PENDING,
    )

    fun filter(records: List<KeyRecord>, status: KeyStatus?, platform: Platform?): List<KeyRecord> =
        records.filter { record ->
            (status == null || record.lastStatus == status) &&
                (platform == null || record.platform == platform)
        }

    fun sort(records: List<KeyRecord>, mode: SortMode): List<KeyRecord> = when (mode) {
        SortMode.PLATFORM -> records.sortedWith(::compareInGroup)
        SortMode.STATUS -> records.sortedWith { a, b ->
            val byStatus = STATUS_ORDER.indexOf(a.lastStatus) - STATUS_ORDER.indexOf(b.lastStatus)
            if (byStatus != 0) byStatus else compareInGroup(a, b)
        }
        SortMode.ADDED_AT -> records.sortedByDescending { it.addedAt }
    }

    /** 平台分组；组内按 [compareInGroup]，组按平台展示名排序。 */
    fun groupByPlatform(records: List<KeyRecord>): List<Pair<Platform, List<KeyRecord>>> =
        records.groupBy { it.platform }
            .toList()
            .sortedBy { it.first.displayName }
            .map { (platform, list) -> platform to list.sortedWith(::compareInGroup) }

    /** 余额降序 → 添加时间倒序。null（`—`）沉底。 */
    fun compareInGroup(a: KeyRecord, b: KeyRecord): Int {
        val ra = balanceRank(a)
        val rb = balanceRank(b)
        val byBalance = when {
            ra == null && rb == null -> 0
            ra == null -> 1
            rb == null -> -1
            else -> rb.compareTo(ra)
        }
        if (byBalance != 0) return byBalance
        return b.addedAt.compareTo(a.addedAt)
    }

    /** ∞ 视为最大；`—`/null 返回 null 表示沉底。 */
    private fun balanceRank(record: KeyRecord): Double? = when {
        record.lastUnlimited -> Double.MAX_VALUE
        record.lastBalance != null -> record.lastBalance
        else -> null
    }

    fun platformCounts(records: List<KeyRecord>): List<Pair<Platform, Int>> =
        records.groupBy { it.platform }.map { it.key to it.value.size }.sortedBy { it.first.displayName }
}
