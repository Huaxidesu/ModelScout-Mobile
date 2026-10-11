package com.modelscout.mobile.core.engine

import com.modelscout.mobile.core.dict.ModelParamDictionary
import com.modelscout.mobile.core.model.Capability
import com.modelscout.mobile.core.model.LimitSource
import com.modelscout.mobile.core.model.ModelResult
import com.modelscout.mobile.core.net.HttpClients
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * 真实网关冒烟测试（默认跳过）。
 *
 * 运行方式（凭证走 Gradle 属性，不落仓库）：
 * ```
 * ./gradlew testDebugUnitTest --tests "*RealGatewayIT*" \
 *   -PrealGateway=1 -PrealBaseUrl=https://xxx/v1 -PrealApiKey=sk-xxx
 * ```
 */
class RealGatewayIT {

    @Before
    fun requireOptIn() {
        Assume.assumeTrue(
            "未开启真实网关测试（加 -PrealGateway=1 才跑）",
            System.getProperty("modelscout.real") == "1",
        )
    }

    /** 期望值：与用户提供的「正确答案」表一致。 */
    private data class Expected(
        val id: String,
        val context: Int,
        val vision: Boolean,
    )

    private val expected = listOf(
        Expected("Agents-A1", 262144, true),
        Expected("Atria-Dawn-Preview", 524288, false),
        Expected("deepseek-v4-flash-0731", 1048576, false),
        Expected("deepseek-v4-flash-vision", 1048576, true),
        Expected("deepseek-v4-pro-0813", 1048576, false),
        Expected("glm-5.3", 1048576, false),
        Expected("intern-s2", 262144, true),
        Expected("kimi-k2.6", 262144, true),
        Expected("minimax-m3", 1048576, false),
        Expected("qwen3.8-27b", 262144, true),
    )

    @Test
    fun `scan matches the expected capability table`() = runBlocking {
        val baseUrl = System.getProperty("modelscout.baseUrl").orEmpty()
        val apiKey = System.getProperty("modelscout.apiKey").orEmpty()
        assertTrue("必须提供 -PrealBaseUrl", baseUrl.isNotBlank())
        assertTrue("必须提供 -PrealApiKey", apiKey.isNotBlank())

        val engine = ScanEngine(ModelParamDictionary.EMPTY) { HttpClients.create(it) }
        val outcome = engine.run(
            config = ScanConfig(baseUrl = baseUrl, apiKey = apiKey, concurrency = 3),
            onLog = { println("[log] $it") },
        )

        println()
        println("=".repeat(96))
        println(
            "%-26s %-10s %-8s %-8s %-10s %-8s %-10s".format(
                "模型 ID", "上下文", "工具", "视觉", "推理", "输出", "上限来源",
            ),
        )
        println("-".repeat(96))
        outcome.results.forEach { r ->
            println(
                "%-26s %-10s %-8s %-8s %-10s %-8s %-10s".format(
                    r.id,
                    r.contextLimit?.toString() ?: "-",
                    r.tool.name,
                    r.vision.name,
                    r.reasoning.name,
                    r.outputLimit?.toString() ?: "-",
                    r.limitSource?.name ?: "-",
                ),
            )
        }
        println("=".repeat(96))
        println("共 ${outcome.results.size} 个模型，fatalError=${outcome.fatalError}")
        println()

        assertEquals("模型数量不符", expected.size, outcome.results.size)
        val byId: Map<String, ModelResult> = outcome.results.associateBy { it.id }

        expected.forEach { want ->
            val got = byId[want.id] ?: error("缺少模型 ${want.id}")
            assertEquals("${want.id} 上下文长度不符", want.context, got.contextLimit)
            assertEquals(
                "${want.id} 视觉能力不符",
                if (want.vision) Capability.SUPPORTED else Capability.UNSUPPORTED,
                got.vision,
            )
            assertEquals("${want.id} 工具调用应为支持", Capability.SUPPORTED, got.tool)
            assertEquals("${want.id} 上限来源应为声明", LimitSource.DECLARED, got.limitSource)
        }
    }
}
