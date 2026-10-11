# NOTES.md — R1 交接物（公开 API 签名清单）

> 这是 R1 → R2 **唯一**的交接物。R2 只允许调用下面这些签名，不要改核心层内部实现。
> 技术栈：Kotlin + Compose M3 + OkHttp + kotlinx.serialization + Coroutines/Flow + DataStore。
> 无 Hilt，手动 DI（`di/AppContainer.kt`）。minSdk 26，compileSdk/targetSdk 35。

---

## 1. 常量 `core/Constants.kt`

```kotlin
object Constants {
    const val PROBE_MAX_TOKENS = 16
    const val LIMIT_PROBE_MAX_TOKENS = 999_999
    const val DEFAULT_CONCURRENCY = 3          // 范围 1..10
    const val MIN_CONCURRENCY = 1
    const val MAX_CONCURRENCY = 10
    const val DEFAULT_TIMEOUT_SECONDS = 30L    // 下限 5
    const val MIN_TIMEOUT_SECONDS = 5L
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val MODELS_MIN_TIMEOUT_SECONDS = 60L // /models 取 max(配置, 60)
    const val LOG_BUFFER_LINES = 600
    const val ACCEPT_JSON = "application/json"
    const val TOOL_PROBE_NAME = "modelscout_ping"
    const val PROBE_IMAGE_PNG_BASE64: String   // 64x64 纯色 PNG（不是计划书原文的 1x1，原因见第 8 节）
    const val PROBE_IMAGE_DATA_URL_PREFIX = "data:image/png;base64,"
    const val LIMIT_ECHO_THRESHOLD = 900_000
    const val FALLBACK_CONTEXT_LIMIT = 128_000
    const val FALLBACK_OUTPUT_LIMIT = 4_096

    fun effectiveTimeoutSeconds(raw: Long): Long
    fun modelsTimeoutSeconds(raw: Long): Long
    fun effectiveConcurrency(raw: Int): Int
}
```

## 2. 数据模型 `core/model/`

```kotlin
enum class Capability { SUPPORTED, UNSUPPORTED, UNKNOWN }

enum class LimitSource(val label: String) { DECLARED("声明"), DICTIONARY("字典"), MEASURED("实测"), MANUAL("手动") }

enum class Stage(val weight: Int) { TOOL(20), VISION(40), REASONING(60), LIMIT(80), DONE(100) }

data class ModelEntry(
    val id: String, val displayName: String = id,
    val declaredTool: Boolean? = null, val declaredVision: Boolean? = null,
    val declaredReasoning: Boolean? = null,
    val declaredContext: Int? = null, val declaredOutput: Int? = null,
)

data class ModelResult(
    val id: String, val displayName: String = id,
    val tool: Capability = Capability.UNKNOWN,
    val vision: Capability = Capability.UNKNOWN,
    val reasoning: Capability = Capability.UNKNOWN,
    val contextLimit: Int? = null, val outputLimit: Int? = null,
    val limitSource: LimitSource? = null,
    val selected: Boolean = true, val note: String? = null,
) { fun withLimits(context: Int?, output: Int?, source: LimitSource?, note: String? = note): ModelResult }

enum class ErrorKind(val message: String, val endpointLevel: Boolean = false)
// KEY_INVALID(401/403/TLS, 端点级) / BASE_URL_INVALID(404/DNS, 端点级) / NETWORK_TIMEOUT
// RATE_LIMITED(429) / SERVER_ERROR(5xx) / MODEL_NOT_FOUND(404 model not found, 端点级)
// PARSE_ERROR / CANCELLED / UNKNOWN

data class AppError(val kind: ErrorKind, val detail: String? = null, val httpCode: Int? = null) {
    val message: String
}
object ErrorClassifier { fun classify(code: Int, body: String?, throwable: Throwable? = null): AppError }
```

> `ErrorKind.endpointLevel == true` ⇒ 整轮直接终止，不重试其他模型。

## 3. 引擎 `core/engine/`

```kotlin
data class ScanConfig(
    val baseUrl: String, val apiKey: String = "",
    val concurrency: Int = Constants.DEFAULT_CONCURRENCY,
    val timeoutSeconds: Long = Constants.DEFAULT_TIMEOUT_SECONDS,
    val probeLimit: Boolean = true, val filter: String = "",
)

class AbortFlag { val isAborted: Boolean; fun abort() }

data class ScanState(
    val total: Int, val completed: Int, val percent: Int,
    val currentModel: String?, val stage: Stage,
    val running: Boolean, val fatalError: AppError?,
    val results: List<ModelResult>,
)

data class ScanOutcome(
    val results: List<ModelResult>, val total: Int,
    val fatalError: AppError?, val aborted: Boolean,
)

class ScanEngine(
    dictionary: ModelParamDictionary = ModelParamDictionary.EMPTY,
    clientFactory: (Long) -> OkHttpClient = { HttpClients.create(it) },
) {
    suspend fun run(
        config: ScanConfig,
        abort: AbortFlag = AbortFlag(),
        onLog: (String) -> Unit = {},
        onState: (ScanState) -> Unit = {},
    ): ScanOutcome
}
```

**R2 只需**：`ScanEngine(container.dictionary).run(config, abort, onLog, onState)`。
`percent` / `completed` / `total` / `results` 全部由引擎算好，UI **只显示**，不要再自己算。

**声明优先**：`/models` 里带声明字段时，引擎会跳过对应探测请求。
- 工具：`declaredTool == true` 且推理已能判定（声明或名称正则）→ 不发探测；否则发一次工具探测（兼取推理信号）。
- 视觉：`declaredVision == true/false` 都不发探测；只有 `null` 才探测。
- 上限：`declaredContext/declaredOutput` 命中即标 `DECLARED`，不发上限探测。
- 极端情况：网关把能力全声明齐 → **一次 chat 探测都不发**（有单测覆盖）。

## 4. 解析 / 提取 / 生成

```kotlin
object ModelsParser    { fun parse(raw: String): List<ModelEntry> }          // 三形态 + 两种声明 schema
object LimitParser     { data class Parsed(val context: Int?, val output: Int?)
                         fun parse(raw: String): Parsed }                    // 2.4 报错文本
object NumberTokens    { fun parse(token: String): Int? }                    // 128k / 1.5m / 128,000
object ReasoningHeuristics { fun looksLikeReasoning(modelId: String): Boolean }
object CapabilityJudge { fun judge(code: Int, body: String, vision: Boolean): Capability
                         fun hasReasoningContent(body: String): Boolean }

object ConnectionExtractor {
    data class UrlCandidate(val url: String, val score: Int, val reasons: List<String>)
    data class Result(val baseUrl: String?, val apiKey: String?, val keyRule: String?,
                      val urlCandidates: List<UrlCandidate>) { val hasUrl: Boolean; val hasKey: Boolean }
    fun extract(text: String): Result
    fun extractKey(text: String): Pair<String, String>?
}

object Redactor {
    const val REDACTED = "***REDACTED***"
    fun mask(secret: String): String                                   // <=8 全星号，否则前3后4
    fun redact(text: String, configuredKey: String? = null): String
}

class ModelParamDictionary private constructor(entries: List<DictEntry>) {
    fun lookup(modelId: String): DictEntry?
    val size: Int
    companion object { val EMPTY; fun fromJson(raw: String): ModelParamDictionary }
}
data class DictEntry(val match: String, val context: Int? = null, val output: Int? = null)

object UrlNormalizer {
    fun normalizeBaseUrl(raw: String): String
    fun endpoint(baseUrl: String, path: String): String
}
object HttpClients { fun create(timeoutSeconds: Long): OkHttpClient }

data class RawResponse(val code: Int, val body: String, val error: AppError?) { val isSuccess: Boolean }
class ProbeClient(client: OkHttpClient, baseUrl: String, apiKey: String) {
    suspend fun postChatCompletions(body: String): RawResponse
    fun cancelAll()
}
class ModelsFetcher(client: OkHttpClient, baseUrl: String, apiKey: String) {
    suspend fun fetch(): ModelsFetchResult
}
data class ModelsFetchResult(val models: List<ModelEntry>, val error: AppError?, val raw: String)

object ProbeRequests {
    fun toolProbe(modelId: String): String
    fun visionProbe(modelId: String): String
    fun limitProbe(modelId: String): String
}
```

## 5. 配置生成 `core/gen/`

```kotlin
data class GenerationInput(
    val providerKey: String, val providerName: String, val npm: String,
    val baseUrl: String,
    val apiKeyLiteral: String? = null,   // 明文模式
    val envVar: String? = null,          // {env:VAR} 模式
    val models: List<ModelResult> = emptyList(),
) { val apiKeyValue: String }

object ProviderCatalog {
    const val OPENAI_COMPATIBLE = "@ai-sdk/openai-compatible"
    fun infer(baseUrl: String, fallbackName: String? = null): ProviderMeta
}
data class ProviderMeta(val key: String, val displayName: String, val npm: String)

object OpenCodeGenerator { fun generate(input: GenerationInput): String }
// ★ toolcall / attachment / reasoning 为顶层字段；★ limit.context / limit.output 必填

enum class DshFormat(val label: String, val extension: String) { JSONC, JSON, TOML }
object DshGenerator {
    const val DISCLAIMER: String
    fun generate(input: GenerationInput, format: DshFormat): String
}
```

## 6. 日志

```kotlin
class LogBuffer(capacity: Int = Constants.LOG_BUFFER_LINES) {
    val flow: StateFlow<List<String>>
    fun append(line: String); fun appendAll(newLines: List<String>)
    fun snapshot(): List<String>; fun clear()
}
```

## 7. DI 与设置

```kotlin
class AppContainer(context: Context) {
    val settingsStore: AppSettingsStore
    val logBuffer: LogBuffer
    val dictionary: ModelParamDictionary      // 读 assets/model_params.json（20 条）
    fun newEngine(): ScanEngine
}

class AppSettingsStore(context: Context) {
    data class Snapshot(baseUrl, concurrency, timeoutSeconds, probeLimit, filter, keyMode, dshFormat)
    val snapshot: Flow<Snapshot>
    suspend fun saveConnection(...); suspend fun saveDshFormat(format: String)
    companion object { const val KEY_MODE_ENV = "env"; const val KEY_MODE_PLAIN = "plain" }
}
```

> **API Key 只存内存**（`UiState.apiKey`），`AppSettingsStore` 不落盘任何密钥。

---

## 8. 已知取舍（R2 需在文案上体现）

1. **探测图片是 64x64 纯色 PNG，不是计划书原文的「1x1 透明 PNG」。**
   实测 Intern Discovery 网关：1x1 PNG 被上游判为 `image file is truncated (0 bytes not processed)`，
   导致 `deepseek-v4-flash-vision` / `kimi-k2.6` / `qwen3.8-27b` 这些**真视觉模型被误判为不支持**。
   换成 64x64 后它们正常返回 200 并能描述图片内容。
2. **但视觉探测的 HTTP 状态码本身不可靠**：同一个网关里，纯文本模型收到图片也会返回 200（图片被静默丢弃）。
   所以**必须依赖声明字段**；`CapabilityJudge` 额外把「图片加载失败」类错误（truncated / failed to load image 等）
   判为 `UNKNOWN` 而不是「不支持」，避免把基础设施故障当成能力结论。
3. **上限候选 >= 900000 一律剔除**（`LIMIT_ECHO_THRESHOLD`）。这是为了丢弃 999999 的请求回显；
   副作用是 1M / 1.5M 这类真实超长上下文也会被丢弃。**实测网关会声明 1048576，走声明路径不受影响**，
   但纯探测路径下 1M 模型的上限会拿不到。
4. **`limit.context` / `output` 未知时填兜底值** 128000 / 4096，因为 OpenCode schema 要求两字段必填。
5. **上限探测拿到 2xx** 说明端点不校验 `max_tokens`，结果标「手动」，不写数值。
6. **推理能力只做正向判定**（声明 / 名称正则 / `reasoning_content`），判定不出来时是 `UNKNOWN` 而不是 `UNSUPPORTED`。
7. **401/403/404 视为端点级错误**，命中即整轮终止。
   注意：部分网关会对单个模型回 404（如 `xxx is not supported by TokenPlan`），此时整轮会中断——
   走声明路径可规避，纯探测路径下需留意。
8. **探测失败会重试一次**（`ProbeClient.MAX_ATTEMPTS = 2`），只针对瞬时故障（网络异常 / 5xx），
   因为实测该网关会偶发 SSL EOF。

## 9. 构建与验收命令

```bash
./gradlew testDebugUnitTest     # R1 验收：全绿
./gradlew assembleDebug         # R2 验收：出 APK
```

### 应用版本号（单一来源）

**唯一来源是 `gradle/libs.versions.toml` 的 `[versions]`**：

```toml
appVersionName = "1.1"
appVersionCode = "2"
```

`app/build.gradle.kts` 读它填 `versionName` / `versionCode`；
`ui/AppInfo.kt` 通过 `BuildConfig.VERSION_NAME` / `VERSION_CODE` 读同一份值（`buildConfig = true`）。
**任何地方都不要再手写版本字符串。**

真实网关冒烟测试（凭证只从命令行注入，不写进仓库）：

```bash
./gradlew testDebugUnitTest --tests "*RealGatewayIT*" \
  -PrealGateway=1 \
  -PrealBaseUrl="https://discovery-api.intern-ai.org.cn/v1" \
  -PrealApiKey="sk-..."
```

---
---

# KeyHub（V1.1）交接物

> 新增独立模块 `keyhub/`，不改既有探测引擎。集成点只有 `MainActivity` 与 `AppContainer`。

## K1. 包隔离

新代码全在 `keyhub/` 下。允许 import 的既有件只有两个：

| 计划书写的 | 本工程实际路径 |
|---|---|
| `core/error` | `com.modelscout.mobile.core.model.ErrorKind`（错误表枚举） |
| `Redactor` | `com.modelscout.mobile.core.security.Redactor`（上游文本脱敏） |

**不 import** `core/net`（probe 包）、`core/engine`、`core/parse`。KeyHub 自带
`KeyHubHttp`（独立 OkHttp 实例）与 `keyhub/AbortFlag`。

## K2. 平台知识单点 `keyhub/PlatformRegistry.kt`

```kotlin
enum class AuthStyle { BEARER, X_API_KEY, X_GOOG_API_KEY, NONE }
data class PrefixRule(val value: String, val confidence: Confidence = Confidence.CERTAIN)

data class PlatformSpec(
    val platform: Platform,
    val prefixes: List<PrefixRule>, val pattern: Regex?, val confidence: Confidence,
    val authStyle: AuthStyle, val extraHeaders: Map<String, String>,
    val validityEndpoint: String?, val balanceEndpoint: String?,
    val balanceAmountPaths: List<String>, val balanceSubtractPath: String?,
    val balanceCurrencyPaths: List<String>, val unlimitedWhenPathNull: String?,
    val defaultCurrency: String?, val runtimeBaseUrl: Boolean, val note: String?,
) {
    val hasBalanceEndpoint: Boolean
    val supportsDetection: Boolean
}

data class Detection(val platform: Platform, val confidence: Confidence) { val label: String }

object PlatformRegistry {
    val specs: List<PlatformSpec>
    fun spec(platform: Platform): PlatformSpec
    fun detect(rawKey: String): Detection          // 最长前缀优先 → 正则 → 自定义（推断）
    val tokenRegex: Regex                          // 由注册表前缀 + 平台正则 + 32 位兜底自动生成
    val selectablePlatforms: List<Platform>
}
```

**加平台 = 在 `specs` 里加一条记录**，其它文件不许出现平台分支。

## K3. 分类单点 `keyhub/KeyClassifier.kt`

```kotlin
object KeyClassifier { fun classify(outcome: CheckOutcome): KeyStatus }
```

`KeyStatus`（五态）自带 `label` / `symbol` / `tone`，UI 只按 `tone` 取色，不推断状态。

## K4. 模型 `keyhub/model/`

```kotlin
enum class Platform(val displayName: String)          // 11 个平台
enum class Confidence { CERTAIN, INFERRED }
enum class StatusTone { GREEN, ORANGE, RED, GRAY, OUTLINE }
enum class KeyStatus(val label: String, val symbol: String) { AVAILABLE, INSUFFICIENT, INVALID, CHECK_FAILED, PENDING }

data class Balance(val amount: Double?, val currency: String?, val unlimited: Boolean) {
    fun display(): String          // ¥ / $ / — / ∞
}

data class KeyRecord(
    id, rawKey, label, platform, platformOverridden, addedAt,
    lastStatus, lastBalance, lastCurrency, lastUnlimited, lastCheckedAt,
    customBaseUrl, customBalanceEndpoint,
) { fun displayBalance(): Balance }

data class HistoryPoint(val fp: String, val ts: Long, val status: KeyStatus, val balance: Double?, val currency: String?)

enum class TransportError { TIMEOUT, DNS, TLS, NETWORK }
data class CheckOutcome(
    val httpCode: Int?, val transport: TransportError?, val balance: Balance?,
    val hasBalanceEndpoint: Boolean, val storageOnly: Boolean, val errorKind: ErrorKind?,
)
```

> 比计划书 schema 多三个字段：`lastUnlimited`（渲染 ∞ 必需）、`customBaseUrl` / `customBalanceEndpoint`
> （计划书 §4.1 自定义平台「用户填 baseUrl + /models，可选填余额端点」必需）。

## K5. 识别与检测

```kotlin
object KeyDetector {
    fun detect(rawKey: String): Detection
    fun extractKeys(text: String): ExtractionResult
}
data class ExtractedKey(val rawKey: String, val label: String?, val platform: Platform, val confidence: Confidence)
data class ExtractionResult(val keys: List<ExtractedKey>, val duplicatesSkipped: Int) { val total: Int }

fun interface EndpointResolver { fun resolve(spec: PlatformSpec, record: KeyRecord): Endpoint? }
data class Endpoint(val url: String, val isBalance: Boolean)
object RegistryEndpointResolver : EndpointResolver        // 默认实现，只读 PlatformRegistry

class KeyChecker(client: OkHttpClient, resolver: EndpointResolver = RegistryEndpointResolver) {
    suspend fun check(record: KeyRecord): CheckOutcome
}
object BalanceParser { fun parse(body: String, spec: PlatformSpec): Balance? }

sealed interface KeyHubEvent {
    data class KeyChecked(keyId, status, balance, errorKind) : KeyHubEvent
    data class BatchProgress(val checked: Int, val total: Int) : KeyHubEvent
    data class BatchFinished(val summary: BatchSummary) : KeyHubEvent
}
data class BatchSummary(total, checked, available, insufficient, invalid, failed, pending, cancelled, failedIds)

class KeyCheckEngine(checker: KeyChecker, clock: () -> Long = System::currentTimeMillis) {
    val events: SharedFlow<KeyHubEvent>
    suspend fun checkOne(record: KeyRecord, abort: AbortFlag = AbortFlag()): KeyRecord
    suspend fun checkAll(records, concurrency = 2, abort, onResult: (KeyRecord) -> Unit): BatchSummary
}
```

`EndpointResolver` 是给单测用的接缝（指向 MockWebServer），平台知识仍单点在注册表。

## K6. 存储与单一数据源

```kotlin
interface KeyCipher { fun encrypt(ByteArray): ByteArray; fun decrypt(ByteArray): ByteArray }
class KeystoreCipher(alias: String = KeyHubConfig.KEYSTORE_ALIAS) : KeyCipher   // AndroidKeyStore AES256-GCM
class NoopCipher / class XorCipher : KeyCipher                                  // 单测用

class KeyVault(dir: File, cipher: KeyCipher) {
    fun loadKeys(): List<KeyRecord>; fun saveKeys(records)
    fun loadHistory(): List<HistoryPoint>; fun saveHistory(points)
    fun appendHistory(newPoints): List<HistoryPoint>
    fun capHistory(points): List<HistoryPoint>
    fun exists(): Boolean; fun clearAll()
}

interface KeyHubPrefs { val persistEnabled: Flow<Boolean>; val concurrency: Flow<Int>; ... }
class DataStoreKeyHubPrefs(context) / class InMemoryKeyHubPrefs : KeyHubPrefs

class KeyHubStore(vault: KeyVault, engine: KeyCheckEngine, clock: () -> Long = ...) {
    var persistEnabled: Boolean                 // 由 ViewModel 跟随偏好流同步
    val records: StateFlow<List<KeyRecord>>     // ★ 唯一数据源
    val history: StateFlow<List<HistoryPoint>>  // ★ 唯一数据源
    val events: SharedFlow<KeyHubEvent>
    fun restore(); fun onPersistDisabled(); fun clearAll()
    fun add(extracted: List<ExtractedKey>): AddResult
    fun remove(id); fun updateLabel(id, label); fun updatePlatform(id, platform, customBaseUrl, customBalanceEndpoint)
    suspend fun checkOne(id, abort): KeyRecord?
    suspend fun checkAll(concurrency, abort, only: List<String>?): BatchSummary
    fun historyFor(record: KeyRecord): List<HistoryPoint>
}

object KeyMasker {
    fun mask(key: String): String          // >=12 → 前5+***+后4；否则等长星号（注意与 Redactor.mask 不同）
    fun fingerprint(rawKey: String): String // sha256 前 16 位十六进制
}

object KeyHubHttp { fun create(): OkHttpClient }   // connect 10s / read 15s
object KeyHubConfig { /* 超时、并发 2(1~5)、历史上限、文件名、Keystore 别名 */ }
class AbortFlag { val isAborted: Boolean; fun abort() }
```

## K7. UI `keyhub/ui/`

```kotlin
@Composable fun KeyHubRoot(store: KeyHubStore, prefs: KeyHubPrefs)   // 集成点唯一入口
class KeyHubViewModel(store, prefs) {
    val state: StateFlow<KeyHubUiState>
    // 排序/过滤/添加/详情/揭示/改平台/删除/检测/取消/设置
    companion object { fun factory(store, prefs): ViewModelProvider.Factory }
}
enum class KeyHubScreen { LIST, DETAIL }
enum class SortMode(val label: String) { PLATFORM, STATUS, ADDED_AT }
object KeyHubListOps {   // 纯函数：过滤 / 排序 / 分组（组内余额降序，∞ 最高，— 沉底）
    fun filter(...); fun sort(...); fun groupByPlatform(...); fun compareInGroup(a, b): Int
}
@Composable fun BalanceChart(points: List<HistoryPoint>, modifier)   // 自绘 Canvas，最近 30 点
```

## K8. 集成点（改动清单）

| 文件 | 改动 |
|---|---|
| `di/AppContainer.kt` | +8 行：`keyHubPrefs` / `keyHubStore` |
| `ui/MainActivity.kt` | +4 行：NAV_ITEMS 加一项、`when` 加一个分支、`titleOf` / `previousOf` 各一行 |
| `ui/ScanViewModel.kt` | **+1 行**：`Screen` 枚举加 `KEYHUB` |

> 计划书说「唯一允许改的既有文件是 MainActivity 与 AppContainer」。
> 本工程没有 NavHost，路由表就是 `ui/ScanViewModel.kt` 里的 `Screen` 枚举，
> 所以「加一条路由」等价于在枚举里加一个常量（1 行）。除此之外没动任何既有文件。

## K9. KeyHub 已知取舍

1. **`customBaseUrl` / `customBalanceEndpoint` / `lastUnlimited` 是计划书 schema 之外的补充字段**，
   分别用于：自定义平台端点、渲染 `∞`。不加这三个字段功能不完整。
2. **KeyHub 的 `mask` 与 core 的 `Redactor.mask` 规则不同**（前者前5后4，后者前3后4），
   刻意各留一份，避免改一处影响另一处。
3. **仅存储平台（智谱 / 火山方舟）永远停在「⏳ 待检测」**，且不发任何请求（有单测覆盖）。
4. **OpenAI / Groq / Anthropic / Gemini 无公开余额接口**，有效性通过即「✅ 可用」，余额显示 `—`。
5. **429 一律归「查询失败」**，绝不判「已失效」（决策 6）。
6. **手动改平台会清空该 Key 的旧结论**（状态回到待检测），避免旧平台的余额被误读。

## K10. R3 真实冒烟命令

**凭证走 gitignored 的 `local.secrets.properties`**（不要用 `-P` 在命令行传，
否则明文会落进 `.gradle/*/executionHistory.bin`）：

```properties
# local.secrets.properties（已 gitignore，绝不入库）
realGateway=1
realBaseUrl=https://your-gateway/v1
realApiKey=sk-...
keyhubBaseUrl=https://your-gateway/v1
keyhubApiKey=sk-...
```

```bash
./gradlew testDebugUnitTest --tests "*RealKeyHubIT*"
```

`app/build.gradle.kts` 里的 `secret(name)` 会先读命令行 `-P`，再读这个文件。

## K11. 凭据红线

1. **绝不要把真实 Key 的任何片段写进测试数据**。测试用 `sk-fake0000000000000000000000000000`
   这类明显假的字符串。
2. 真实 Key 只允许出现在 `local.secrets.properties`（gitignored）。
3. KeyHub 运行时红线：明文 Key 只出现在**输入框、加密文件、剪贴板**三处；
   日志 / 事件 / UI 一律 `KeyMasker.mask`。
