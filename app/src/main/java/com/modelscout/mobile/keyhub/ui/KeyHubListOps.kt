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

    // ---------------------------------------------------------------- 多选（批量编辑）

    /** 勾选 / 取消勾选一个 id。 */
    fun toggleSelection(selected: Set<String>, id: String): Set<String> =
        if (id in selected) selected - id else selected + id

    fun selectAll(records: List<KeyRecord>): Set<String> = records.mapTo(HashSet()) { it.id }

    fun isAllSelected(records: List<KeyRecord>, selected: Set<String>): Boolean =
        records.isNotEmpty() && records.all { it.id in selected }

    /**
     * 剔除已经不存在的 id（删除 / 清空之后调用），避免出现「幽灵选中」——
     * 底部写着「已选 3」但列表里只剩 1 张卡。
     */
    fun pruneSelection(selected: Set<String>, records: List<KeyRecord>): Set<String> {
        if (selected.isEmpty()) return selected
        val alive = records.mapTo(HashSet()) { it.id }
        val pruned = selected.filterTo(HashSet()) { it in alive }
        return if (pruned.size == selected.size) selected else pruned
    }

    /** 一批记录里最常见的平台，用作「批量改平台」的预选值。并列取先出现的；空集合返回 null。 */
    fun dominantPlatform(records: List<KeyRecord>): Platform? =
        records.groupingBy { it.platform }.eachCount().maxByOrNull { it.value }?.key
}
