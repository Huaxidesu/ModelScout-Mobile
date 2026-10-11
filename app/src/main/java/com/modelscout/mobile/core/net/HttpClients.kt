package com.modelscout.mobile.core.net

import com.modelscout.mobile.core.Constants
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** 计划书 2.1：connect_timeout 15s；读写超时取配置值（下限 5s）。 */
object HttpClients {

    fun create(timeoutSeconds: Long): OkHttpClient {
        val timeout = Constants.effectiveTimeoutSeconds(timeoutSeconds)
        return OkHttpClient.Builder()
            .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
