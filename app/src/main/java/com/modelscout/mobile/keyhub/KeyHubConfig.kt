package com.modelscout.mobile.keyhub

/**
 * KeyHub 自有常量。刻意不复用 core/Constants（包隔离原则：新模块只允许依赖 core/error 与 Redactor）。
 */
object KeyHubConfig {

    /** 独立 OkHttp 实例：connect 10s / read 15s（计划书 §4.3）。 */
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 15L
    const val WRITE_TIMEOUT_SECONDS = 15L

    /** 余额检测并发默认 2，可调 1~5（决策 6）。 */
    const val DEFAULT_CONCURRENCY = 2
    const val MIN_CONCURRENCY = 1
    const val MAX_CONCURRENCY = 5

    /** 历史快照上限（计划书 §4.5）。 */
    const val HISTORY_PER_KEY_LIMIT = 500
    const val HISTORY_GLOBAL_LIMIT = 50_000

    /** 折线图展示的最近点数（计划书 §5.4）。 */
    const val CHART_MAX_POINTS = 30

    const val ACCEPT_JSON = "application/json"

    /** 加密文件名。 */
    const val FILE_KEYS = "keys.enc"
    const val FILE_HISTORY = "history.enc"

    /** AndroidKeyStore 主钥别名。 */
    const val KEYSTORE_ALIAS = "modelscout.keyhub.master"

    const val USER_AGENT = "ModelScout-KeyHub/1.1"

    fun effectiveConcurrency(raw: Int): Int = raw.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY)
}
