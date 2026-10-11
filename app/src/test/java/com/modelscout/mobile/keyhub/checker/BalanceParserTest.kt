package com.modelscout.mobile.keyhub.checker

import com.modelscout.mobile.keyhub.PlatformRegistry
import com.modelscout.mobile.keyhub.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 测试组 3：四平台余额 JSON 解析（含字段缺失容错）。 */
class BalanceParserTest {

    private fun spec(platform: Platform) = PlatformRegistry.spec(platform)

    @Test
    fun `deepseek nested balance_infos`() {
        val body = """{"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"110.00"}]}"""
        val balance = BalanceParser.parse(body, spec(Platform.DEEPSEEK))!!
        assertEquals(110.0, balance.amount!!, 1e-9)
        assertEquals("CNY", balance.currency)
        assertEquals("¥110", balance.display())
    }

    @Test
    fun `moonshot data available_balance`() {
        val body = """{"code":0,"data":{"available_balance":88.5,"voucher_balance":0}}"""
        val balance = BalanceParser.parse(body, spec(Platform.MOONSHOT))!!
        assertEquals(88.5, balance.amount!!, 1e-9)
        assertEquals("CNY", balance.currency)
    }

    @Test
    fun `siliconflow falls back to totalBalance when balance missing`() {
        val first = BalanceParser.parse("""{"data":{"balance":"12.34"}}""", spec(Platform.SILICONFLOW))!!
        assertEquals(12.34, first.amount!!, 1e-9)

        val fallback = BalanceParser.parse("""{"data":{"totalBalance":20}}""", spec(Platform.SILICONFLOW))!!
        assertEquals(20.0, fallback.amount!!, 1e-9)
    }

    @Test
    fun `openrouter subtracts usage from limit`() {
        val body = """{"data":{"label":"sk-or","limit":100,"usage":30,"limit_remaining":70}}"""
        val balance = BalanceParser.parse(body, spec(Platform.OPENROUTER))!!
        assertEquals(70.0, balance.amount!!, 1e-9)
        assertEquals("USD", balance.currency)
        assertEquals("$70", balance.display())
    }

    @Test
    fun `openrouter null limit means unlimited`() {
        val body = """{"data":{"label":"sk-or","limit":null,"usage":30}}"""
        val balance = BalanceParser.parse(body, spec(Platform.OPENROUTER))!!
        assertTrue(balance.unlimited)
        assertNull(balance.amount)
        assertEquals("∞", balance.display())
    }

    @Test
    fun `missing fields are tolerated and return null`() {
        assertNull(BalanceParser.parse("""{"foo":1}""", spec(Platform.DEEPSEEK)))
        assertNull(BalanceParser.parse("", spec(Platform.DEEPSEEK)))
        assertNull(BalanceParser.parse("not json", spec(Platform.DEEPSEEK)))
        assertNull(BalanceParser.parse("[1,2,3]", spec(Platform.DEEPSEEK)))
    }

    @Test
    fun `balance display maps currencies`() {
        assertEquals("—", com.modelscout.mobile.keyhub.model.Balance().display())
        assertEquals("¥5", com.modelscout.mobile.keyhub.model.Balance(5.0, "CNY").display())
        assertEquals("$1.25", com.modelscout.mobile.keyhub.model.Balance(1.25, "USD").display())
        assertEquals("EUR 3", com.modelscout.mobile.keyhub.model.Balance(3.0, "EUR").display())
    }
}
