package com.modelscout.mobile.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 测试组 2：报错解析（4 例：英文 at most / 中文上限 / 128k 单位 / >=900000 剔除）。 */
class ErrorParsingTest {

    @Test
    fun `parses english at most output limit and ignores echoed request value`() {
        val text = "Invalid max_tokens: 999999 is too large. maximum allowed value is 8192"
        val parsed = LimitParser.parse(text)
        assertEquals(8192, parsed.output)
        assertNull(parsed.context)
    }

    @Test
    fun `parses chinese output limit`() {
        val text = "请求的 max_tokens 超出上限，最大输出为 4096"
        val parsed = LimitParser.parse(text)
        assertEquals(4096, parsed.output)
    }

    @Test
    fun `parses 128k unit and thousands separator`() {
        val k = LimitParser.parse("This model's maximum context length is 128k tokens")
        assertEquals(128000, k.context)

        val separated = LimitParser.parse("This model's maximum context length is 128,000 tokens")
        assertEquals(128000, separated.context)

        val million = LimitParser.parse("This model's maximum context length is 1.5m tokens")
        // 1.5m 能被正确解析成 1500000，但按解析规则 >= 900000 视为回显值被剔除
        assertNull(million.context)
    }

    @Test
    fun `drops candidates at or above 900000`() {
        val parsed = LimitParser.parse("max_tokens 999999 is too large")
        assertTrue(parsed.isEmpty)
    }

    @Test
    fun `drops output when context contradicts it`() {
        val text = "maximum context length is 4096 tokens, max_tokens at most 8192"
        val parsed = LimitParser.parse(text)
        assertEquals(4096, parsed.context)
        assertNull(parsed.output)
    }

    @Test
    fun `ignores ordinary 400 without limit keywords`() {
        val parsed = LimitParser.parse("""{"error":{"message":"invalid request: messages is required"}}""")
        assertTrue(parsed.isEmpty)
    }
}
