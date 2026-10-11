package com.modelscout.mobile.core.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 测试组 3：连接智能提取（含 URL+Key 的文档片段 / 纯 JSON / 无 Key）。 */
class ExtractorTest {

    @Test
    fun `extracts base url and sk key from a doc snippet`() {
        val text = """
            # 快速开始
            先到 https://platform.example.com/docs/quickstart.html 看文档，
            接口地址是 https://api.deepseek.com/v1/chat/completions
            你的密钥：sk-abcdef1234567890abcdef1234567890
        """.trimIndent()
        val result = ConnectionExtractor.extract(text)
        assertEquals("https://api.deepseek.com/v1", result.baseUrl)
        assertEquals("sk-abcdef1234567890abcdef1234567890", result.apiKey)
        assertEquals("sk- 前缀", result.keyRule)
    }

    @Test
    fun `extracts apiKey json field from pure json config`() {
        val text = """
            {
              "baseURL": "https://open.bigmodel.cn/api/paas/v4",
              "apiKey": "0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        val result = ConnectionExtractor.extract(text)
        assertEquals("https://open.bigmodel.cn/api/paas/v4", result.baseUrl)
        assertEquals("0123456789abcdef0123456789abcdef", result.apiKey)
    }

    @Test
    fun `reports no key when text has none`() {
        val result = ConnectionExtractor.extract("Base URL: https://api.moonshot.cn/v1")
        assertTrue(result.hasUrl)
        assertFalse(result.hasKey)
        assertNull(result.apiKey)
    }

    @Test
    fun `prefers api host over documentation host`() {
        val text = "see https://github.com/foo/bar and https://api.siliconflow.cn/v1"
        val result = ConnectionExtractor.extract(text)
        assertEquals("https://api.siliconflow.cn/v1", result.baseUrl)
    }

    @Test
    fun `rejects urls whose score is not positive`() {
        val result = ConnectionExtractor.extract("homepage https://example.com")
        assertNull(result.baseUrl)
    }

    @Test
    fun `strips endpoint path so base url does not double up`() {
        val result = ConnectionExtractor.extract("https://api.openai.com/v1/embeddings")
        assertEquals("https://api.openai.com/v1", result.baseUrl)
    }
}
