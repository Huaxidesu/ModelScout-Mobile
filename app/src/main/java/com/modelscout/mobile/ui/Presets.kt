package com.modelscout.mobile.ui

/** R2 预设下拉 10 项。 */
data class GatewayPreset(
    val label: String,
    val baseUrl: String,
    val envVar: String,
)

val GATEWAY_PRESETS: List<GatewayPreset> = listOf(
    GatewayPreset("OpenAI", "https://api.openai.com/v1", "OPENAI_API_KEY"),
    GatewayPreset("DeepSeek", "https://api.deepseek.com/v1", "DEEPSEEK_API_KEY"),
    GatewayPreset(
        "通义千问",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "DASHSCOPE_API_KEY",
    ),
    GatewayPreset("Moonshot", "https://api.moonshot.cn/v1", "MOONSHOT_API_KEY"),
    GatewayPreset("智谱", "https://open.bigmodel.cn/api/paas/v4", "ZHIPU_API_KEY"),
    GatewayPreset("SiliconFlow", "https://api.siliconflow.cn/v1", "SILICONFLOW_API_KEY"),
    GatewayPreset("火山方舟", "https://ark.cn-beijing.volces.com/api/v3", "ARK_API_KEY"),
    GatewayPreset("Ollama（本地）", "http://localhost:11434/v1", "OLLAMA_API_KEY"),
    GatewayPreset("LM Studio（本地）", "http://localhost:1234/v1", "LMSTUDIO_API_KEY"),
    GatewayPreset("vLLM（本地）", "http://localhost:8000/v1", "VLLM_API_KEY"),
)
