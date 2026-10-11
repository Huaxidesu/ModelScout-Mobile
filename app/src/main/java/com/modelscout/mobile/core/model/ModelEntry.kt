package com.modelscout.mobile.core.model

/** 能力三态。 */
enum class Capability { SUPPORTED, UNSUPPORTED, UNKNOWN }

/** 上限值来源角标：实测 / 字典 / 声明 / 手动。 */
enum class LimitSource(val label: String) {
    DECLARED("声明"),
    DICTIONARY("字典"),
    MEASURED("实测"),
    MANUAL("手动"),
}

/** 单模型流水线阶段，权重即进度百分比。 */
enum class Stage(val weight: Int) {
    TOOL(20),
    VISION(40),
    REASONING(60),
    LIMIT(80),
    DONE(100),
}

/**
 * /models 拉取到的条目。
 * declaredXxx 为 null 表示「网关没有声明」，不代表不支持。
 */
data class ModelEntry(
    val id: String,
    val displayName: String = id,
    val declaredTool: Boolean? = null,
    val declaredVision: Boolean? = null,
    val declaredReasoning: Boolean? = null,
    val declaredContext: Int? = null,
    val declaredOutput: Int? = null,
)

/** 扫描结果。 */
data class ModelResult(
    val id: String,
    val displayName: String = id,
    val tool: Capability = Capability.UNKNOWN,
    val vision: Capability = Capability.UNKNOWN,
    val reasoning: Capability = Capability.UNKNOWN,
    val contextLimit: Int? = null,
    val outputLimit: Int? = null,
    val limitSource: LimitSource? = null,
    val selected: Boolean = true,
    val note: String? = null,
) {
    fun withLimits(context: Int?, output: Int?, source: LimitSource?, note: String? = this.note) =
        copy(contextLimit = context, outputLimit = output, limitSource = source, note = note)
}
