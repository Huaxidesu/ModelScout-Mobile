package com.modelscout.mobile.keyhub

import com.modelscout.mobile.keyhub.model.CheckOutcome
import com.modelscout.mobile.keyhub.model.KeyStatus

/**
 * 分类单点。状态只能由这个纯函数产出，UI 禁止自行推断。
 *
 * | 输入 | 结论 |
 * |---|---|
 * | HTTP 401/403 | 已失效 |
 * | HTTP 402 | 余额不足 |
 * | HTTP 429 | 查询失败（限流） |
 * | 5xx / 超时 / DNS / TLS | 查询失败 |
 * | 200 + 余额>0 或 limit=null | 可用 |
 * | 200 + 余额<=0 | 余额不足 |
 * | 200 + 有效性端点通过（无余额端点平台） | 可用（余额 —） |
 * | 200 + 响应解析失败 | 查询失败 |
 * | 平台仅存储 | 待检测 |
 *
 * 决策 6：429 一律归「查询失败」，绝不判「已失效」。
 */
object KeyClassifier {

    fun classify(outcome: CheckOutcome): KeyStatus {
        if (outcome.storageOnly) return KeyStatus.PENDING
        if (outcome.transport != null) return KeyStatus.CHECK_FAILED

        val code = outcome.httpCode ?: return KeyStatus.CHECK_FAILED
        return when {
            code == 401 || code == 403 -> KeyStatus.INVALID
            code == 402 -> KeyStatus.INSUFFICIENT
            code == 429 -> KeyStatus.CHECK_FAILED
            code in 500..599 -> KeyStatus.CHECK_FAILED
            code in 200..299 -> classifySuccess(outcome)
            else -> KeyStatus.CHECK_FAILED
        }
    }

    private fun classifySuccess(outcome: CheckOutcome): KeyStatus {
        // 无余额端点的平台：有效性端点通过即「可用」，余额显示 —
        if (!outcome.hasBalanceEndpoint) return KeyStatus.AVAILABLE
        val balance = outcome.balance ?: return KeyStatus.CHECK_FAILED // 响应解析失败
        if (balance.unlimited) return KeyStatus.AVAILABLE
        val amount = balance.amount ?: return KeyStatus.CHECK_FAILED
        return if (amount > 0) KeyStatus.AVAILABLE else KeyStatus.INSUFFICIENT
    }
}
