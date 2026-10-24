package com.modelscout.mobile.core.net

import com.modelscout.mobile.core.Constants
import com.modelscout.mobile.core.model.AppError
import com.modelscout.mobile.core.model.ErrorClassifier
import com.modelscout.mobile.core.model.ErrorKind
import com.modelscout.mobile.core.model.ModelEntry
import com.modelscout.mobile.core.parse.ModelsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ModelsFetchResult(
    val models: List<ModelEntry> = emptyList(),
    val error: AppError? = null,
    val raw: String = "",
)

/**
 * /models 拉取。超时放宽到 max(配置, 60s)（由传入 client 保证）。
 * 404 时提示「Base URL 可能缺 /v1 路径」。
 */
class ModelsFetcher(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
) {

    suspend fun fetch(): ModelsFetchResult = withContext(Dispatchers.IO) {
        val request = try {
            Request.Builder()
                .url(UrlNormalizer.endpoint(baseUrl, "models"))
                .header("Accept", Constants.ACCEPT_JSON)
                .applyAuth(apiKey)
                .get()
                .build()
        } catch (t: Throwable) {
            return@withContext ModelsFetchResult(error = ErrorClassifier.classify(0, null, t))
        }

        try {
            client.newCall(request).execute().use { response ->
                val text = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                if (!response.isSuccessful) {
                    val error = ErrorClassifier.classify(response.code, text)
                    val hint = if (response.code == 404) {
                        AppError(ErrorKind.BASE_URL_INVALID, "Base URL 可能缺 /v1 路径", 404)
                    } else {
                        error
                    }
                    return@withContext ModelsFetchResult(error = hint, raw = text)
                }
                val models = ModelsParser.parse(text)
                ModelsFetchResult(models = models, raw = text)
            }
        } catch (t: Throwable) {
            ModelsFetchResult(error = ErrorClassifier.classify(0, null, t))
        }
    }
}
