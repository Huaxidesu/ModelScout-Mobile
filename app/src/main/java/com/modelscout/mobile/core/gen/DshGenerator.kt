package com.modelscout.mobile.core.gen

import com.modelscout.mobile.core.Constants
import com.modelscout.mobile.core.model.Capability
import com.modelscout.mobile.core.model.ModelResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

enum class DshFormat(val label: String, val extension: String) {
    JSONC("JSONC", "jsonc"),
    JSON("JSON", "json"),
    TOML("TOML", "toml"),
}

/**
 * DeepSeekHardness：输出 JSONC / JSON / TOML 三种形态让用户挑。
 * 界面须注明「格式基于公开资料假设，以官方 README 为准」。不用精雕。
 */
object DshGenerator {

    const val DISCLAIMER = "格式基于公开资料假设，以官方 README 为准。"

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    fun generate(input: GenerationInput, format: DshFormat): String = when (format) {
        DshFormat.JSONC -> jsonc(input)
        DshFormat.JSON -> prettyJson.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildTree(input),
        )
        DshFormat.TOML -> toml(input)
    }

    // ------------------------------------------------------------ JSON / JSONC

    private fun buildTree(input: GenerationInput) = buildJsonObject {
        putJsonObject("provider") {
            putJsonObject(input.providerKey) {
                put("npm", input.npm)
                put("name", input.providerName)
                putJsonObject("options") {
                    put("apiKey", input.apiKeyValue)
                    put("baseURL", input.baseUrl)
                }
                putJsonObject("models") {
                    input.models.forEach { model ->
                        putJsonObject(model.id) {
                            put("id", model.id)
                            put("name", model.displayName)
                            put("toolcall", model.tool != Capability.UNSUPPORTED)
                            put("attachment", model.vision == Capability.SUPPORTED)
                            put("reasoning", model.reasoning == Capability.SUPPORTED)
                            putJsonObject("limit") {
                                put("context", model.contextLimit ?: Constants.FALLBACK_CONTEXT_LIMIT)
                                put("output", model.outputLimit ?: Constants.FALLBACK_OUTPUT_LIMIT)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun jsonc(input: GenerationInput): String {
        val body = prettyJson.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildTree(input),
        )
        return buildString {
            appendLine("// DeepSeekHardness 配置 — 由 ModelScout 生成")
            appendLine("// $DISCLAIMER")
            append(body)
        }
    }

    // ------------------------------------------------------------ TOML

    private fun toml(input: GenerationInput): String = buildString {
        appendLine("# DeepSeekHardness 配置 — 由 ModelScout 生成")
        appendLine("# $DISCLAIMER")
        appendLine()
        appendLine("[provider.${q(input.providerKey)}]")
        appendLine("npm = ${str(input.npm)}")
        appendLine("name = ${str(input.providerName)}")
        appendLine()
        appendLine("[provider.${q(input.providerKey)}.options]")
        appendLine("apiKey = ${str(input.apiKeyValue)}")
        appendLine("baseURL = ${str(input.baseUrl)}")
        input.models.forEach { model ->
            val base = "provider.${q(input.providerKey)}.models.${q(model.id)}"
            appendLine()
            appendLine("[$base]")
            appendLine("id = ${str(model.id)}")
            appendLine("name = ${str(model.displayName)}")
            appendLine("toolcall = ${model.tool != Capability.UNSUPPORTED}")
            appendLine("attachment = ${model.vision == Capability.SUPPORTED}")
            appendLine("reasoning = ${model.reasoning == Capability.SUPPORTED}")
            appendLine()
            appendLine("[$base.limit]")
            appendLine("context = ${model.contextLimit ?: Constants.FALLBACK_CONTEXT_LIMIT}")
            appendLine("output = ${model.outputLimit ?: Constants.FALLBACK_OUTPUT_LIMIT}")
        }
    }

    private fun q(key: String): String = "\"" + key.replace("\"", "\\\"") + "\""

    private fun str(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
