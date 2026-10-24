package com.modelscout.mobile.keyhub.checker

import com.modelscout.mobile.keyhub.PlatformSpec
import com.modelscout.mobile.keyhub.model.Balance
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 余额解析。解析路径以实际响应为准，多候选路径容错。
 * 路径语法：点分 + `[n]` 索引，如 `balance_infos[0].total_balance`。
 */
object BalanceParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val SEGMENT = Regex("^([A-Za-z0-9_\\-]*)((?:\\[\\d+])*)$")

    /** 解析失败返回 null（由 KeyClassifier 判「查询失败」）。 */
    fun parse(body: String, spec: PlatformSpec): Balance? {
        if (body.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(body.trim()) }.getOrNull() ?: return null
        if (root !is JsonObject) return null

        // 无上限：该路径的父节点存在，但子节点缺失或为 null
        spec.unlimitedWhenPathNull?.let { path ->
            val parent = resolveParent(root, path)
            if (parent != null) {
                val child = resolve(root, path)
                if (child == null || child is JsonNull) {
                    return Balance(unlimited = true, currency = spec.defaultCurrency)
                }
            }
        }

        val amount = spec.balanceAmountPaths.firstNotNullOfOrNull { path ->
            resolve(root, path)?.let { toDouble(it) }
        } ?: return null

        val subtract = spec.balanceSubtractPath?.let { path ->
            resolve(root, path)?.let { toDouble(it) }
        } ?: 0.0

        val currency = spec.balanceCurrencyPaths.firstNotNullOfOrNull { path ->
            resolve(root, path)?.let { toStringOrNull(it) }
        } ?: spec.defaultCurrency

        return Balance(amount = amount - subtract, currency = currency)
    }

    private fun resolve(root: JsonElement, path: String): JsonElement? {
        var current: JsonElement = root
        for (segment in path.split('.')) {
            if (segment.isEmpty()) continue
            val match = SEGMENT.matchEntire(segment) ?: return null
            val name = match.groupValues[1]
            if (name.isNotEmpty()) {
                current = (current as? JsonObject)?.get(name) ?: return null
            }
            Regex("\\[(\\d+)]").findAll(match.groupValues[2]).forEach { idx ->
                val i = idx.groupValues[1].toIntOrNull() ?: return null
                current = (current as? JsonArray)?.getOrNull(i) ?: return null
            }
        }
        return current
    }

    private fun resolveParent(root: JsonElement, path: String): JsonElement? {
        val cut = path.lastIndexOf('.')
        if (cut <= 0) return root
        return resolve(root, path.substring(0, cut))
    }

    private fun toDouble(element: JsonElement): Double? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> element.content.trim().toDoubleOrNull()
        else -> null
    }

    private fun toStringOrNull(element: JsonElement): String? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> element.content.trim().takeIf { it.isNotEmpty() }
        else -> null
    }
}
