package com.modelscout.mobile.core.parse

/**
 * 推理模型判定：默认不发请求，用名称正则 + 响应体 reasoning_content/.reasoning 非空判定。
 */
object ReasoningHeuristics {

    private val NAME_PATTERNS = listOf(
        Regex("""(^|[^a-z0-9])r1([^a-z0-9]|$)""", RegexOption.IGNORE_CASE),
        Regex("""deepseek-reasoner""", RegexOption.IGNORE_CASE),
        Regex("""^o1""", RegexOption.IGNORE_CASE),
        Regex("""^o3""", RegexOption.IGNORE_CASE),
        Regex("""^o4-mini""", RegexOption.IGNORE_CASE),
        Regex("""thinking""", RegexOption.IGNORE_CASE),
        Regex("""reason""", RegexOption.IGNORE_CASE),
        Regex("""(^|[^a-z0-9])cot([^a-z0-9]|$)""", RegexOption.IGNORE_CASE),
        Regex("""^qwq""", RegexOption.IGNORE_CASE),
        Regex("""qwen3-think""", RegexOption.IGNORE_CASE),
        Regex("""glm-z1""", RegexOption.IGNORE_CASE),
    )

    fun looksLikeReasoning(modelId: String): Boolean =
        NAME_PATTERNS.any { it.containsMatchIn(modelId) }
}
