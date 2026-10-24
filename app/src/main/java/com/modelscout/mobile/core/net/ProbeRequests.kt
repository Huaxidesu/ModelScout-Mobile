package com.modelscout.mobile.core.net

import com.modelscout.mobile.core.Constants
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 探测请求体：字段与上游探测协议一一对应，修改需同步验证脚本。
 */
object ProbeRequests {

    /** 工具探测 POST /chat/completions */
    fun toolProbe(modelId: String): String = buildJsonObject {
        put("model", modelId)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                put("content", "hi")
            }
        }
        put("max_tokens", Constants.PROBE_MAX_TOKENS)
        putJsonArray("tools") {
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", Constants.TOOL_PROBE_NAME)
                    put("description", "A no-op probe function used to detect tool-calling support.")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("echo") {
                                put("type", "string")
                                put("description", "Echo back the value.")
                            }
                        }
                        putJsonArray("required") { add("echo") }
                    }
                }
            }
        }
        put("tool_choice", "auto")
    }.toString()

    /** 视觉探测：content 改为数组 [text, image_url(1x1 透明 PNG, detail=low)] */
    fun visionProbe(modelId: String): String = buildJsonObject {
        put("model", modelId)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", "ok?")
                    }
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put(
                                "url",
                                Constants.PROBE_IMAGE_DATA_URL_PREFIX + Constants.PROBE_IMAGE_PNG_BASE64,
                            )
                            put("detail", "low")
                        }
                    }
                }
            }
        }
        put("max_tokens", Constants.PROBE_MAX_TOKENS)
    }.toString()

    /** 上限探测：max_tokens = 999999 */
    fun limitProbe(modelId: String): String = buildJsonObject {
        put("model", modelId)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                put("content", "hi")
            }
        }
        put("max_tokens", Constants.LIMIT_PROBE_MAX_TOKENS)
    }.toString()
}
