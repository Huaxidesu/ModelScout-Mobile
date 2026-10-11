package com.modelscout.mobile.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 测试组 5：脱敏（2 例）。 */
class RedactorTest {

    @Test
    fun `masks short secrets fully and long secrets with head and tail`() {
        assertEquals("********", Redactor.mask("12345678"))
        assertEquals("abc******wxyz", Redactor.mask("abcdefghiwxyz"))
    }

    @Test
    fun `redacts configured key and sk or bearer forms in upstream text`() {
        val configured = "sk-configured-key-1234567890"
        val upstream = """
            Authorization: Bearer sk-otherkey0987654321
            raw key = $configured
        """.trimIndent()

        val output = Redactor.redact(upstream, configured)

        assertFalse("配置的明文 Key 不能出现在输出里", output.contains(configured))
        assertFalse("sk- 形态必须被打码", output.contains("sk-otherkey0987654321"))
        assertFalse(output.contains("sk-configured-key-1234567890"))
        // Bearer 前缀保留，token 被打码
        org.junit.Assert.assertTrue(output.contains("Bearer sk-"))
    }
}
