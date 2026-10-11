package com.modelscout.mobile.keyhub.checker

import com.modelscout.mobile.keyhub.AbortFlag
import com.modelscout.mobile.keyhub.KeyHubHttp
import com.modelscout.mobile.keyhub.model.KeyRecord
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 测试组 6：MockWebServer 引擎 3 例（并发上限 2 / 中途取消 / 429 不判失效）。 */
class KeyCheckEngineTest {

    private lateinit var server: MockWebServer
    private val paths = java.util.Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun checker() = KeyChecker(KeyHubHttp.create()) { spec, record ->
        Endpoint(
            server.url("/" + spec.platform.name + "/" + record.id).toString(),
            isBalance = spec.hasBalanceEndpoint,
        )
    }

    private fun record(id: String, platform: Platform = Platform.DEEPSEEK) = KeyRecord(
        id = id,
        rawKey = "sk-fake0000000000000000000000000000$id",
        platform = platform,
        addedAt = 0L,
    )

    private val deepSeekBalance =
        """{"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"10.00"}]}"""

    @Test
    fun `never exceeds the configured concurrency`() = runBlocking {
        val inFlight = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val now = inFlight.incrementAndGet()
                maxSeen.updateAndGet { maxOf(it, now) }
                Thread.sleep(120)
                inFlight.decrementAndGet()
                return MockResponse().setResponseCode(200).setBody(deepSeekBalance)
            }
        }

        val engine = KeyCheckEngine(checker())
        val records = (1..6).map { record("k$it") }
        val summary = engine.checkAll(records, concurrency = 2)

        assertEquals(6, summary.checked)
        assertEquals(6, summary.available)
        assertTrue("并发超过上限：实测 ${maxSeen.get()}", maxSeen.get() <= 2)
        assertEquals(6, server.requestCount)
    }

    @Test
    fun `abort flag stops the batch cooperatively`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Thread.sleep(80)
                return MockResponse().setResponseCode(200).setBody(deepSeekBalance)
            }
        }

        val abort = AbortFlag()
        val engine = KeyCheckEngine(checker())
        val records = (1..12).map { record("k$it") }

        val killer = launch {
            delay(150)
            abort.abort()
        }
        val summary = engine.checkAll(records, concurrency = 2, abort = abort)
        killer.join()

        assertTrue("应当标记为已取消", summary.cancelled)
        assertTrue("取消后不应把 12 个都测完，实测 ${summary.checked}", summary.checked < 12)
    }

    @Test
    fun `429 is check failed and 401 is invalid`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.path.orEmpty()
                return when {
                    request.path.orEmpty().contains("RATE") ->
                        MockResponse().setResponseCode(429).setBody("""{"error":"rate limited"}""")
                    else -> MockResponse().setResponseCode(401).setBody("""{"error":"invalid key"}""")
                }
            }
        }

        val engine = KeyCheckEngine(checker())
        val rateLimited = engine.checkOne(record("RATE", Platform.GROQ))
        assertEquals("429 绝不能判成已失效", KeyStatus.CHECK_FAILED, rateLimited.lastStatus)

        val bad = engine.checkOne(record("BAD", Platform.OPENAI))
        assertEquals(KeyStatus.INVALID, bad.lastStatus)
    }

    @Test
    fun `overriding platform routes the next check to the new endpoint`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.path.orEmpty()
                return MockResponse().setResponseCode(200).setBody(deepSeekBalance)
            }
        }

        val engine = KeyCheckEngine(checker())
        val original = record("k1", Platform.OPENAI)

        engine.checkOne(original)
        assertEquals("/OPENAI/k1", paths.last())

        // 用户手动改平台后重测，必须走新平台端点（决策 3）
        val switched = original.copy(platform = Platform.DEEPSEEK, platformOverridden = true)
        val updated = engine.checkOne(switched)

        assertEquals("/DEEPSEEK/k1", paths.last())
        assertEquals(KeyStatus.AVAILABLE, updated.lastStatus)
        assertEquals(10.0, updated.lastBalance!!, 1e-9)
        assertEquals("CNY", updated.lastCurrency)
    }
}
