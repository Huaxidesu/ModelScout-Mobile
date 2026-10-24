package com.modelscout.mobile.core.gen

import com.modelscout.mobile.core.Constants
import com.modelscout.mobile.core.model.Capability
import com.modelscout.mobile.core.model.ModelResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * OpenCode 配置生成（重点，有两个坑）。
 *
 * ★ toolcall / attachment / reasoning 必须是顶层字段，写成 capabilities:{...} 会过不了 schema 校验。
 * ★ limit.context / limit.output 均必填。
 */
object OpenCodeGenerator {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    fun generate(input: GenerationInput): String {
        val root = buildJsonObject {
            putJsonObject("provider") {
                putJsonObject(input.providerKey) {
                    put("npm", input.npm)
                    put("name", input.providerName)
                    putJsonObject("options") {
                        put("apiKey", input.apiKeyValue)
                        put("baseURL", input.baseUrl)
                    }
                    putJsonObject("models") {
                        input.models.forEach { model -> putModel(model) }
                    }
                }
            }
        }
        return prettyJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putModel(model: ModelResult) {
        putJsonObject(model.id) {
            put("id", model.id)
            put("name", model.displayName)
            // ★ 顶层能力字段（不是 capabilities）
            put("toolcall", model.tool != Capability.UNSUPPORTED)
            put("attachment", model.vision == Capability.SUPPORTED)
            put("reasoning", model.reasoning == Capability.SUPPORTED)
            // ★ limit.context / limit.output 必填
            putJsonObject("limit") {
                put("context", model.contextLimit ?: Constants.FALLBACK_CONTEXT_LIMIT)
                put("output", model.outputLimit ?: Constants.FALLBACK_OUTPUT_LIMIT)
            }
        }
    }
}
