package com.modelscout.mobile.core.dict

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

data class DictEntry(
    val match: String,
    val context: Int? = null,
    val output: Int? = null,
)

/**
 * 计划书 2.4：内置字典 assets/model_params.json（首版 20 条）。
 * 匹配规则：先精确 id，再子串匹配（取最长 match 命中）。
 */
class ModelParamDictionary(private val entries: List<DictEntry>) {

    fun lookup(modelId: String): DictEntry? {
        val id = modelId.lowercase()
        entries.firstOrNull { it.match.equals(modelId, ignoreCase = true) }?.let { return it }
        return entries
            .filter { it.match.isNotBlank() && id.contains(it.match.lowercase()) }
            .maxByOrNull { it.match.length }
    }

    val size: Int get() = entries.size

    companion object {
        val EMPTY = ModelParamDictionary(emptyList())

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun fromJson(raw: String): ModelParamDictionary {
            val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return EMPTY
            val array = root["entries"] as? JsonArray ?: JsonArray(emptyList())
            val entries = array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val match = (obj["match"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                DictEntry(
                    match = match,
                    context = obj.positiveInt("context"),
                    output = obj.positiveInt("output"),
                )
            }
            return ModelParamDictionary(entries)
        }

        private fun JsonObject.positiveInt(key: String): Int? =
            (this[key] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
    }
}
