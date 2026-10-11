package com.modelscout.mobile.ui

import com.modelscout.mobile.BuildConfig

/** 应用元信息与外部链接，集中一处便于改。 */
object AppInfo {
    const val NAME = "ModelScout"

    /** 版本号唯一来源是 gradle/libs.versions.toml，这里只做读取，不要手写字符串。 */
    val VERSION: String = BuildConfig.VERSION_NAME
    val VERSION_CODE: Int = BuildConfig.VERSION_CODE

    const val GITHUB_URL = "https://github.com/Huaxidesu/ModelScout-Mobile"

    const val DESCRIPTION =
        "探测任意 OpenAI 兼容网关的模型能力（工具调用 / 视觉 / 推理 / 上下文与输出上限），" +
            "并生成 OpenCode 与 DeepSeekHardness 配置。"

    val HIGHLIGHTS = listOf(
        "网关声明优先：/models 里带声明字段时直接采信，不浪费探测请求",
        "三态能力图标：支持 / 不支持 / 未知，未知不冒充结论",
        "上限角标区分来源：声明 / 字典 / 实测 / 手动",
        "并发可调（1~10）、协作式中止、进度由引擎统一计算",
        "全链路脱敏：上游文本入日志前一律打码，API Key 只存内存",
    )
}
