package com.modelscout.mobile.core.log

import com.modelscout.mobile.core.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 600 行环形日志缓冲。所有写入都经过脱敏（由调用方负责传入已脱敏文本，
 * 或在 [appendRaw] 时显式提供 configuredKey）。
 */
class LogBuffer(private val capacity: Int = Constants.LOG_BUFFER_LINES) {

    private val lines = ArrayDeque<String>(capacity)
    private val _flow = MutableStateFlow<List<String>>(emptyList())

    val flow: StateFlow<List<String>> = _flow.asStateFlow()

    @Synchronized
    fun append(line: String) {
        if (lines.size >= capacity) {
            repeat(lines.size - capacity + 1) { lines.removeFirstOrNull() }
        }
        lines.addLast(line)
        _flow.value = lines.toList()
    }

    @Synchronized
    fun appendAll(newLines: List<String>) {
        newLines.forEach { append(it) }
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() {
        lines.clear()
        _flow.value = emptyList()
    }
}
