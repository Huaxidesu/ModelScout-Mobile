package com.modelscout.mobile.keyhub

import com.modelscout.mobile.keyhub.model.Confidence
import com.modelscout.mobile.keyhub.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

/** 测试组 1：前缀识别 10 例（计划书 §4.6）。 */
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
    fun `candidate platforms are not auto detected`() {
        // 决策 3：DeepSeek / Moonshot / SiliconFlow 只是裸 sk- 候选，不得自动判成自己
        listOf(Platform.DEEPSEEK, Platform.MOONSHOT, Platform.SILICONFLOW).forEach { platform ->
            assertEquals(emptyList<PrefixRule>(), PlatformRegistry.spec(platform).prefixes)
        }
    }
}
