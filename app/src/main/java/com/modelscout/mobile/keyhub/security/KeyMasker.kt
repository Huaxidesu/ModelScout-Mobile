package com.modelscout.mobile.keyhub.security

import java.security.MessageDigest

/**
 * Key 脱敏。不复用 core 的 Redactor.mask——那份是给网关日志用的
 * 「前3后4」，KeyHub 要求「前5后4」。
 */
object KeyMasker {

    /** 长度 >= 12 → 前 5 + `*` + 后 4（`sk-ab*cdef` 样式）；否则等长星号。 */
    fun mask(key: String): String {
        if (key.isEmpty()) return ""
        return if (key.length >= 12) {
            key.take(5) + "***" + key.takeLast(4)
        } else {
            "*".repeat(key.length)
        }
    }

    /** 历史快照用的指纹：sha256(rawKey) 前 16 位十六进制。历史里绝不存明文 Key。 */
    fun fingerprint(rawKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
