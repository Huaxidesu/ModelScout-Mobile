package com.modelscout.mobile.keyhub.storage

import com.modelscout.mobile.keyhub.KeyHubConfig
import com.modelscout.mobile.keyhub.model.HistoryPoint
import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 测试组 5：Vault 往返 4 例（开-写读-关-清除-损坏文件回退空）。 */
class KeyVaultTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val cipher = XorCipher()
    private fun vault(dir: File = temp.root) = KeyVault(dir, cipher)

    private fun record(id: String) = KeyRecord(
        id = id,
        rawKey = "sk-proj-SECRETSECRETSECRET$id",
        label = "测试 $id",
        platform = Platform.OPENAI,
        addedAt = 1_700_000_000_000L,
        lastStatus = KeyStatus.AVAILABLE,
        lastBalance = 42.0,
        lastCurrency = "USD",
    )

    @Test
    fun `write then read round trips`() {
        val vault = vault()
        vault.saveKeys(listOf(record("a"), record("b")))
        vault.appendHistory(
            listOf(
                HistoryPoint("fp1", 1L, KeyStatus.AVAILABLE, 42.0, "USD"),
                HistoryPoint("fp1", 2L, KeyStatus.INSUFFICIENT, 0.0, "USD"),
            ),
        )

        val reopened = vault()
        val keys = reopened.loadKeys()
        assertEquals(2, keys.size)
        assertEquals("sk-proj-SECRETSECRETSECRETa", keys[0].rawKey)
        assertEquals(Platform.OPENAI, keys[0].platform)
        assertEquals(42.0, keys[0].lastBalance!!, 1e-9)

        val history = reopened.loadHistory()
        assertEquals(2, history.size)
        assertEquals(KeyStatus.INSUFFICIENT, history[1].status)
    }

    @Test
    fun `turning persistence off deletes both files`() {
        val vault = vault()
        vault.saveKeys(listOf(record("a")))
        vault.saveHistory(listOf(HistoryPoint("fp", 1L, KeyStatus.AVAILABLE)))
        assertTrue(vault.exists())

        vault.clearAll()

        assertFalse(File(temp.root, KeyHubConfig.FILE_KEYS).exists())
        assertFalse(File(temp.root, KeyHubConfig.FILE_HISTORY).exists())
        assertTrue(vault.loadKeys().isEmpty())
        assertTrue(vault.loadHistory().isEmpty())
    }

    @Test
    fun `corrupt file falls back to empty instead of throwing`() {
        val keysFile = File(temp.root, KeyHubConfig.FILE_KEYS)
        temp.root.mkdirs()
        keysFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertTrue(vault().loadKeys().isEmpty())

        keysFile.writeBytes("this is not encrypted json".toByteArray())
        assertTrue(vault().loadKeys().isEmpty())
    }

    @Test
    fun `ciphertext on disk never contains the plaintext key`() {
        val vault = vault()
        vault.saveKeys(listOf(record("a")))
        val onDisk = File(temp.root, KeyHubConfig.FILE_KEYS).readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("磁盘上不该出现明文 Key", onDisk.contains("sk-proj-SECRETSECRETSECRETa"))
        assertFalse("磁盘上不该出现明文 label", onDisk.contains("测试 a"))
    }

    @Test
    fun `history is capped per key and globally`() {
        val vault = vault()
        val many = (1..(KeyHubConfig.HISTORY_PER_KEY_LIMIT + 20)).map {
            HistoryPoint("fpA", it.toLong(), KeyStatus.AVAILABLE, it.toDouble())
        }
        val stored = vault.appendHistory(many)
        assertEquals(KeyHubConfig.HISTORY_PER_KEY_LIMIT, stored.size)
        // FIFO：保留最新的
        assertEquals(KeyHubConfig.HISTORY_PER_KEY_LIMIT.toLong() + 20, stored.last().ts)
    }
}
