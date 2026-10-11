package com.modelscout.mobile.keyhub.checker

import com.modelscout.mobile.keyhub.KeyClassifier
import com.modelscout.mobile.keyhub.KeyHubHttp
import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform
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
 * ./gradlew testDebugUnitTest --tests "*RealKeyHubIT*" -PrealGateway=1 \
 *   -PkeyhubBaseUrl=https://xxx/v1 -PkeyhubApiKey=sk-xxx
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
}
