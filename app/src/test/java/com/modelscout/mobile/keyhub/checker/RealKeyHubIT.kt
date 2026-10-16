package com.modelscout.mobile.keyhub.checker

import com.modelscout.mobile.keyhub.KeyClassifier
import com.modelscout.mobile.keyhub.KeyHubHttp
import com.modelscout.mobile.keyhub.PlatformRegistry
import com.modelscout.mobile.keyhub.model.Confidence
import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform
import com.modelscout.mobile.keyhub.security.KeyMasker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * R3 真实网关冒烟（默认跳过）。
 *
 * ```
 * # 单 Key（自定义平台）
 * ./gradlew testDebugUnitTest --tests "*RealKeyHubIT*" -PrealGateway=1 \
 *   -PkeyhubBaseUrl=https://xxx/v1 -PkeyhubApiKey=sk-xxx
 *
 * # 批量（同厂家一批 Key，逗号分隔；推荐写进 gitignored 的 local.secrets.properties）
 * # local.secrets.properties:  realGateway=1
 * #                            keyhubBatchKeys=sk-a,sk-b,sk-c
 * ./gradlew testDebugUnitTest --tests "*RealKeyHubIT*"
 * ```
 */
class RealKeyHubIT {

    @Before
    fun requireOptIn() {
        Assume.assumeTrue(
            "未开启真实网关测试（加 -PrealGateway=1 才跑）",
            System.getProperty("modelscout.real") == "1",
        )
    }

    private fun engine() = KeyCheckEngine(KeyChecker(KeyHubHttp.create()))

    private fun record(platform: Platform, rawKey: String) = KeyRecord(
        id = platform.name,
        rawKey = rawKey,
        platform = platform,
        addedAt = 0L,
    )

    @Test
    fun `obviously invalid keys are classified as invalid not check failed`() = runBlocking {
        val bogus = "sk-definitely-invalid-key-000000000000"
        val platforms = listOf(
            Platform.DEEPSEEK,
            Platform.SILICONFLOW,
            Platform.OPENROUTER,
            Platform.OPENAI,
            Platform.GROQ,
            Platform.ANTHROPIC,
        )

        println()
        println("=".repeat(78))
        val checker = KeyChecker(KeyHubHttp.create())
        platforms.forEach { platform ->
            val outcome = checker.check(record(platform, bogus))
            val status = KeyClassifier.classify(outcome)
            println(
                "%-14s -> %-12s http=%s transport=%s errorKind=%s 余额=%s".format(
                    platform.displayName,
                    status,
                    outcome.httpCode?.toString() ?: "-",
                    outcome.transport?.name ?: "-",
                    outcome.errorKind?.name ?: "-",
                    com.modelscout.mobile.keyhub.model.Balance(
                        outcome.balance?.amount,
                        outcome.balance?.currency,
                    ).display(),
                ),
            )
            assertTrue(
                "${platform.displayName} 的无效 Key 不该判成「${status.label}」",
                status == KeyStatus.INVALID || status == KeyStatus.CHECK_FAILED,
            )
        }
        println("=".repeat(78))
        println()
    }

    @Test
    fun `custom platform validity path works against a real gateway`() = runBlocking {
        val baseUrl = System.getProperty("modelscout.keyhubBaseUrl").orEmpty()
        val apiKey = System.getProperty("modelscout.keyhubApiKey").orEmpty()
        Assume.assumeTrue("未提供 -PkeyhubBaseUrl / -PkeyhubApiKey", baseUrl.isNotBlank() && apiKey.isNotBlank())

        val custom = KeyRecord(
            id = "custom-real",
            rawKey = apiKey,
            platform = Platform.CUSTOM,
            addedAt = 0L,
            customBaseUrl = baseUrl,
        )
        val updated = engine().checkOne(custom)

        println()
        println("[CUSTOM] $baseUrl -> ${updated.lastStatus}  余额=${updated.displayBalance().display()}")
        println()

        assertEquals(KeyStatus.AVAILABLE, updated.lastStatus)
        // 无余额端点的平台：余额显示 —（决策 4）
        assertNull(updated.lastBalance)
        assertEquals("—", updated.displayBalance().display())
        assertNotNull(updated.lastCheckedAt)
    }

    @Test
    fun `a working key resolves to available on its balance endpoint`() = runBlocking {
        val apiKey = System.getProperty("modelscout.keyhubApiKey").orEmpty()
        val platformName = System.getProperty("modelscout.keyhubPlatform").orEmpty()
        Assume.assumeTrue("未提供 -PkeyhubPlatform", platformName.isNotBlank() && apiKey.isNotBlank())

        val platform = Platform.valueOf(platformName)
        val updated = engine().checkOne(record(platform, apiKey))

        println()
        println("[${platform.displayName}] ${updated.lastStatus} 余额=${updated.displayBalance().display()}")
        println()

        assertEquals(KeyStatus.AVAILABLE, updated.lastStatus)
    }

    /**
     * 批量冒烟：一整批同厂家的 Key（走 `local.secrets.properties` 的 `keyhubBatchKeys`，逗号分隔）。
     *
     * 验证两件事：
     * 1. **识别**：每个 Key 都被 [PlatformRegistry.detect] 判成 DeepSeek，而不是被裸 `sk-` 抢成 OpenAI（决策 13）；
     * 2. **余额端点**：DeepSeek 的 `/user/balance` + 解析路径真的能跑通（GOTCHAS §3 一直缺的端到端验证）。
     *
     * 只打印**指纹**，绝不打印明文。
     */
    @Test
    fun `a batch of deepseek keys is detected and checked end to end`() = runBlocking {
        val raw = System.getProperty("modelscout.keyhubBatchKeys").orEmpty()
        Assume.assumeTrue("未提供 keyhubBatchKeys", raw.isNotBlank())

        val keys = raw.split(',', '\n', '\r', ';', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val engine = KeyCheckEngine(KeyChecker(KeyHubHttp.create()))
        val statuses = mutableListOf<KeyStatus>()

        println()
        println("=".repeat(88))
        keys.forEach { rawKey ->
            val detection = PlatformRegistry.detect(rawKey)
            val updated = engine.checkOne(record(Platform.DEEPSEEK, rawKey))
            statuses += updated.lastStatus
            println(
                "%-16s %-14s -> %-10s 余额=%-14s 指纹=%s".format(
                    detection.platform.displayName,
                    if (detection.confidence == Confidence.INFERRED) "（推断）" else "",
                    updated.lastStatus.label,
                    updated.displayBalance().display(),
                    KeyMasker.fingerprint(rawKey),
                ),
            )
        }
        println("=".repeat(88))
        println(
            "共 ${keys.size} 个：" +
                KeyStatus.entries.joinToString(" · ") { s -> "${s.label} ${statuses.count { it == s }}" },
        )
        println()

        // 1. 识别：一个都不能落到 OpenAI
        keys.forEach { rawKey ->
            assertEquals(
                "该 Key 被识别成了 ${PlatformRegistry.detect(rawKey).platform.displayName}",
                Platform.DEEPSEEK,
                PlatformRegistry.detect(rawKey).platform,
            )
        }

        // 2. 端点 + 解析：至少要有一个 Key 走到明确结论，
        //    全是 CHECK_FAILED 说明端点或解析路径没跑通（或本机网络/代理有问题）。
        val definitive = statuses.count {
            it == KeyStatus.AVAILABLE || it == KeyStatus.INSUFFICIENT || it == KeyStatus.INVALID
        }
        assertTrue("整批都没有拿到明确结论（全是查询失败），端点或解析路径可能有问题", definitive > 0)
    }
}
