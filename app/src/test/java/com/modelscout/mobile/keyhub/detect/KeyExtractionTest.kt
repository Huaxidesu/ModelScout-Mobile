package com.modelscout.mobile.keyhub.detect

import com.modelscout.mobile.keyhub.model.Confidence
import com.modelscout.mobile.keyhub.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 测试组 4：粘贴解析 4 例。 */
class KeyExtractionTest {

    @Test
    fun `multi line paste keeps leading text as label`() {
        val text = """
            OpenAI 主力: sk-proj-AAAAAAAAAAAAAAAAAAAAAAAAAAAA
            备用 gsk_BBBBBBBBBBBBBBBBBBBBBBBBBBBB
        """.trimIndent()
        val result = KeyDetector.extractKeys(text)
        assertEquals(2, result.keys.size)
        assertEquals("OpenAI 主力", result.keys[0].label)
        assertEquals("备用", result.keys[1].label)
        assertEquals(Platform.OPENAI, result.keys[0].platform)
        assertEquals(Platform.GROQ, result.keys[1].platform)
    }

    @Test
    fun `comma and whitespace separated keys on one line`() {
        val text = "sk-proj-AAAAAAAAAAAAAAAAAAAAAAAAAAAA,sk-svcacct-BBBBBBBBBBBBBBBBBBBBBBBBBBBB " +
            "sk-or-v1-CCCCCCCCCCCCCCCCCCCCCCCCCCCC"
        val result = KeyDetector.extractKeys(text)
        assertEquals(3, result.keys.size)
        assertEquals(Platform.OPENAI, result.keys[0].platform)
        assertEquals(Platform.OPENAI, result.keys[1].platform)
        assertEquals(Platform.OPENROUTER, result.keys[2].platform)
        // 同一行的第一个 Key 前面没有文字，label 为空
        assertEquals(null, result.keys[0].label)
    }

    @Test
    fun `json array payload`() {
        val text = """["sk-proj-AAAAAAAAAAAAAAAAAAAAAAAAAAAA", "gsk_BBBBBBBBBBBBBBBBBBBBBBBBBBBB"]"""
        val result = KeyDetector.extractKeys(text)
        assertEquals(2, result.keys.size)
        assertEquals(Platform.OPENAI, result.keys[0].platform)
        assertEquals(Platform.GROQ, result.keys[1].platform)
        assertEquals(0, result.duplicatesSkipped)
    }

    @Test
    fun `duplicates are skipped and counted`() {
        val text = """
            sk-proj-AAAAAAAAAAAAAAAAAAAAAAAAAAAA
            sk-proj-AAAAAAAAAAAAAAAAAAAAAAAAAAAA
            gsk_BBBBBBBBBBBBBBBBBBBBBBBBBBBB
        """.trimIndent()
        val result = KeyDetector.extractKeys(text)
        assertEquals(2, result.keys.size)
        assertEquals(1, result.duplicatesSkipped)
        assertEquals(3, result.total)
    }

    @Test
    fun `empty input yields nothing`() {
        assertTrue(KeyDetector.extractKeys("").keys.isEmpty())
        assertTrue(KeyDetector.extractKeys("   \n  ").keys.isEmpty())
    }

    @Test
    fun `long opaque token is picked up with low confidence`() {
        val token = "a".repeat(40)
        val result = KeyDetector.extractKeys(token)
        assertEquals(1, result.keys.size)
        assertEquals(Confidence.INFERRED, result.keys[0].confidence)
        assertEquals(Platform.CUSTOM, result.keys[0].platform)
    }
}
