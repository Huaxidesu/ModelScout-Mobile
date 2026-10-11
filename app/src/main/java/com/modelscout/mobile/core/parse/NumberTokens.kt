package com.modelscout.mobile.core.parse

/** 数值 token 解析：支持 128000 / 128,000 / 128k / 1.5m。 */
object NumberTokens {

    private val TOKEN = Regex("""([0-9][0-9,]*(?:\.[0-9]+)?)\s*([kKmM]?)""")

    fun parse(token: String): Int? {
        val match = TOKEN.find(token.trim()) ?: return null
        val base = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].lowercase()) {
            "k" -> 1_000.0
            "m" -> 1_000_000.0
            else -> 1.0
        }
        val value = base * multiplier
        if (value <= 0 || value > Int.MAX_VALUE) return null
        return value.toInt()
    }
}
