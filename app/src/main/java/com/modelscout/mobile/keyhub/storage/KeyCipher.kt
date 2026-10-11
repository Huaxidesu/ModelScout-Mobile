package com.modelscout.mobile.keyhub.storage

/** 加解密抽象。测试用内存实现，真机用 AndroidKeyStore。 */
interface KeyCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(payload: ByteArray): ByteArray
}

/** 测试 / 无 Keystore 环境的直通实现（不加密，仅用于单测）。 */
class NoopCipher : KeyCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain
    override fun decrypt(payload: ByteArray): ByteArray = payload
}

/** 单测用：可逆 XOR，验证「写入-读出」往返与文件确实不是明文。 */
class XorCipher(private val key: Byte = 0x5A) : KeyCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain.map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
    override fun decrypt(payload: ByteArray): ByteArray = encrypt(payload)
}
