package com.modelscout.mobile.core.parse

import com.modelscout.mobile.core.model.ModelEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * 计划书 2.2 /models 拉取解析，兼容三形态：
 *   {"data":[...]} / {"models":[...]} / 裸数组 [...]
 * id 必需（缺失回退 name / model，都没有则跳过），结果按 id 排序。
 *
 * 声明字段支持两种 schema：
 *  A. 扁平 OpenRouter 风格：`supported_parameters: ["tools","reasoning"]`、
 *     `input_modalities: ["text","image"]`、顶层 `max_context_length.value`。
 *  B. 嵌套模态 schema（Intern Discovery 等）：`input_modalities: [{type, supported_inputs.max_context_length}]`、
 *     `output_modalities: [{max_length, supported_parameters:{tools,reasoning,max_tokens.max}}]`。
 *
 * 声明优先于探测：命中即跳过对应探测请求（计划书 2.2）。
 */
object ModelsParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val IMAGE_NAMES = setOf("image", "图片", "图像", "vision", "multimodal", "多模态")
    private val TEXT_NAMES = setOf("text", "文本")

    fun parse(raw: String): List<ModelEntry> {
        val root = runCatching { json.parseToJsonElement(raw.trim()) }.getOrNull() ?: return emptyList()
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["data"] as? JsonArray)
                ?: (root["models"] as? JsonArray)
                ?: (root["result"] as? JsonArray)
                ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return array.mapNotNull { parseEntry(it) }.sortedBy { it.id }
    }

    private fun parseEntry(element: JsonElement): ModelEntry? {
        val obj = element as? JsonObject ?: return null
        val id = obj.stringOrNull("id")
            ?: obj.stringOrNull("name")
            ?: obj.stringOrNull("model")
            ?: return null
        if (id.isBlank()) return null

        // ---------------- 输入侧：模态 + 上下文长度 ----------------
        var declaredVision: Boolean? = null
        var declaredContext: Int? = null
        val inputModalities = obj["input_modalities"]
        if (inputModalities is JsonArray && inputModalities.isNotEmpty()) {
            var sawImage = false
            inputModalities.forEach { item ->
                when (item) {
                    is JsonPrimitive -> if (isImageName(item.content)) sawImage = true
                    is JsonObject -> {
                        val type = (item["type"] as? JsonPrimitive)?.content
                        if (isImageName(type)) sawImage = true
                        if (type == null || isTextName(type)) {
                            declaredContext = declaredContext ?: firstPositiveInt(
                                (item["supported_inputs"] as? JsonObject)?.get("max_context_length"),
                                item["max_context_length"],
                            )
                        }
                    }
                    else -> Unit
                }
            }
            // 模态声明是穷举的：没列 image 即明确不支持视觉
            declaredVision = sawImage
        }
        if (declaredContext == null) {
            declaredContext = firstPositiveInt(
                obj["max_context_length"],
                obj["context_length"],
                obj["max_context_tokens"],
                obj["max_model_len"],
            )
        }

        // ---------------- 输出侧：tools / reasoning / 输出上限 ----------------
        var declaredTool: Boolean? = null
        var declaredReasoning: Boolean? = null
        var declaredOutput: Int? = null
        (obj["output_modalities"] as? JsonArray)?.forEach { item ->
            val output = item as? JsonObject ?: return@forEach
            val params = output["supported_parameters"] as? JsonObject
            if (params != null) {
                // 「与值无关」：只要键存在即视为支持
                if (params.containsKey("tools")) declaredTool = true
                if (params.containsKey("reasoning")) declaredReasoning = true
            }
            declaredOutput = declaredOutput ?: firstPositiveInt(
                output["max_length"],
                (params?.get("max_tokens") as? JsonObject)?.get("max"),
            )
        }
        if (declaredOutput == null) {
            declaredOutput = firstPositiveInt(
                obj["max_output_tokens"],
                obj["max_completion_tokens"],
                (obj["top_provider"] as? JsonObject)?.get("max_completion_tokens"),
            )
        }

        // ---------------- 扁平 OpenRouter 风格兜底 ----------------
        val flatParams = obj["supported_parameters"].asStringList()
        if (declaredTool != true && flatParams.any { it.equals("tools", ignoreCase = true) }) {
            declaredTool = true
        }
        if (declaredReasoning != true && flatParams.any { it.equals("reasoning", ignoreCase = true) }) {
            declaredReasoning = true
        }
        if (declaredTool != true && obj.booleanOrNull("supports_tools") == true) declaredTool = true
        if (declaredTool != true && obj.booleanOrNull("tool_call") == true) declaredTool = true
        if (declaredReasoning != true && obj.booleanOrNull("supports_reasoning") == true) {
            declaredReasoning = true
        }
        if (declaredReasoning != true && obj.booleanOrNull("reasoning") == true) declaredReasoning = true
        if (declaredVision != true && obj.booleanOrNull("supports_vision") == true) declaredVision = true

        return ModelEntry(
            id = id,
            displayName = obj.stringOrNull("name") ?: id,
            declaredTool = declaredTool,
            declaredVision = declaredVision,
            declaredReasoning = declaredReasoning,
            declaredContext = declaredContext,
            declaredOutput = declaredOutput,
        )
    }

    private fun isImageName(value: String?): Boolean =
        value != null && IMAGE_NAMES.contains(value.trim().lowercase())

    private fun isTextName(value: String?): Boolean =
        value != null && TEXT_NAMES.contains(value.trim().lowercase())

    private fun JsonElement?.asStringList(): List<String> {
        val array = this as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.booleanOrNull(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    /** 支持裸数字、字符串数字、`{"value": 128000}`、`{"max": 262144}` 等写法。 */
    private fun firstPositiveInt(vararg elements: JsonElement?): Int? {
        for (element in elements) {
            extractInt(element)?.takeIf { it > 0 }?.let { return it }
        }
        return null
    }

    private fun extractInt(element: JsonElement?): Int? = when (element) {
        null -> null
        is JsonPrimitive -> element.content.trim().toIntOrNull()
        is JsonObject -> extractInt(element["value"])
            ?: extractInt(element["max"])
            ?: extractInt(element["context"])
        else -> null
    }
}
