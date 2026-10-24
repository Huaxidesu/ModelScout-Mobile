package com.modelscout.mobile.core.net

import com.modelscout.mobile.core.Constants
import com.modelscout.mobile.core.model.AppError
import com.modelscout.mobile.core.model.ErrorClassifier
import com.modelscout.mobile.core.model.ErrorKind
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 原始 HTTP 响应。code = -1 表示没拿到响应（网络/取消）。 */
data class RawResponse(
    val code: Int,
    val body: String,
    val error: AppError? = null,
) {
    val isSuccess: Boolean get() = code in 200..299
}

/**
 * 探测执行器。所有请求带 Accept: application/json。
 * 支持协作式中止：cancelAll() 会取消在途请求。
 */
class ProbeClient(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
) {

    private val inFlight: MutableSet<Call> = Collections.synchronizedSet(mutableSetOf())

    fun cancelAll() {
        val calls = synchronized(inFlight) { inFlight.toList() }
        calls.forEach { runCatching { it.cancel() } }
        inFlight.clear()
    }

    suspend fun postChatCompletions(body: String): RawResponse = execute(
        Request.Builder()
            .url(UrlNormalizer.endpoint(baseUrl, "chat/completions"))
            .header("Accept", Constants.ACCEPT_JSON)
            .header("Content-Type", "application/json")
            .applyAuth(apiKey)
            .post(body.toRequestBody(JSON_MEDIA))
            .build(),
    )

    private suspend fun execute(request: Request): RawResponse = withContext(Dispatchers.IO) {
        var response = executeOnce(request)
        var attempt = 1
        // 瞬时故障（SSL EOF / 5xx / 连接重置）重试一次。实测 Intern Discovery 会偶发 SSL EOF。
        while (attempt < MAX_ATTEMPTS && isRetryable(response)) {
            attempt++
            response = executeOnce(request)
        }
        response
    }

    private fun isRetryable(response: RawResponse): Boolean {
        response.error?.let { return it.kind != ErrorKind.CANCELLED }
        return response.code in 500..599
    }

    private suspend fun executeOnce(request: Request): RawResponse = withContext(Dispatchers.IO) {
        val call = try {
            client.newCall(request)
        } catch (t: Throwable) {
            return@withContext RawResponse(-1, "", ErrorClassifier.classify(0, null, t))
        }
        inFlight.add(call)
        try {
            call.execute().use { response ->
                val text = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                RawResponse(response.code, text, null)
            }
        } catch (t: Throwable) {
            val error = if (call.isCanceled()) {
                AppError(ErrorKind.CANCELLED, t.message)
            } else {
                ErrorClassifier.classify(0, null, t)
            }
            RawResponse(-1, "", error)
        } finally {
            inFlight.remove(call)
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val MAX_ATTEMPTS = 2
    }
}

internal fun Request.Builder.applyAuth(apiKey: String): Request.Builder =
    if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") else this
