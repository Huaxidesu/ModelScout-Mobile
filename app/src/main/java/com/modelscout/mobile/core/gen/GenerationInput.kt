package com.modelscout.mobile.core.gen

import java.net.URI

/** 生成器共用的输入。 */
data class GenerationInput(
    val providerKey: String,
    val providerName: String,
    val npm: String,
    val baseUrl: String,
    val apiKeyLiteral: String? = null,
    val envVar: String? = null,
    val models: List<com.modelscout.mobile.core.model.ModelResult> = emptyList(),
) {
    /** 二选一：明文 / {env:VAR} 引用。 */
    val apiKeyValue: String
        get() = envVar?.takeIf { it.isNotBlank() }?.let { "{env:${it.trim()}}" }
            ?: apiKeyLiteral.orEmpty()
}

data class ProviderMeta(
    val key: String,
    val displayName: String,
    val npm: String,
)

/**
 * 按 baseURL 推断 OpenCode provider 片段（key / name / npm）。
 * 未收录的网关一律回落到 @ai-sdk/openai-compatible。
 */
object ProviderCatalog {

    const val OPENAI_COMPATIBLE = "@ai-sdk/openai-compatible"

    private data class Known(
        val needle: String,
        val meta: ProviderMeta,
    )

    private val KNOWN = listOf(
        Known("api.openai.com", ProviderMeta("openai", "OpenAI", "@ai-sdk/openai")),
        Known("api.deepseek.com", ProviderMeta("deepseek", "DeepSeek", "@ai-sdk/deepseek")),
        Known(
            "dashscope.aliyuncs.com",
            ProviderMeta("dashscope", "通义千问", OPENAI_COMPATIBLE),
        ),
        Known("api.moonshot.cn", ProviderMeta("moonshot", "Moonshot", OPENAI_COMPATIBLE)),
        Known("open.bigmodel.cn", ProviderMeta("bigmodel", "智谱 AI", OPENAI_COMPATIBLE)),
        Known("api.siliconflow.cn", ProviderMeta("siliconflow", "SiliconFlow", OPENAI_COMPATIBLE)),
        Known("ark.cn-beijing.volces.com", ProviderMeta("volcengine", "火山方舟", OPENAI_COMPATIBLE)),
        Known("api.anthropic.com", ProviderMeta("anthropic", "Anthropic", "@ai-sdk/anthropic")),
        Known(
            "generativelanguage.googleapis.com",
            ProviderMeta("google", "Google", "@ai-sdk/google"),
        ),
        Known("localhost", ProviderMeta("local", "本地服务", OPENAI_COMPATIBLE)),
        Known("127.0.0.1", ProviderMeta("local", "本地服务", OPENAI_COMPATIBLE)),
    )

    fun infer(baseUrl: String, fallbackName: String? = null): ProviderMeta {
        val host = runCatching { URI(baseUrl).host }.getOrNull()?.lowercase().orEmpty()
        KNOWN.firstOrNull { host == it.needle || host.endsWith("." + it.needle) }?.let { return it.meta }
        val key = deriveKey(host)
        return ProviderMeta(key, fallbackName?.takeIf { it.isNotBlank() } ?: key, OPENAI_COMPATIBLE)
    }

    private fun deriveKey(host: String): String {
        if (host.isEmpty()) return "custom"
        val parts = host.split('.').filter { it.isNotBlank() }
        val candidate = when {
            parts.size >= 2 -> parts[parts.size - 2]
            parts.size == 1 -> parts[0]
            else -> "custom"
        }
        val sanitized = candidate.lowercase().filter { it.isLetterOrDigit() || it == '-' }
        return sanitized.ifBlank { "custom" }
    }
}
