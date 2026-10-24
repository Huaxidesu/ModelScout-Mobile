package com.modelscout.mobile.keyhub.storage

import com.modelscout.mobile.keyhub.KeyHubConfig
import com.modelscout.mobile.keyhub.model.HistoryPoint
import com.modelscout.mobile.keyhub.model.KeyRecord
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 加密落盘。
 * - `keys.enc` / `history.enc`，AES-GCM 密文；
 * - 文件缺失或损坏 → 回退空列表，不抛异常；
 * - 关闭开关或「清除数据」→ 删除两个文件。
 */
class KeyVault(
    private val dir: File,
    private val cipher: KeyCipher,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val keysSerializer = ListSerializer(KeyRecord.serializer())
    private val historySerializer = ListSerializer(HistoryPoint.serializer())

    private val keysFile: File get() = File(dir, KeyHubConfig.FILE_KEYS)
    private val historyFile: File get() = File(dir, KeyHubConfig.FILE_HISTORY)

    // ------------------------------------------------------------ keys

    fun loadKeys(): List<KeyRecord> = read(keysFile) { text ->
        json.decodeFromString(keysSerializer, text)
    }.orEmpty()

    fun saveKeys(records: List<KeyRecord>) {
        write(keysFile, json.encodeToString(keysSerializer, records))
    }

    // ------------------------------------------------------------ history

    fun loadHistory(): List<HistoryPoint> = read(historyFile) { text ->
        json.decodeFromString(historySerializer, text)
    }.orEmpty()

    fun saveHistory(points: List<HistoryPoint>) {
        write(historyFile, json.encodeToString(historySerializer, points))
    }

    /** 追加并按上限裁剪（每 Key 500 条、全局 5 万条 FIFO）。 */
    fun appendHistory(newPoints: List<HistoryPoint>): List<HistoryPoint> {
        val merged = capHistory(loadHistory() + newPoints)
        saveHistory(merged)
        return merged
    }

    fun capHistory(points: List<HistoryPoint>): List<HistoryPoint> {
        val perKey = points.groupBy { it.fp }
            .flatMap { (_, list) -> list.takeLast(KeyHubConfig.HISTORY_PER_KEY_LIMIT) }
        return perKey.sortedBy { it.ts }.takeLast(KeyHubConfig.HISTORY_GLOBAL_LIMIT)
    }

    // ------------------------------------------------------------ lifecycle

    fun exists(): Boolean = keysFile.exists() || historyFile.exists()

    /** 删除两个文件（关闭开关 / 清除数据）。 */
    fun clearAll() {
        runCatching { keysFile.delete() }
        runCatching { historyFile.delete() }
    }

    // ------------------------------------------------------------ io

    private fun <T> read(file: File, decode: (String) -> T): T? {
        if (!file.exists()) return null
        return runCatching {
            decode(String(cipher.decrypt(file.readBytes()), Charsets.UTF_8))
        }.getOrNull() // 损坏文件回退空
    }

    private fun write(file: File, text: String) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            file.writeBytes(cipher.encrypt(text.toByteArray(Charsets.UTF_8)))
        }
    }
}
