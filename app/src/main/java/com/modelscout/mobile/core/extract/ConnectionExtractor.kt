package com.modelscout.mobile.core.extract

import com.modelscout.mobile.core.net.UrlNormalizer
import java.net.URI

/**
 * 计划书 2.5 连接智能提取（纯函数，一段正则逻辑）。
 * 宁可漏识别，不可错识别。
 */
object ConnectionExtractor {

    data class UrlCandidate(val url: String, val score: Int, val reasons: List<String>)

    data class Result(
        val baseUrl: String? = null,
        val apiKey: String? = null,
        val keyRule: String? = null,
        val urlCandidates: List<UrlCandidate> = emptyList(),
    ) {
        val hasUrl: Boolean get() = !baseUrl.isNullOrBlank()
        val hasKey: Boolean get() = !apiKey.isNullOrBlank()
    }

    private val URL_REGEX = Regex("""https?://[^\s"'<>()\[\]{},;|，；、]+""")

    private val DOC_HOSTS = listOf(
        "github.com", "githubusercontent.com", "gitee.com", "zhihu.com", "juejin.cn",
        "csdn.net", "cnblogs.com", "segmentfault.com", "stackoverflow.com", "medium.com",
        "wikipedia.org", "notion.site", "yuque.com", "jianshu.com", "infoq.cn", "oschina.net",
    )

    private val FILE_EXTENSIONS = listOf(
        ".html", ".htm", ".md", ".txt", ".json", ".js", ".mjs", ".css", ".png", ".jpg",
        ".jpeg", ".gif", ".svg", ".zip", ".rar", ".7z", ".pdf", ".xml", ".yaml", ".yml",
        ".tar", ".gz", ".exe", ".apk", ".doc", ".docx", ".xls", ".xlsx", ".csv",
    )

    private val INTRANET_HOSTS = listOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "host.docker.internal")

    /** 按权重从高到低：sk- 前缀 > JSON 字段 > apiKey: xxx / 密钥：xxx > Bearer xxx > 32+ 位十六进制。 */
    private val KEY_RULES: List<Pair<String, Regex>> = listOf(
        "sk- 前缀" to Regex("""\bsk-[A-Za-z0-9_\-]{10,}"""),
        "JSON 字段" to Regex(
            """"(?:api[_-]?key|apikey|api_key|key|token)"\s*:\s*"([^"\s]{8,})"""",
            RegexOption.IGNORE_CASE,
        ),
        "apiKey/密钥 赋值" to Regex(
            """(?:api[_-]?key|apikey|密钥)\s*[:=：]\s*["']?([A-Za-z0-9_\-\.]{10,})["']?""",
            RegexOption.IGNORE_CASE,
        ),
        "Bearer" to Regex("""(?i)\bbearer\s+([A-Za-z0-9_\-\.]{10,})"""),
        "32+ 位十六进制" to Regex("""\b[a-fA-F0-9]{32,}\b"""),
    )

    fun extract(text: String): Result {
        if (text.isBlank()) return Result()
        val candidates = extractUrlCandidates(text)
        val best = candidates.firstOrNull()
        val key = extractKey(text)
        return Result(
            baseUrl = best?.url,
            apiKey = key?.first,
            keyRule = key?.second,
            urlCandidates = candidates,
        )
    }

    // ---------------------------------------------------------------- URL

    fun extractUrlCandidates(text: String): List<UrlCandidate> {
        val seen = LinkedHashMap<String, UrlCandidate>()
        for (match in URL_REGEX.findAll(text)) {
            val raw = match.value.trimEnd('.', ',', ';', ':', '、', '。', '，', '；', '！', '？', '!', '?')
            if (raw.length <= "https://".length) continue
            val normalized = UrlNormalizer.normalizeBaseUrl(raw)
            if (normalized.isBlank()) continue
            val candidate = score(raw, normalized)
            val existing = seen[normalized]
            if (existing == null || candidate.score > existing.score) {
                seen[normalized] = candidate
            }
        }
        return seen.values
            .filter { it.score > 0 } // 正分才采信
            .sortedByDescending { it.score }
    }

    private fun score(raw: String, normalized: String): UrlCandidate {
        val reasons = mutableListOf<String>()
        var score = 0

        val uri = runCatching { URI(normalized) }.getOrNull()
        val host = uri?.host?.lowercase().orEmpty()
        val path = uri?.path.orEmpty().trimEnd('/')

        if (host.contains("api")) {
            score += 3
            reasons += "主机含 api(+3)"
        }
        if (path.endsWith("/v1")) {
            score += 4
            reasons += "路径以 /v1 结尾(+4)"
        }
        val lowerRaw = raw.lowercase()
        if (listOf("compatible-mode", "openai", "paas").any { lowerRaw.contains(it) }) {
            score += 2
            reasons += "含 compatible-mode/openai/paas(+2)"
        }
        if (isIntranet(host)) {
            score += 3
            reasons += "内网地址(+3)"
        }
        if (FILE_EXTENSIONS.any { path.lowercase().endsWith(it) }) {
            score -= 6
            reasons += "文件扩展名结尾(-6)"
        }
        if (DOC_HOSTS.any { host == it || host.endsWith(".$it") }) {
            score -= 8
            reasons += "文档站(-8)"
        }
        if (path.isEmpty()) {
            score -= 2
            reasons += "纯域名(-2)"
        }
        return UrlCandidate(url = normalized, score = score, reasons = reasons)
    }

    private fun isIntranet(host: String): Boolean {
        if (host.isEmpty()) return false
        if (INTRANET_HOSTS.contains(host)) return true
        if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan")) return true
        if (host.startsWith("192.168.") || host.startsWith("10.")) return true
        if (host.startsWith("172.")) {
            val second = host.split('.').getOrNull(1)?.toIntOrNull() ?: -1
            if (second in 16..31) return true
        }
        // 单标签主机名（如 http://myserver:8000/v1）
        if (!host.contains('.') && !host.contains(':')) return true
        return false
    }

    // ---------------------------------------------------------------- Key

    fun extractKey(text: String): Pair<String, String>? {
        for ((rule, regex) in KEY_RULES) {
            val match = regex.find(text) ?: continue
            val value = if (match.groupValues.size > 1 && match.groupValues[1].isNotBlank()) {
                match.groupValues[1]
            } else {
                match.value
            }
            val cleaned = value.trim().trim('"', '\'', ',', ';', '。', '，')
            if (cleaned.length >= 8) return cleaned to rule
        }
        return null
    }
}
