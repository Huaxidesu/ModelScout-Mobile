package com.modelscout.mobile.core.model

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 计划书 2.7 错误表，各配一句中文文案。
 * endpointLevel = true 表示端点级错误：整轮直接终止，不重试其他模型。
 */
enum class ErrorKind(val message: String, val endpointLevel: Boolean = false) {
    KEY_INVALID("API Key 无效或没有访问权限（401/403/TLS 握手失败）", true),
    BASE_URL_INVALID("Base URL 无法访问（404/DNS 解析失败），请检查是否缺少 /v1 路径", true),
    NETWORK_TIMEOUT("网络超时，请检查网络或调大超时时间"),
    RATE_LIMITED("请求过于频繁（429），请降低并发数或稍后重试"),
    SERVER_ERROR("服务端错误（5xx），请稍后重试"),
    MODEL_NOT_FOUND("模型不存在（404 model not found）", true),
    PARSE_ERROR("响应解析失败"),
    CANCELLED("已中止"),
    UNKNOWN("未知错误"),
}

data class AppError(
    val kind: ErrorKind,
    val detail: String? = null,
    val httpCode: Int? = null,
) {
    val message: String get() = kind.message

    override fun toString(): String =
        buildString {
            append(kind.name)
            httpCode?.let { append(" [HTTP ").append(it).append(']') }
            detail?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
        }
}

object ErrorClassifier {

    private val MODEL_NOT_FOUND_HINTS = listOf("model not found", "模型不存在", "model_not_found", "no such model")

    fun classify(code: Int, body: String?, throwable: Throwable? = null): AppError {
        if (throwable != null) {
            return when (throwable) {
                is SocketTimeoutException -> AppError(ErrorKind.NETWORK_TIMEOUT, throwable.message)
                is UnknownHostException -> AppError(ErrorKind.BASE_URL_INVALID, throwable.message)
                is ConnectException -> AppError(ErrorKind.BASE_URL_INVALID, throwable.message)
                is SSLException -> AppError(ErrorKind.KEY_INVALID, throwable.message)
                is IOException -> AppError(ErrorKind.BASE_URL_INVALID, throwable.message)
                else -> AppError(ErrorKind.UNKNOWN, throwable.message)
            }
        }
        val hay = body.orEmpty().lowercase()
        return when {
            code == 401 || code == 403 -> AppError(ErrorKind.KEY_INVALID, null, code)
            code == 429 -> AppError(ErrorKind.RATE_LIMITED, null, code)
            code == 404 && MODEL_NOT_FOUND_HINTS.any { hay.contains(it) } ->
                AppError(ErrorKind.MODEL_NOT_FOUND, null, code)
            code == 404 -> AppError(ErrorKind.BASE_URL_INVALID, null, code)
            code in 500..599 -> AppError(ErrorKind.SERVER_ERROR, null, code)
            else -> AppError(ErrorKind.UNKNOWN, null, code)
        }
    }
}
