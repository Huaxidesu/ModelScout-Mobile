package com.modelscout.mobile.core

/**
 * 必守常量。全项目唯一来源，禁止在别处硬编码这些数值。
 */
object Constants {

    /** 探测请求 max_tokens。 */
    const val PROBE_MAX_TOKENS = 16

    /** 上限探测 max_tokens。 */
    const val LIMIT_PROBE_MAX_TOKENS = 999_999

    /** 默认并发数（1~10）。 */
    const val DEFAULT_CONCURRENCY = 3
    const val MIN_CONCURRENCY = 1
    const val MAX_CONCURRENCY = 10

    /** 默认请求超时（秒），下限 5s。 */
    const val DEFAULT_TIMEOUT_SECONDS = 30L
    const val MIN_TIMEOUT_SECONDS = 5L

    /** connect_timeout 15s。 */
    const val CONNECT_TIMEOUT_SECONDS = 15L

    /** /models 超时放宽到 max(配置, 60s)。 */
    const val MODELS_MIN_TIMEOUT_SECONDS = 60L

    /** 日志环形缓冲行数。 */
    const val LOG_BUFFER_LINES = 600

    /** 所有请求必须带 Accept: application/json（缺了部分网关回 406）。 */
    const val ACCEPT_JSON = "application/json"

    /** 工具探测用的 no-op 函数名。 */
    const val TOOL_PROBE_NAME = "modelscout_ping"

    /**
     * 视觉探测用的图片（64x64 纯色 PNG，133 字节）。
     *
     * 使用 64x64 而非 1x1：部分网关会把 1x1 图片判为
     * "image file is truncated"，导致真视觉模型被误判为不支持；
     * 64x64 图片真视觉模型正常返回 200 并能描述图片内容。
     */
    const val PROBE_IMAGE_PNG_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAATElEQVR42u3PQQkAAAgEsItucy+Cb2GwAktmfxMQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQuBQXsPFaWqd+fAAAAABJRU5ErkJggg=="

    /** 视觉探测图片的 MIME 前缀。 */
    const val PROBE_IMAGE_DATA_URL_PREFIX = "data:image/png;base64,"

    /** 单模型流水线阶段权重。 */
    const val STAGE_WEIGHT_TOOL = 20
    const val STAGE_WEIGHT_VISION = 40
    const val STAGE_WEIGHT_REASONING = 60
    const val STAGE_WEIGHT_LIMIT = 80
    const val STAGE_WEIGHT_DONE = 100

    /** 上限候选剔除阈值：>= 900000 视为回显的请求值。 */
    const val LIMIT_ECHO_THRESHOLD = 900_000

    /** 生成配置时 limit 字段必填，未知时使用的后备值。 */
    const val FALLBACK_CONTEXT_LIMIT = 128_000
    const val FALLBACK_OUTPUT_LIMIT = 4_096

    fun effectiveTimeoutSeconds(raw: Long): Long = raw.coerceAtLeast(MIN_TIMEOUT_SECONDS)

    /** /models 用更宽松的超时。 */
    fun modelsTimeoutSeconds(raw: Long): Long =
        maxOf(effectiveTimeoutSeconds(raw), MODELS_MIN_TIMEOUT_SECONDS)

    fun effectiveConcurrency(raw: Int): Int = raw.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY)
}
