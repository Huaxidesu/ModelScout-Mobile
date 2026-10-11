package com.modelscout.mobile.keyhub

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** KeyHub 独立的 OkHttp 实例（计划书 §4.3）：connect 10s / read 15s。 */
object KeyHubHttp {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(KeyHubConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(KeyHubConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(KeyHubConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
}
