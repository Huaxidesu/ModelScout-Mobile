package com.modelscout.mobile.core.engine

import com.modelscout.mobile.core.Constants
import com.modelscout.mobile.core.model.AppError

data class ScanConfig(
    val baseUrl: String,
    val apiKey: String = "",
    val concurrency: Int = Constants.DEFAULT_CONCURRENCY,
    val timeoutSeconds: Long = Constants.DEFAULT_TIMEOUT_SECONDS,
    val probeLimit: Boolean = true,
    val filter: String = "",
)

/** 协作式中止标志，在阶段边界检查。 */
class AbortFlag {
    @Volatile
    private var aborted: Boolean = false

    val isAborted: Boolean get() = aborted

    fun abort() {
        aborted = true
    }
}

/**
 * 进度状态。total / completed / percent / results 全部来自引擎的同一份结果表，
 * 避免历史上「双变量导致界面永远 0/0」的 bug。
 */
data class ScanState(
    val total: Int = 0,
    val completed: Int = 0,
    val percent: Int = 0,
    val currentModel: String? = null,
    val stage: com.modelscout.mobile.core.model.Stage = com.modelscout.mobile.core.model.Stage.TOOL,
    val running: Boolean = false,
    val fatalError: AppError? = null,
    val results: List<com.modelscout.mobile.core.model.ModelResult> = emptyList(),
)

data class ScanOutcome(
    val results: List<com.modelscout.mobile.core.model.ModelResult> = emptyList(),
    val total: Int = 0,
    val fatalError: AppError? = null,
    val aborted: Boolean = false,
)
