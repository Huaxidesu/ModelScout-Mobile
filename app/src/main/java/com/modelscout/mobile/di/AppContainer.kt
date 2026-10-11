package com.modelscout.mobile.di

import android.content.Context
import com.modelscout.mobile.core.dict.ModelParamDictionary
import com.modelscout.mobile.core.engine.ScanEngine
import com.modelscout.mobile.core.log.LogBuffer
import com.modelscout.mobile.data.AppSettingsStore
import com.modelscout.mobile.keyhub.KeyHubPrefs
import com.modelscout.mobile.keyhub.KeyHubStore
import com.modelscout.mobile.keyhub.KeyHubHttp
import com.modelscout.mobile.keyhub.checker.KeyCheckEngine
import com.modelscout.mobile.keyhub.checker.KeyChecker
import com.modelscout.mobile.keyhub.storage.DataStoreKeyHubPrefs
import com.modelscout.mobile.keyhub.storage.KeyVault
import com.modelscout.mobile.keyhub.storage.KeystoreCipher

/**
 * 手动 DI（技术栈锁定：不引入 Hilt）。
 */
class AppContainer(private val context: Context) {

    val settingsStore: AppSettingsStore by lazy { AppSettingsStore(context) }

    val logBuffer: LogBuffer by lazy { LogBuffer() }

    val dictionary: ModelParamDictionary by lazy {
        runCatching {
            val raw = context.assets.open(ASSET_MODEL_PARAMS).bufferedReader().use { it.readText() }
            ModelParamDictionary.fromJson(raw)
        }.getOrDefault(ModelParamDictionary.EMPTY)
    }

    fun newEngine(): ScanEngine = ScanEngine(dictionary)

    // ---------------- KeyHub（V1.1）----------------
    val keyHubPrefs: KeyHubPrefs by lazy { DataStoreKeyHubPrefs(context) }

    val keyHubStore: KeyHubStore by lazy {
        KeyHubStore(
            vault = KeyVault(context.filesDir, KeystoreCipher()),
            engine = KeyCheckEngine(KeyChecker(KeyHubHttp.create())),
        )
    }

    private companion object {
        const val ASSET_MODEL_PARAMS = "model_params.json"
    }
}
