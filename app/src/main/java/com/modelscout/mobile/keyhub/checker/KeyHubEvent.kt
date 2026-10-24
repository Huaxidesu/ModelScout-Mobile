package com.modelscout.mobile.keyhub.checker

import com.modelscout.mobile.core.model.ErrorKind
import com.modelscout.mobile.keyhub.model.Balance
import com.modelscout.mobile.keyhub.model.KeyStatus

/** 检测事件流。 */
sealed interface KeyHubEvent {
    data class KeyChecked(
        val keyId: String,
        val status: KeyStatus,
        val balance: Balance? = null,
        val errorKind: ErrorKind? = null,
    ) : KeyHubEvent

    data class BatchProgress(val checked: Int, val total: Int) : KeyHubEvent

    data class BatchFinished(val summary: BatchSummary) : KeyHubEvent
}

data class BatchSummary(
    val total: Int = 0,
    val checked: Int = 0,
    val available: Int = 0,
    val insufficient: Int = 0,
    val invalid: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
    val cancelled: Boolean = false,
    /** 「仅重测失败」用的 id 列表（只含可重试的「查询失败」）。 */
    val failedIds: List<String> = emptyList(),
) {
    companion object {
        fun of(records: List<com.modelscout.mobile.keyhub.model.KeyRecord>, cancelled: Boolean): BatchSummary =
            BatchSummary(
                total = records.size,
                checked = records.count { it.lastStatus != KeyStatus.PENDING },
                available = records.count { it.lastStatus == KeyStatus.AVAILABLE },
                insufficient = records.count { it.lastStatus == KeyStatus.INSUFFICIENT },
                invalid = records.count { it.lastStatus == KeyStatus.INVALID },
                failed = records.count { it.lastStatus == KeyStatus.CHECK_FAILED },
                pending = records.count { it.lastStatus == KeyStatus.PENDING },
                cancelled = cancelled,
                failedIds = records.filter { it.lastStatus == KeyStatus.CHECK_FAILED }.map { it.id },
            )
    }
}
