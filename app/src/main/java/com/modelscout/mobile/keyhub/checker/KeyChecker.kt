package com.modelscout.mobile.keyhub.checker

import com.modelscout.mobile.core.model.ErrorKind
import com.modelscout.mobile.keyhub.AuthStyle
import com.modelscout.mobile.keyhub.KeyHubConfig
import com.modelscout.mobile.keyhub.PlatformRegistry
import com.modelscout.mobile.keyhub.PlatformSpec
import com.modelscout.mobile.keyhub.model.CheckOutcome
import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.TransportError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 本次要打的端点。 */
data class Endpoint(val url: String, val isBalance: Boolean)

/**
 * 端点解析接缝。默认实现只读 [PlatformRegistry]，平台知识仍然单点；
 * 单测注入自己的实现指向 MockWebServer。
 */
fun interface EndpointResolver {
    fun resolve(spec: PlatformSpec, record: KeyRecord): Endpoint?
}

/** 默认：完全由 PlatformRegistry 决定（计划书 §4.1）。 */
object RegistryEndpointResolver : EndpointResolver {
    override fun resolve(spec: PlatformSpec, record: KeyRecord): Endpoint? {
        if (spec.runtimeBaseUrl) {
            record.customBalanceEndpoint?.takeIf { it.isNotBlank() }?.let {
                return Endpoint(it.trim(), isBalance = true)
            }
            val base = record.customBaseUrl?.takeIf { it.isNotBlank() } ?: return null
            return Endpoint(base.trimEnd('/') + "/models", isBalance = false)
        }
        spec.balanceEndpoint?.let { return Endpoint(it, isBalance = true) }
        spec.validityEndpoint?.let { return Endpoint(it, isBalance = false) }
        return null
    }
}

/**
 * 单 Key 检测（计划书 §4.3）。只发一次请求：
 * 有余额端点 → 打余额端点；无余额端点 → 打有效性端点。
 *
 * 决策 3：**绝不**把同一个 Key 发往多个平台试错；发到哪个端点完全由记录的 platform 决定。
 */
class KeyChecker(
    private val client: OkHttpClient,
    private val resolver: EndpointResolver = RegistryEndpointResolver,
) {

    suspend fun check(record: KeyRecord): CheckOutcome {
        val spec = PlatformRegistry.spec(record.platform)
        val target = resolver.resolve(spec, record) ?: return CheckOutcome(storageOnly = true)

        val request = try {
            Request.Builder()
                .url(target.url)
                .header("Accept", KeyHubConfig.ACCEPT_JSON)
                .header("User-Agent", KeyHubConfig.USER_AGENT)
                .applyAuth(spec, record.rawKey)
                .apply { spec.extraHeaders.forEach { (name, value) -> header(name, value) } }
                .get()
                .build()
        } catch (t: Throwable) {
            return CheckOutcome(transport = TransportError.NETWORK, errorKind = ErrorKind.UNKNOWN)
        }

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    val code = response.code
                    val success = code in 200..299
                    val balance = if (target.isBalance && success) {
                        BalanceParser.parse(body, spec)
                    } else {
                        null
                    }
                    CheckOutcome(
                        httpCode = code,
                        balance = balance,
                        hasBalanceEndpoint = target.isBalance,
                        errorKind = errorKindFor(code, parseFailed = target.isBalance && success && balance == null),
                    )
                }
            } catch (t: Throwable) {
                val transport = when (t) {
                    is SocketTimeoutException -> TransportError.TIMEOUT
                    is UnknownHostException -> TransportError.DNS
                    is SSLException -> TransportError.TLS
                    is ConnectException -> TransportError.NETWORK
                    is IOException -> TransportError.NETWORK
                    else -> TransportError.NETWORK
                }
                CheckOutcome(
                    transport = transport,
                    hasBalanceEndpoint = target.isBalance,
                    errorKind = if (transport == TransportError.TIMEOUT) {
                        ErrorKind.NETWORK_TIMEOUT
                    } else {
                        ErrorKind.BASE_URL_INVALID
                    },
                )
            }
        }
    }

    private fun Request.Builder.applyAuth(spec: PlatformSpec, rawKey: String): Request.Builder =
        when (spec.authStyle) {
            AuthStyle.BEARER -> header("Authorization", "Bearer $rawKey")
            AuthStyle.X_API_KEY -> header("x-api-key", rawKey)
            AuthStyle.X_GOOG_API_KEY -> header("x-goog-api-key", rawKey)
            AuthStyle.NONE -> this
        }

    private fun errorKindFor(code: Int, parseFailed: Boolean): ErrorKind? = when {
        code == 401 || code == 403 -> ErrorKind.KEY_INVALID
        code == 429 -> ErrorKind.RATE_LIMITED
        code in 500..599 -> ErrorKind.SERVER_ERROR
        parseFailed -> ErrorKind.PARSE_ERROR
        code in 200..299 -> null
        else -> ErrorKind.UNKNOWN
    }
}
