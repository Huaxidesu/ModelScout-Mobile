package com.modelscout.mobile.core.security

/**
 * 脱敏。
 * - 上游文本里出现配置的明文 Key -> *REDACTED*
 * - 日志里的 sk-xxx / Bearer xxx 打码：<=8 位全星号，否则前 3 后 4
 */
object Redactor {

    const val REDACTED = "***REDACTED***"

    private val SK_PATTERN = Regex("""sk-[A-Za-z0-9_\-]{6,}""")
    private val BEARER_PATTERN = Regex("""(?i)(bearer\s+)([A-Za-z0-9_\-\.]{6,})""")

    /** 单个密钥打码：<=8 位全星号，否则前 3 后 4。 */
    fun mask(secret: String): String {
        if (secret.isEmpty()) return secret
        if (secret.length <= 8) return "*".repeat(secret.length)
        val middle = secret.length - 7
        return secret.take(3) + "*".repeat(middle.coerceAtLeast(1)) + secret.takeLast(4)
    }

    /**
     * 展示上游文本前的脱敏。先用配置 Key 做精确替换，再对 sk- / Bearer 形态做后备打码。
     */
    fun redact(text: String, configuredKey: String? = null): String {
        if (text.isEmpty()) return text
        var out = text
        val key = configuredKey?.trim().orEmpty()
        if (key.length >= 6) {
            out = out.replace(key, REDACTED)
        }
        out = SK_PATTERN.replace(out) { m -> mask(m.value) }
        out = BEARER_PATTERN.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) }
        return out
    }
}
