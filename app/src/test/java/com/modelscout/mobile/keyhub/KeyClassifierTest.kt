package com.modelscout.mobile.keyhub

import com.modelscout.mobile.keyhub.model.Balance
import com.modelscout.mobile.keyhub.model.CheckOutcome
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.TransportError
import org.junit.Assert.assertEquals
import org.junit.Test

/** 测试组 2：分类矩阵 12 例（计划书 §4.4）。UI 不得绕过这个函数自行推断。 */
class KeyClassifierTest {

    @Test
    fun `401 is invalid`() =
        assertEquals(KeyStatus.INVALID, KeyClassifier.classify(CheckOutcome(httpCode = 401)))

    @Test
    fun `403 is invalid`() =
        assertEquals(KeyStatus.INVALID, KeyClassifier.classify(CheckOutcome(httpCode = 403)))

    @Test
    fun `402 is insufficient`() =
        assertEquals(KeyStatus.INSUFFICIENT, KeyClassifier.classify(CheckOutcome(httpCode = 402)))

    @Test
    fun `429 is check failed and never invalid`() {
        // 决策 6：429 一律归「查询失败」，绝不判「已失效」
        assertEquals(KeyStatus.CHECK_FAILED, KeyClassifier.classify(CheckOutcome(httpCode = 429)))
    }

    @Test
    fun `500 is check failed`() =
        assertEquals(KeyStatus.CHECK_FAILED, KeyClassifier.classify(CheckOutcome(httpCode = 503)))

    @Test
    fun `timeout is check failed`() =
        assertEquals(
            KeyStatus.CHECK_FAILED,
            KeyClassifier.classify(CheckOutcome(transport = TransportError.TIMEOUT)),
        )

    @Test
    fun `dns failure is check failed`() =
        assertEquals(
            KeyStatus.CHECK_FAILED,
            KeyClassifier.classify(CheckOutcome(transport = TransportError.DNS)),
        )

    @Test
    fun `tls failure is check failed`() =
        assertEquals(
            KeyStatus.CHECK_FAILED,
            KeyClassifier.classify(CheckOutcome(transport = TransportError.TLS)),
        )

    @Test
    fun `200 with positive balance is available`() =
        assertEquals(
            KeyStatus.AVAILABLE,
            KeyClassifier.classify(
                CheckOutcome(httpCode = 200, balance = Balance(amount = 12.5, currency = "CNY")),
            ),
        )

    @Test
    fun `200 with zero balance is insufficient`() =
        assertEquals(
            KeyStatus.INSUFFICIENT,
            KeyClassifier.classify(
                CheckOutcome(httpCode = 200, balance = Balance(amount = 0.0, currency = "CNY")),
            ),
        )

    @Test
    fun `200 with negative balance is insufficient`() =
        assertEquals(
            KeyStatus.INSUFFICIENT,
            KeyClassifier.classify(
                CheckOutcome(httpCode = 200, balance = Balance(amount = -3.0, currency = "USD")),
            ),
        )

    @Test
    fun `200 with unlimited balance is available`() =
        assertEquals(
            KeyStatus.AVAILABLE,
            KeyClassifier.classify(
                CheckOutcome(httpCode = 200, balance = Balance(unlimited = true, currency = "USD")),
            ),
        )

    @Test
    fun `200 with unparsable balance is check failed`() =
        assertEquals(
            KeyStatus.CHECK_FAILED,
            KeyClassifier.classify(CheckOutcome(httpCode = 200, balance = null)),
        )

    @Test
    fun `200 from validity endpoint is available with dash balance`() =
        assertEquals(
            KeyStatus.AVAILABLE,
            KeyClassifier.classify(CheckOutcome(httpCode = 200, hasBalanceEndpoint = false)),
        )

    @Test
    fun `storage only platform stays pending`() =
        assertEquals(
            KeyStatus.PENDING,
            KeyClassifier.classify(CheckOutcome(storageOnly = true, httpCode = 200)),
        )

    @Test
    fun `status tone mapping is centralised`() {
        assertEquals(com.modelscout.mobile.keyhub.model.StatusTone.GREEN, KeyStatus.AVAILABLE.tone)
        assertEquals(com.modelscout.mobile.keyhub.model.StatusTone.ORANGE, KeyStatus.INSUFFICIENT.tone)
        assertEquals(com.modelscout.mobile.keyhub.model.StatusTone.RED, KeyStatus.INVALID.tone)
        assertEquals(com.modelscout.mobile.keyhub.model.StatusTone.GRAY, KeyStatus.CHECK_FAILED.tone)
        assertEquals(com.modelscout.mobile.keyhub.model.StatusTone.OUTLINE, KeyStatus.PENDING.tone)
    }
}
