package com.modelscout.mobile.keyhub

import com.modelscout.mobile.keyhub.detect.ExtractedKey
import com.modelscout.mobile.keyhub.detect.KeyDetector
import com.modelscout.mobile.keyhub.model.Confidence
import com.modelscout.mobile.keyhub.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

/** 测试组 1：前缀识别 10 例。 */
class KeyDetectionTest {

    private fun assertDetect(raw: String, platform: Platform, confidence: Confidence) {
        val detection = PlatformRegistry.detect(raw)
        assertEquals("平台不符：$raw", platform, detection.platform)
        assertEquals("置信度不符：$raw", confidence, detection.confidence)
    }

    @Test
    fun `openai project prefix`() =
        assertDetect("sk-proj-abcdefghijklmnop", Platform.OPENAI, Confidence.CERTAIN)

    @Test
    fun `openai service account prefix`() =
        assertDetect("sk-svcacct-abcdefghijklmnop", Platform.OPENAI, Confidence.CERTAIN)

    @Test
    fun `bare sk prefix is inferred openai`() =
        assertDetect("sk-fake0000000000000000000000000000", Platform.OPENAI, Confidence.INFERRED)

    @Test
    fun `anthropic api03 prefix beats bare sk`() =
        assertDetect("sk-ant-api03-abcdefghijklmnop", Platform.ANTHROPIC, Confidence.CERTAIN)

    @Test
    fun `anthropic admin prefix`() =
        assertDetect("sk-ant-admin01-abcdefghijklmnop", Platform.ANTHROPIC, Confidence.CERTAIN)

    @Test
    fun `anthropic short prefix`() =
        assertDetect("sk-ant-abcdefghijklmnop", Platform.ANTHROPIC, Confidence.CERTAIN)

    @Test
    fun `openrouter v1 prefix beats bare sk`() =
        assertDetect("sk-or-v1-abcdefghijklmnop", Platform.OPENROUTER, Confidence.CERTAIN)

    @Test
    fun `groq prefix`() =
        assertDetect("gsk_abcdefghijklmnopqrst", Platform.GROQ, Confidence.CERTAIN)

    @Test
    fun `gemini prefix`() =
        assertDetect("AIzaSyA1b2C3d4E5f6G7h8I9j0", Platform.GEMINI, Confidence.CERTAIN)

    @Test
    fun `zhipu dotted format`() =
        assertDetect("a1b2c3d4e5f6g7h8.0123456789abcdef", Platform.ZHIPU, Confidence.CERTAIN)

    @Test
    fun `volcengine uuid format`() =
        assertDetect("550e8400-e29b-41d4-a716-446655440000", Platform.VOLCENGINE, Confidence.CERTAIN)

    @Test
    fun `empty string falls back to custom inferred`() {
        assertDetect("", Platform.CUSTOM, Confidence.INFERRED)
        assertDetect("   ", Platform.CUSTOM, Confidence.INFERRED)
    }

    @Test
    fun `unknown format falls back to custom inferred`() =
        assertDetect("totally-unknown-credential", Platform.CUSTOM, Confidence.INFERRED)

    @Test
    fun `inferred label carries suffix`() {
        assertEquals("OpenAI（推断）", PlatformRegistry.detect("sk-abcdefghijklmnop").label)
        assertEquals("Anthropic", PlatformRegistry.detect("sk-ant-api03-abcdefgh").label)
    }

    @Test
    fun `deepseek format beats bare sk inference`() {
        // 决策 13：DeepSeek = sk- + 32 位小写 hex，格式匹配必须压过裸 sk- 的 OpenAI 推断
        assertDetect(
            "sk-0123456789abcdef0123456789abcdef",
            Platform.DEEPSEEK,
            Confidence.INFERRED,
        )
    }

    @Test
    fun `deepseek detection survives surrounding text`() {
        val text = "主号 sk-0123456789abcdef0123456789abcdef\n备用 sk-fedcba9876543210fedcba9876543210"
        val result = KeyDetector.extractKeys(text)
        assertEquals(2, result.keys.size)
        result.keys.forEach { assertEquals(Platform.DEEPSEEK, it.platform) }
        assertEquals("主号", result.keys[0].label)
    }

    @Test
    fun `uppercase hex is not deepseek`() {
        // DeepSeek 只发小写 hex；大写不认，避免把其它厂商的裸 sk- 误判成 DeepSeek
        assertDetect(
            "sk-0123456789ABCDEF0123456789ABCDEF",
            Platform.OPENAI,
            Confidence.INFERRED,
        )
    }

    @Test
    fun `wrong length hex is not deepseek`() {
        assertDetect("sk-0123456789abcdef0123456789abcde", Platform.OPENAI, Confidence.INFERRED) // 31 位
        assertDetect("sk-0123456789abcdef0123456789abcdef0", Platform.OPENAI, Confidence.INFERRED) // 33 位
    }

    @Test
    fun `non hex characters are not deepseek`() {
        // 40 位但含 g-z，属于「不知道是谁的裸 sk-」，仍归 OpenAI（推断）
        assertDetect("sk-fake0000000000000000000000000000", Platform.OPENAI, Confidence.INFERRED)
    }

    @Test
    fun `vendor prefixes still win over the deepseek pattern`() {
        // pattern 只吃「32 位小写 hex」，不该抢走带厂商标识的长前缀
        assertDetect("sk-proj-abcdefghijklmnop", Platform.OPENAI, Confidence.CERTAIN)
        assertDetect("sk-ant-api03-abcdefghijklmnop", Platform.ANTHROPIC, Confidence.CERTAIN)
        assertDetect("sk-or-v1-abcdefghijklmnop", Platform.OPENROUTER, Confidence.CERTAIN)
    }

    @Test
    fun `ambiguous candidates stay manual only`() {
        // 决策 3 仍然成立：Moonshot / SiliconFlow 是 sk- + 48 位字母数字，与旧版 OpenAI 无法区分，
        // 不得自动判成自己。DeepSeek 因为格式可辨识，已从「候选」升级为 pattern 识别（决策 13）。
        listOf(Platform.MOONSHOT, Platform.SILICONFLOW).forEach { platform ->
            assertEquals(emptyList<PrefixRule>(), PlatformRegistry.spec(platform).prefixes)
            assertEquals(null, PlatformRegistry.spec(platform).pattern)
        }
        assertEquals(emptyList<PrefixRule>(), PlatformRegistry.spec(Platform.DEEPSEEK).prefixes)
    }

    @Test
    fun `dominant platform picks the most frequent one`() {
        val mixed = listOf(
            ExtractedKey("sk-0123456789abcdef0123456789abcdef", null, Platform.DEEPSEEK, Confidence.INFERRED),
            ExtractedKey("gsk_aaaaaaaaaaaaaaaaaaaaaaaa", null, Platform.GROQ, Confidence.CERTAIN),
            ExtractedKey("sk-fedcba9876543210fedcba9876543210", null, Platform.DEEPSEEK, Confidence.INFERRED),
        )
        assertEquals(Platform.DEEPSEEK, KeyDetector.dominantPlatform(mixed))
        assertEquals(null, KeyDetector.dominantPlatform(emptyList()))
    }
}
