package com.modelscout.mobile.core.parse

import com.modelscout.mobile.core.model.Capability
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 计划书 2.3 探测结果三态判定。
 * 2xx -> 支持；4xx 且报错含关键词 -> 不支持；其他（429/401 等）-> 未知。
 */
object CapabilityJudge {

    private val TOOL_HINTS = listOf("tool", "function", "tool_choice", "工具")
    private val VISION_HINTS = listOf("image", "vision", "multimodal", "图片", "多模态")

    /**
     * 基础设施类失败（图片没送达/解码失败），不能当成「模型不支持视觉」。
     * 实测 Intern Discovery 网关：图片被判 "image file is truncated" 时，
     * 真视觉模型也会返回 400，若直接判 UNSUPPORTED 就是误报。
     */
    private val IMAGE_LOAD_FAILURE_HINTS = listOf(
        "truncated",
        "failed to load image",
        "could not load image",
        "unable to load image",
        "invalid image",
        "failed to decode",
        "image decode",
        "image file is",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun judge(code: Int, body: String, vision: Boolean): Capability {
        if (code in 200..299) return Capability.SUPPORTED
        if (code in 400..499) {
            val hay = body.lowercase()
            if (vision && IMAGE_LOAD_FAILURE_HINTS.any { hay.contains(it) }) {
                return Capability.UNKNOWN
            }
            val hints = if (vision) VISION_HINTS else TOOL_HINTS
            return if (hints.any { hay.contains(it.lowercase()) }) Capability.UNSUPPORTED else Capability.UNKNOWN
        }
        return Capability.UNKNOWN
    }

    /**
     * 上一步响应体的 reasoning_content / .reasoning 非空判定（计划书 2.3）。
     * 支持字符串与数组两种形态（部分网关返回 [{type:text,text:...}]）。
     */
    fun hasReasoningContent(body: String): Boolean {
        if (body.isBlank()) return false
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return false
        val choices = root["choices"] as? JsonArray ?: return false
        for (choice in choices) {
            val obj = choice as? JsonObject ?: continue
            val message = (obj["message"] as? JsonObject) ?: (obj["delta"] as? JsonObject) ?: continue
            if (isNonEmpty(message["reasoning_content"])) return true
            if (isNonEmpty(message["reasoning"])) return true
        }
        return false
    }

    private fun isNonEmpty(element: kotlinx.serialization.json.JsonElement?): Boolean = when (element) {
        null -> false
        is JsonPrimitive -> element.content.isNotBlank()
        is JsonArray -> element.any { isNonEmpty(it) }
        is JsonObject -> (element["text"] as? JsonPrimitive)?.content?.isNotBlank() == true
        else -> false
    }
}
