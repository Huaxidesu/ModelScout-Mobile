package com.modelscout.mobile.keyhub

/** 协作式中止标志。刻意不复用 core/engine 的同类件（包隔离）。 */
class AbortFlag {
    @Volatile
    private var aborted: Boolean = false

    val isAborted: Boolean get() = aborted

    fun abort() {
        aborted = true
    }
}
