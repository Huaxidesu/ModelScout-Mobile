package com.modelscout.mobile.core.parse

import com.modelscout.mobile.core.Constants

/**
 * 计划书 2.4 上限探测的报错文本解析。
 *
 * 解析要点（照抄，别改）：
 * 1. 文本必须含 too large / exceed / at most / maximum / 超出 / 超过 / 上限 / 最大 / 不支持 之一才解析（防普通 400 误判）；
 * 2. 输出上限关键词：max_tokens ... at most N / completion tokens / maximum allowed / 中文「最大输出 / 上限为」；
 *    上下文关键词：maximum context length is N / maximum model length N / 中文「上下文长度 / 窗口」；
 * 3. 取候选最大值、剔除 >= 900000 的数（那是回显的请求值）；
 * 4. 支持 128k / 1.5m 与 128,000 千位分隔；
 * 5. 若上下文 < 输出（矛盾），丢弃输出保留上下文。
 */
object LimitParser {

    data class Parsed(val context: Int?, val output: Int?) {
        val isEmpty: Boolean get() = context == null && output == null
    }

    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]+)?\s*[kKmM]?)"""

    private val GATES = listOf(
        "too large", "exceed", "at most", "maximum", "超出", "超过", "上限", "最大", "不支持",
    )

    private val OUTPUT_PATTERNS = listOf(
        Regex("""max_tokens[^0-9]{0,80}?$NUM""", RegexOption.IGNORE_CASE),
        Regex("""(?:at most|maximum allowed|maximum of|no more than|max allowed)[^0-9]{0,30}?$NUM""", RegexOption.IGNORE_CASE),
        Regex("""maximum\s+(?:completion|output)\s+tokens?[^0-9]{0,40}?$NUM""", RegexOption.IGNORE_CASE),
        Regex("""(?:completion|output)\s+tokens[^0-9]{0,40}?$NUM""", RegexOption.IGNORE_CASE),
        Regex("""(?:<=|≤)\s*$NUM"""),
        Regex("""最大输出[^0-9]{0,20}?$NUM"""),
        Regex("""上限为[^0-9]{0,20}?$NUM"""),
    )

    private val CONTEXT_PATTERNS = listOf(
        Regex("""maximum\s+context\s+length[^0-9]{0,40}?$NUM""", RegexOption.IGNORE_CASE),
        Regex("""maximum\s+model\s+length[^0-9]{0,40}?$NUM""", RegexOption.IGNORE_CASE),
        Regex("""context\s+length[^0-9]{0,40}?$NUM""", RegexOption.IGNORE_CASE),
        Regex("""上下文长度[^0-9]{0,20}?$NUM"""),
        Regex("""上下文窗口[^0-9]{0,20}?$NUM"""),
        Regex("""窗口[^0-9]{0,20}?$NUM"""),
    )

    fun parse(raw: String): Parsed {
        if (raw.isBlank()) return Parsed(null, null)
        val lower = raw.lowercase()
        if (GATES.none { lower.contains(it.lowercase()) }) return Parsed(null, null)

        var output = maxCandidate(OUTPUT_PATTERNS, raw)
        val context = maxCandidate(CONTEXT_PATTERNS, raw)

        // 矛盾时：上下文 < 输出 -> 丢弃输出保留上下文
        if (context != null && output != null && context < output) {
            output = null
        }
        return Parsed(context, output)
    }

    /** 取该组关键词下所有候选的最大值，剔除回显值（>= 900000）。 */
    private fun maxCandidate(patterns: List<Regex>, text: String): Int? =
        patterns
            .asSequence()
            .flatMap { regex -> regex.findAll(text).map { it.groupValues[1] } }
            .mapNotNull { NumberTokens.parse(it) }
            .filter { it in 1 until Constants.LIMIT_ECHO_THRESHOLD }
            .maxOrNull()
}
