package com.modelscout.mobile.keyhub

import kotlinx.coroutines.flow.Flow

/** KeyHub 的持久化偏好。开关默认关。 */
interface KeyHubPrefs {
    val persistEnabled: Flow<Boolean>
    val concurrency: Flow<Int>
    suspend fun setPersistEnabled(value: Boolean)
    suspend fun setConcurrency(value: Int)
}

/** 内存实现（单测 / 预览用）。 */
class InMemoryKeyHubPrefs(
    initialPersist: Boolean = false,
    initialConcurrency: Int = KeyHubConfig.DEFAULT_CONCURRENCY,
) : KeyHubPrefs {
    private val persist = kotlinx.coroutines.flow.MutableStateFlow(initialPersist)
    private val concurrencyFlow = kotlinx.coroutines.flow.MutableStateFlow(initialConcurrency)

    override val persistEnabled: Flow<Boolean> = persist
    override val concurrency: Flow<Int> = concurrencyFlow
    override suspend fun setPersistEnabled(value: Boolean) {
        persist.value = value
    }

    override suspend fun setConcurrency(value: Int) {
        concurrencyFlow.value = KeyHubConfig.effectiveConcurrency(value)
    }
}
