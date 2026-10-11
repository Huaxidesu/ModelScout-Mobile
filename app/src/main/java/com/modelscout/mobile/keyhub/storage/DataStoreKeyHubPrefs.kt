package com.modelscout.mobile.keyhub.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.modelscout.mobile.keyhub.KeyHubConfig
import com.modelscout.mobile.keyhub.KeyHubPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.keyHubDataStore: DataStore<Preferences> by preferencesDataStore(name = "keyhub_settings")

/**
 * 「本地保存（加密）」开关 + 并发数。
 * 注意：开关本身**不加密**（它不含敏感信息），只有 keys.enc / history.enc 走 Keystore。
 */
class DataStoreKeyHubPrefs(private val context: Context) : KeyHubPrefs {

    override val persistEnabled: Flow<Boolean> =
        context.keyHubDataStore.data.map { it[KEY_PERSIST] ?: false }

    override val concurrency: Flow<Int> =
        context.keyHubDataStore.data.map { it[KEY_CONCURRENCY] ?: KeyHubConfig.DEFAULT_CONCURRENCY }

    override suspend fun setPersistEnabled(value: Boolean) {
        context.keyHubDataStore.edit { it[KEY_PERSIST] = value }
    }

    override suspend fun setConcurrency(value: Int) {
        context.keyHubDataStore.edit { it[KEY_CONCURRENCY] = KeyHubConfig.effectiveConcurrency(value) }
    }

    private companion object {
        val KEY_PERSIST = booleanPreferencesKey("persist_enabled")
        val KEY_CONCURRENCY = intPreferencesKey("check_concurrency")
    }
}
