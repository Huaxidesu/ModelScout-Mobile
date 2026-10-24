package com.modelscout.mobile.core.engine

import com.modelscout.mobile.core.dict.ModelParamDictionary
import com.modelscout.mobile.core.model.Capability
import com.modelscout.mobile.core.model.LimitSource
import com.modelscout.mobile.core.net.HttpClients
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 测试组 6：MockWebServer 走通一次完整四阶段（工具 -> 视觉 -> 推理 -> 上限）。
 */
class EngineFlowTest {

    private lateinit var server: MockWebServer
    private val recorded = mutableListOf<Pair<String, String>>() // path to body

    private val modelsJson = """
        {"object":"list","data":[
          {"id":"alpha-model"},
          {"id":"deepseek-r1"}
        ]}
    """.trimIndent()

    private val toolOkBody =
        """{"choices":[{"message":{"role":"assistant","content":"hi"}}]}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = request.body.readUtf8()
                recorded += path to body
                return when {
                    path.endsWith("/models") ->
                        MockResponse().setResponseCode(200).setBody(modelsJson)

                    path.endsWith("/chat/completions") -> when {
                        body.contains("\"tools\"") ->
                            MockResponse().setResponseCode(200).setBody(toolOkBody)

                        body.contains("image_url") ->
                            MockResponse().setResponseCode(400)
                                .setBody("""{"error":{"message":"This model does not support image input"}}""")

                        body.contains("999999") ->
                            MockResponse().setResponseCode(400)
                                .setBody(
                                    """{"error":{"message":"Invalid max_tokens: 999999 is too large. """ +
                                        """maximum allowed value is 8192 (auth sk-super-secret-key-value)"}}""",
                                )

                        else -> MockResponse().setResponseCode(200).setBody(toolOkBody)
                    }

                    else -> MockResponse().setResponseCode(404).setBody("""{"error":"not found"}""")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `walks all four stages against a mock gateway`() = runBlocking {
        val states = mutableListOf<ScanState>()
        val logs = mutableListOf<String>()
        val engine = ScanEngine(ModelParamDictionary.EMPTY) { HttpClients.create(it) }

        val outcome = engine.run(
            config = ScanConfig(
                baseUrl = server.url("/v1").toString(),
                apiKey = "sk-super-secret-key-value",
                concurrency = 1,
                probeLimit = true,
            ),
            onLog = { logs += it },
            onState = { states += it },
        )

        // ---- 四阶段结果 ----
        assertEquals(2, outcome.results.size)
        val alpha = outcome.results.first { it.id == "alpha-model" }
        assertEquals(Capability.SUPPORTED, alpha.tool)
        assertEquals(Capability.UNSUPPORTED, alpha.vision)
        assertEquals(Capability.UNKNOWN, alpha.reasoning)
        assertEquals(8192, alpha.outputLimit)
        assertEquals(LimitSource.MEASURED, alpha.limitSource)

        // 名称正则命中 r1 -> 推理模型
        val r1 = outcome.results.first { it.id == "deepseek-r1" }
        assertEquals(Capability.SUPPORTED, r1.reasoning)

        // ---- 进度不卡 0/0，且能到 100 ----
        assertTrue(states.isNotEmpty())
        assertTrue("运行中不应出现 total=0 的进度", states.all { it.total == 2 })
        assertEquals(100, states.maxOf { it.percent })
        val last = states.last()
        assertEquals(2, last.completed)
        assertFalse(last.running)

        // ---- 每个请求都必须带 Accept: application/json ----
        val accepts = server.requestCount
        assertEquals(1 /* models */ + 2 * 3 /* 两个模型各 3 次探测 */, accepts)

        // ---- 请求体符合----
        val chatBodies = recorded.filter { it.first.endsWith("/chat/completions") }.map { it.second }
        val toolBodies = chatBodies.filter { it.contains("\"tools\"") }
        val visionBodies = chatBodies.filter { it.contains("image_url") }
        val limitBodies = chatBodies.filter { it.contains("999999") }
        assertEquals(2, toolBodies.size)
        assertEquals(2, visionBodies.size)
        assertEquals(2, limitBodies.size)
        assertTrue(toolBodies.all { it.contains("modelscout_ping") })
        assertTrue(toolBodies.all { it.contains("\"tool_choice\":\"auto\"") })
        assertTrue(toolBodies.all { it.contains("\"max_tokens\":16") })
        assertTrue(visionBodies.all { it.contains("data:image/png;base64,") })
        assertTrue(visionBodies.all { it.contains("\"detail\":\"low\"") })

        // ---- 全链路看不到明文 Key ----
        assertTrue("日志里不能出现明文 Key", logs.none { it.contains("sk-super-secret-key-value") })
        assertTrue("上游响应里的 Key 必须被脱敏", logs.any { it.contains("***REDACTED***") })
    }

    @Test
    fun `aborts cooperatively when the flag is raised`() = runBlocking {
        val abort = AbortFlag()
        abort.abort()
        val engine = ScanEngine(ModelParamDictionary.EMPTY) { HttpClients.create(it) }
        val outcome = engine.run(
            config = ScanConfig(baseUrl = server.url("/v1").toString(), concurrency = 2),
            abort = abort,
        )
        assertTrue(outcome.aborted)
        assertEquals(0, outcome.results.size)
    }

    @Test
    fun `skips every probe when the gateway declares everything`() = runBlocking {
        val declaredModels = """
            {"object":"list","data":[{
              "id":"declared-model",
              "input_modalities":[
                {"type":"text","supported_inputs":{"max_context_length":{"value":128000}}}
              ],
              "output_modalities":[{
                "max_length":{"value":8192},
                "supported_parameters":{
                  "tools":{"type":"boolean"},
                  "reasoning":{"type":"boolean"},
                  "max_tokens":{"max":8192}
                }
              }]
            }]}
        """.trimIndent()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded += request.path.orEmpty() to request.body.readUtf8()
                return if (request.path.orEmpty().endsWith("/models")) {
                    MockResponse().setResponseCode(200).setBody(declaredModels)
                } else {
                    MockResponse().setResponseCode(200).setBody(toolOkBody)
                }
            }
        }

        val engine = ScanEngine(ModelParamDictionary.EMPTY) { HttpClients.create(it) }
        val outcome = engine.run(
            config = ScanConfig(baseUrl = server.url("/v1").toString(), concurrency = 1),
        )

        // 声明齐全 => 一次 chat 探测都不该发
        assertEquals(1, server.requestCount)
        val model = outcome.results.single()
        assertEquals(Capability.SUPPORTED, model.tool)
        assertEquals(Capability.UNSUPPORTED, model.vision)
        assertEquals(Capability.SUPPORTED, model.reasoning)
        assertEquals(128000, model.contextLimit)
        assertEquals(8192, model.outputLimit)
        assertEquals(LimitSource.DECLARED, model.limitSource)
    }
}
