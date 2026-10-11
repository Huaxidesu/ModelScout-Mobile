package com.modelscout.mobile.core.net

/**
 * 计划书 2.5：提取后去掉具体接口路径再规整，防止拼出双份路径。
 * 只做「去掉已知端点后缀 + 去尾斜杠」，不自动补 /v1（404 时由 UI 提示）。
 */
object UrlNormalizer {

    private val ENDPOINT_SUFFIXES = listOf(
        "/chat/completions",
        "/completions",
        "/embeddings",
        "/images/generations",
        "/audio/transcriptions",
        "/audio/speech",
        "/moderations",
        "/responses",
        "/messages",
        "/rerank",
        "/models",
    )

    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim().trimEnd('.', ',', ';', '、', '。', '，', '；')
        if (url.isEmpty()) return url
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            url = "https://$url"
        }
        var changed = true
        while (changed) {
            changed = false
            for (suffix in ENDPOINT_SUFFIXES) {
                if (url.length > suffix.length && url.endsWith(suffix, ignoreCase = true)) {
                    url = url.dropLast(suffix.length)
                    changed = true
                }
            }
        }
        return url.trimEnd('/')
    }

    /** 拼接接口路径，避免双份斜杠。 */
    fun endpoint(baseUrl: String, path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}
