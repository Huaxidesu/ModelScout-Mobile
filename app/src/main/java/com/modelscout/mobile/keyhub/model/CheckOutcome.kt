package com.modelscout.mobile.keyhub.model

/** 传输层失败原因。 */
enum class TransportError { TIMEOUT, DNS, TLS, NETWORK }

/**
 * 一次检测的原始产出。只描述事实，不含结论——结论一律由 [com.modelscout.mobile.keyhub.KeyClassifier] 产出。
 *
 * @param httpCode null 表示没拿到响应
 * @param transport 传输层失败
 * @param balance 从余额端点解析出的余额；null 表示解析失败
 * @param hasBalanceEndpoint 该平台本次是否走余额端点（false = 走有效性端点）
 * @param storageOnly 平台仅存储，不做检测
 */
data class CheckOutcome(
    val httpCode: Int? = null,
    val transport: TransportError? = null,
    val balance: Balance? = null,
    val hasBalanceEndpoint: Boolean = true,
    val storageOnly: Boolean = false,
    val errorKind: com.modelscout.mobile.core.model.ErrorKind? = null,
)
