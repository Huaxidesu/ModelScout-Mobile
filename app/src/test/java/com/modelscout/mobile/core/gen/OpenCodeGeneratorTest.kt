package com.modelscout.mobile.core.gen

import com.modelscout.mobile.core.model.Capability
import com.modelscout.mobile.core.model.LimitSource
import com.modelscout.mobile.core.model.ModelResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 测试组 4：OpenCode 生成快照（校验顶层 toolcall + limit 必填）。 */
class OpenCodeGeneratorTest {

    private val input = GenerationInput(
        providerKey = "deepseek",
        providerName = "DeepSeek",
        npm = "@ai-sdk/deepseek",
        baseUrl = "https://api.deepseek.com/v1",
        envVar = "DEEPSEEK_API_KEY",
        models = listOf(
            ModelResult(
                id = "deepseek-chat",
                displayName = "DeepSeek Chat",
                tool = Capability.SUPPORTED,
                vision = Capability.UNSUPPORTED,
                reasoning = Capability.SUPPORTED,
                contextLimit = 65536,
                outputLimit = 8192,
                limitSource = LimitSource.MEASURED,
            ),
        ),
    )

    @Test
    fun `emits opencode schema with top level capability flags and required limit`() {
        val json = Json.parseToJsonElement(OpenCodeGenerator.generate(input)) as JsonObject

        val provider = (json["provider"] as JsonObject)["deepseek"] as JsonObject
        assertEquals("@ai-sdk/deepseek", (provider["npm"] as JsonPrimitive).content)
        assertEquals("DeepSeek", (provider["name"] as JsonPrimitive).content)

        val options = provider["options"] as JsonObject
        assertEquals("{env:DEEPSEEK_API_KEY}", (options["apiKey"] as JsonPrimitive).content)
        assertEquals("https://api.deepseek.com/v1", (options["baseURL"] as JsonPrimitive).content)

        val model = (provider["models"] as JsonObject)["deepseek-chat"] as JsonObject

        // ★ toolcall / attachment / reasoning 必须是顶层字段
        assertTrue("toolcall 必须在顶层", model.containsKey("toolcall"))
        assertTrue("attachment 必须在顶层", model.containsKey("attachment"))
        assertTrue("reasoning 必须在顶层", model.containsKey("reasoning"))
        assertFalse("不允许出现 capabilities 包装", model.containsKey("capabilities"))

        assertTrue((model["toolcall"] as JsonPrimitive).boolean)
        assertFalse((model["attachment"] as JsonPrimitive).boolean)
        assertTrue((model["reasoning"] as JsonPrimitive).boolean)

        // ★ limit.context / limit.output 均必填
        val limit = model["limit"] as JsonObject
        assertNotNull(limit["context"])
        assertNotNull(limit["output"])
        assertEquals(65536, (limit["context"] as JsonPrimitive).int)
        assertEquals(8192, (limit["output"] as JsonPrimitive).int)
    }

    @Test
    fun `fills fallback limit values when unknown so schema stays valid`() {
        val unknown = input.copy(
            models = listOf(ModelResult(id = "mystery-model")),
            envVar = null,
            apiKeyLiteral = "sk-plain-text-key-value",
        )
        val json = Json.parseToJsonElement(OpenCodeGenerator.generate(unknown)) as JsonObject
        val model = ((json["provider"] as JsonObject)["deepseek"] as JsonObject)["models"] as JsonObject
        val limit = (model["mystery-model"] as JsonObject)["limit"] as JsonObject
        assertTrue((limit["context"] as JsonPrimitive).int > 0)
        assertTrue((limit["output"] as JsonPrimitive).int > 0)

        val options = ((json["provider"] as JsonObject)["deepseek"] as JsonObject)["options"] as JsonObject
        assertEquals("sk-plain-text-key-value", (options["apiKey"] as JsonPrimitive).content)
    }
}
