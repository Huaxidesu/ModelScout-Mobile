package com.modelscout.mobile.keyhub

import com.modelscout.mobile.keyhub.model.Confidence
import com.modelscout.mobile.keyhub.model.Platform

/** 鉴权方式。 */
enum class AuthStyle { BEARER, X_API_KEY, X_GOOG_API_KEY, NONE }

/** 前缀规则。裸 `sk-` 这类需要标「推断」的，confidence 单独给。 */
data class PrefixRule(val value: String, val confidence: Confidence = Confidence.CERTAIN)

/**
 * 一个平台的全部事实。**加平台 = 在这里加一条记录**，其它文件不许出现平台分支（计划书 §二.1）。
 */
data class PlatformSpec(
    val platform: Platform,
    val prefixes: List<PrefixRule> = emptyList(),
    val pattern: Regex? = null,
    val confidence: Confidence = Confidence.CERTAIN,
    val authStyle: AuthStyle = AuthStyle.BEARER,
    val extraHeaders: Map<String, String> = emptyMap(),
    val validityEndpoint: String? = null,
    val balanceEndpoint: String? = null,
    /** 余额金额候选路径，按顺序容错尝试（点分路径，支持 `[0]` 索引）。 */
    val balanceAmountPaths: List<String> = emptyList(),
    /** 需要相减的路径（OpenRouter: limit - usage）。 */
    val balanceSubtractPath: String? = null,
    val balanceCurrencyPaths: List<String> = emptyList(),
    /** 该路径为 null 时表示「无上限」。 */
    val unlimitedWhenPathNull: String? = null,
    /** 响应里没有币种时的兜底币种。 */
    val defaultCurrency: String? = null,
    /** baseUrl 由用户运行时提供（自定义平台）。 */
    val runtimeBaseUrl: Boolean = false,
    /** 解析路径以实际响应为准的备注。 */
    val note: String? = null,
) {
    val hasBalanceEndpoint: Boolean get() = balanceEndpoint != null
    val supportsDetection: Boolean get() = validityEndpoint != null || balanceEndpoint != null || runtimeBaseUrl
}

/** 识别结果。 */
data class Detection(val platform: Platform, val confidence: Confidence) {
    /** UI 文案：确定的不加后缀，推断的标「（推断）」。 */
    val label: String
        get() = if (confidence == Confidence.INFERRED) {
            "${platform.displayName}（推断）"
        } else {
            platform.displayName
        }
}

/**
 * 平台知识单点（计划书 §4.1）。前缀、鉴权头、端点、余额解析路径全部收在这一个文件里。
 *
 * 决策 3：裸 `sk-` 识别为「OpenAI 兼容（推断）」，**禁止**自动把 Key 发往多个平台试错；
 * DeepSeek / Moonshot / SiliconFlow 都只是裸 `sk-` 的**候选**，必须用户手动指定。
 */
object PlatformRegistry {

    private val ANTHROPIC_VERSION = "2023-06-01"

    val specs: List<PlatformSpec> = listOf(
        PlatformSpec(
            platform = Platform.OPENAI,
            prefixes = listOf(
                PrefixRule("sk-proj-"),
                PrefixRule("sk-svcacct-"),
                PrefixRule("sk-", Confidence.INFERRED),
            ),
            validityEndpoint = "https://api.openai.com/v1/models",
            note = "无公开余额接口，余额显示 —",
        ),
        PlatformSpec(
            platform = Platform.ANTHROPIC,
            prefixes = listOf(
                PrefixRule("sk-ant-api03-"),
                PrefixRule("sk-ant-admin01-"),
                PrefixRule("sk-ant-"),
            ),
            authStyle = AuthStyle.X_API_KEY,
            extraHeaders = mapOf("anthropic-version" to ANTHROPIC_VERSION),
            validityEndpoint = "https://api.anthropic.com/v1/models",
            note = "无公开余额接口，余额显示 —",
        ),
        PlatformSpec(
            platform = Platform.DEEPSEEK,
            balanceEndpoint = "https://api.deepseek.com/user/balance",
            balanceAmountPaths = listOf("balance_infos[0].total_balance", "balance_infos[0].balance"),
            balanceCurrencyPaths = listOf("balance_infos[0].currency"),
            defaultCurrency = "CNY",
            note = "裸 sk- 候选，需用户手动指定平台",
        ),
        PlatformSpec(
            platform = Platform.MOONSHOT,
            balanceEndpoint = "https://api.moonshot.cn/v1/users/me/balance",
            balanceAmountPaths = listOf("data.available_balance", "data.balance"),
            balanceCurrencyPaths = listOf("data.currency"),
            defaultCurrency = "CNY",
            note = "裸 sk- 候选，需用户手动指定平台",
        ),
        PlatformSpec(
            platform = Platform.SILICONFLOW,
            balanceEndpoint = "https://api.siliconflow.cn/v1/user/info",
            balanceAmountPaths = listOf("data.balance", "data.totalBalance", "data.chargeBalance"),
            balanceCurrencyPaths = listOf("data.currency"),
            defaultCurrency = "CNY",
            note = "裸 sk- 候选，需用户手动指定平台",
        ),
        PlatformSpec(
            platform = Platform.OPENROUTER,
            prefixes = listOf(PrefixRule("sk-or-v1-"), PrefixRule("sk-or-")),
            balanceEndpoint = "https://openrouter.ai/api/v1/key",
            balanceAmountPaths = listOf("data.limit"),
            balanceSubtractPath = "data.usage",
            unlimitedWhenPathNull = "data.limit",
            defaultCurrency = "USD",
            note = "limit=null 表示无上限（∞）",
        ),
        PlatformSpec(
            platform = Platform.GROQ,
            prefixes = listOf(PrefixRule("gsk_")),
            validityEndpoint = "https://api.groq.com/openai/v1/models",
            note = "无公开余额接口，余额显示 —",
        ),
        PlatformSpec(
            platform = Platform.GEMINI,
            prefixes = listOf(PrefixRule("AIza")),
            authStyle = AuthStyle.X_GOOG_API_KEY,
            validityEndpoint = "https://generativelanguage.googleapis.com/v1beta/models",
            note = "无公开余额接口，余额显示 —",
        ),
        PlatformSpec(
            platform = Platform.ZHIPU,
            pattern = Regex("^[A-Za-z0-9]{8,}\\.[A-Za-z0-9]{16,}$"),
            note = "暂不支持自动检测，仅存储",
        ),
        PlatformSpec(
            platform = Platform.VOLCENGINE,
            pattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
            note = "暂不支持自动检测，仅存储",
        ),
        PlatformSpec(
            platform = Platform.CUSTOM,
            runtimeBaseUrl = true,
            // 自定义余额端点没有已知结构，按常见路径容错尝试
            balanceAmountPaths = listOf(
                "data.balance",
                "data.totalBalance",
                "data.total_balance",
                "data.available_balance",
                "balance",
                "total_balance",
            ),
            balanceCurrencyPaths = listOf("data.currency", "currency"),
            note = "用户填 baseUrl + /models，可选填余额端点",
        ),
    )

    private val byPlatform: Map<Platform, PlatformSpec> = specs.associateBy { it.platform }

    /** 所有前缀按长度降序，保证最长前缀优先。 */
    private val prefixIndex: List<Pair<PrefixRule, Platform>> =
        specs.flatMap { spec -> spec.prefixes.map { it to spec.platform } }
            .sortedByDescending { it.first.value.length }

    fun spec(platform: Platform): PlatformSpec =
        byPlatform[platform] ?: error("PlatformRegistry 缺少 $platform 的登记")

    /** 识别平台。最长前缀优先；其次正则；都不中 → 自定义（推断）。 */
    fun detect(rawKey: String): Detection {
        val key = rawKey.trim()
        if (key.isEmpty()) return Detection(Platform.CUSTOM, Confidence.INFERRED)

        prefixIndex.firstOrNull { key.startsWith(it.first.value) }?.let { (rule, platform) ->
            return Detection(platform, rule.confidence)
        }
        specs.firstOrNull { spec -> spec.pattern?.matches(key) == true }?.let { spec ->
            return Detection(spec.platform, spec.confidence)
        }
        return Detection(Platform.CUSTOM, Confidence.INFERRED)
    }

    /**
     * 由注册表自动生成的提取正则：所有前缀 + 平台正则 + 32 位以上 token 兜底（低置信度）。
     */
    val tokenRegex: Regex by lazy {
        val alternatives = mutableListOf<String>()
        prefixIndex.forEach { (rule, _) ->
            alternatives += Regex.escape(rule.value) + "[A-Za-z0-9_\\-]{6,}"
        }
        specs.mapNotNull { it.pattern?.pattern }.forEach { raw ->
            alternatives += "(?:" + raw.removePrefix("^").removeSuffix("$") + ")"
        }
        alternatives += "[A-Za-z0-9_\\-]{32,}"
        Regex(alternatives.joinToString("|"))
    }

    /** 所有平台的展示顺序（用于 UI 下拉）。 */
    val selectablePlatforms: List<Platform> = Platform.entries.toList()
}
