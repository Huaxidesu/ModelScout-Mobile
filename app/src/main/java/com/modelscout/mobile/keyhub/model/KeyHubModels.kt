package com.modelscout.mobile.keyhub.model

import kotlinx.serialization.Serializable

/** 平台枚举。平台差异（前缀/鉴权/端点/解析路径）一律写在 PlatformRegistry，禁止散落。 */
@Serializable
enum class Platform(val displayName: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    DEEPSEEK("DeepSeek"),
    MOONSHOT("Moonshot"),
    SILICONFLOW("SiliconFlow"),
    OPENROUTER("OpenRouter"),
    GROQ("Groq"),
    GEMINI("Gemini"),
    ZHIPU("智谱 GLM"),
    VOLCENGINE("火山方舟"),
    CUSTOM("自定义"),
}

/** 识别置信度：确定 / 推断。 */
@Serializable
enum class Confidence { CERTAIN, INFERRED }

/** 徽章色调，状态 -> 视觉的映射也收在这一处，UI 不自行判断。 */
enum class StatusTone { GREEN, ORANGE, RED, GRAY, OUTLINE }

/**
 * 五态。注意：PENDING「⏳ 待检测」只是初始 UI 态，不算检测结论（决策 1）。
 */
@Serializable
enum class KeyStatus(val label: String, val symbol: String) {
    AVAILABLE("可用", "✅"),
    INSUFFICIENT("余额不足", "❌"),
    INVALID("已失效", "❌"),
    CHECK_FAILED("查询失败", "❌"),
    PENDING("待检测", "⏳"),
    ;

    val tone: StatusTone
        get() = when (this) {
            AVAILABLE -> StatusTone.GREEN
            INSUFFICIENT -> StatusTone.ORANGE
            INVALID -> StatusTone.RED
            CHECK_FAILED -> StatusTone.GRAY
            PENDING -> StatusTone.OUTLINE
        }
}

/** 余额。unlimited=true 表示平台明确返回「无上限」（OpenRouter limit=null）。 */
data class Balance(
    val amount: Double? = null,
    val currency: String? = null,
    val unlimited: Boolean = false,
) {
    /** 展示文案：¥ / $ / — / ∞。 */
    fun display(): String {
        if (unlimited) return "∞"
        val value = amount ?: return "—"
        val symbol = when (currency?.uppercase()) {
            "CNY", "RMB" -> "¥"
            "USD" -> "$"
            else -> currency?.let { "$it " } ?: ""
        }
        val text = if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.4f", value).trimEnd('0').trimEnd('.')
        }
        return symbol + text
    }
}

/** 一条 Key 记录。rawKey 只在输入框 / 加密文件 / 剪贴板三处出现明文（§4.5 红线）。 */
@Serializable
data class KeyRecord(
    val id: String,
    val rawKey: String,
    val label: String? = null,
    val platform: Platform,
    val platformOverridden: Boolean = false,
    val addedAt: Long,
    val lastStatus: KeyStatus = KeyStatus.PENDING,
    val lastBalance: Double? = null,
    val lastCurrency: String? = null,
    /** 平台返回「无上限」时为 true（OpenRouter limit=null），为渲染 ∞ 必需。 */
    val lastUnlimited: Boolean = false,
    val lastCheckedAt: Long? = null,
    /** 自定义平台的 baseUrl。 */
    val customBaseUrl: String? = null,
    /** 自定义平台可选的余额端点。 */
    val customBalanceEndpoint: String? = null,
) {
    /** 组装成展示用余额对象。 */
    fun displayBalance(): Balance = Balance(lastBalance, lastCurrency, lastUnlimited)
}

/** 历史快照。fp = sha256(rawKey) 前 16 位，历史里绝不存明文 Key。 */
@Serializable
data class HistoryPoint(
    val fp: String,
    val ts: Long,
    val status: KeyStatus,
    val balance: Double? = null,
    val currency: String? = null,
)
