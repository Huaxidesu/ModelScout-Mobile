package com.modelscout.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.modelscout.mobile.core.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "modelscout_settings")

/**
 * 非敏感设置持久化。**API Key 只存内存**（计划书 R2 连接页要求），因此这里不落盘任何密钥。
 */
class AppSettingsStore(private val context: Context) {

    data class Snapshot(
        val baseUrl: String = "",
        val concurrency: Int = Constants.DEFAULT_CONCURRENCY,
        val timeoutSeconds: Long = Constants.DEFAULT_TIMEOUT_SECONDS,
        val probeLimit: Boolean = true,
        val filter: String = "",
        val keyMode: String = KEY_MODE_ENV,
        val dshFormat: String = "JSONC",
    )

    val snapshot: Flow<Snapshot> = context.dataStore.data.map { prefs ->
        Snapshot(
            baseUrl = prefs[KEY_BASE_URL].orEmpty(),
            concurrency = prefs[KEY_CONCURRENCY] ?: Constants.DEFAULT_CONCURRENCY,
            timeoutSeconds = prefs[KEY_TIMEOUT] ?: Constants.DEFAULT_TIMEOUT_SECONDS,
            probeLimit = prefs[KEY_PROBE_LIMIT] ?: true,
            filter = prefs[KEY_FILTER].orEmpty(),
            keyMode = prefs[KEY_KEY_MODE] ?: KEY_MODE_ENV,
            dshFormat = prefs[KEY_DSH_FORMAT] ?: "JSONC",
        )
    }

    suspend fun saveConnection(
        baseUrl: String,
        concurrency: Int,
        timeoutSeconds: Long,
        probeLimit: Boolean,
        filter: String,
        keyMode: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl
            prefs[KEY_CONCURRENCY] = concurrency
            prefs[KEY_TIMEOUT] = timeoutSeconds
            prefs[KEY_PROBE_LIMIT] = probeLimit
            prefs[KEY_FILTER] = filter
            prefs[KEY_KEY_MODE] = keyMode
        }
    }

    suspend fun saveDshFormat(format: String) {
        context.dataStore.edit { prefs -> prefs[KEY_DSH_FORMAT] = format }
    }

    companion object {
        const val KEY_MODE_ENV = "env"
        const val KEY_MODE_PLAIN = "plain"

        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_CONCURRENCY = intPreferencesKey("concurrency")
        private val KEY_TIMEOUT = longPreferencesKey("timeout_seconds")
        private val KEY_PROBE_LIMIT = booleanPreferencesKey("probe_limit")
        private val KEY_FILTER = stringPreferencesKey("filter")
        private val KEY_KEY_MODE = stringPreferencesKey("key_mode")
        private val KEY_DSH_FORMAT = stringPreferencesKey("dsh_format")
    }
}
