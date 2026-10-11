package com.modelscout.mobile.keyhub.detect

import com.modelscout.mobile.keyhub.PlatformRegistry
import com.modelscout.mobile.keyhub.model.Confidence
import com.modelscout.mobile.keyhub.model.Platform

data class ExtractedKey(
    val rawKey: String,
    val label: String?,
    val platform: Platform,
    val confidence: Confidence,
)

data class ExtractionResult(
    val keys: List<ExtractedKey> = emptyList(),
    val duplicatesSkipped: Int = 0,
) {
    val total: Int get() = keys.size + duplicatesSkipped
}

/**
 * 识别与粘贴解析（计划书 §4.2）。
 * 分隔符：换行 / 逗号 / 分号 / 空白 / JSON 数组引号——由注册表生成的正则自然覆盖。
 */
object KeyDetector {

    private val LINE_SPLIT = Regex("[\\r\\n]+")
    private val LABEL_TRIM = charArrayOf(
        ' ', '\t', '"', '\'', '[', ']', '{', '}', ',', ';', ':', '=', '-', '，', '；', '：',
    )
    private const val MAX_LABEL_LENGTH = 40
    private const val MIN_TOKEN_LENGTH = 8

    /** 纯函数：最长前缀优先。 */
    fun detect(rawKey: String) = PlatformRegistry.detect(rawKey)

    fun extractKeys(text: String): ExtractionResult {
        if (text.isBlank()) return ExtractionResult()

        val seen = HashSet<String>()
        val keys = mutableListOf<ExtractedKey>()
        var duplicates = 0

        text.split(LINE_SPLIT).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val matches = PlatformRegistry.tokenRegex.findAll(line).toList()
            matches.forEachIndexed { index, match ->
                val token = match.value.trim()
                if (token.length < MIN_TOKEN_LENGTH) return@forEachIndexed
                if (!seen.add(token)) {
                    duplicates++
                    return@forEachIndexed
                }
                val detection = PlatformRegistry.detect(token)
                keys += ExtractedKey(
                    rawKey = token,
                    // 每行 Key 前的文字作为可选 label，一行多个 Key 时只有第一个带 label
                    label = if (index == 0) labelBefore(line, match.range.first) else null,
                    platform = detection.platform,
                    confidence = detection.confidence,
                )
            }
        }
        return ExtractionResult(keys = keys, duplicatesSkipped = duplicates)
    }

    private fun labelBefore(line: String, index: Int): String? {
        if (index <= 0) return null
        val head = line.substring(0, index).trim(*LABEL_TRIM)
        if (head.isEmpty() || head.length > MAX_LABEL_LENGTH) return null
        // 明显是 URL / 句子片段的不当 label
        if (head.contains("://")) return null
        return head
    }
}
