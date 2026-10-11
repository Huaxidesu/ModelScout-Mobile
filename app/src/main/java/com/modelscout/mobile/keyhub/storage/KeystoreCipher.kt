package com.modelscout.mobile.keyhub.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.modelscout.mobile.keyhub.KeyHubConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AndroidKeyStore 里的 AES256-GCM 主钥（计划书 §4.5）。
 * 密文格式：`[ivLen:1][iv][cipherText+tag]`。
 */
class KeystoreCipher(private val alias: String = KeyHubConfig.KEYSTORE_ALIAS) : KeyCipher {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + cipherText
    }

    override fun decrypt(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "密文为空" }
        val ivLength = payload[0].toInt()
        require(ivLength in 1..payload.size - 2) { "密文头损坏" }
        val iv = payload.copyOfRange(1, 1 + ivLength)
        val cipherText = payload.copyOfRange(1 + ivLength, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
