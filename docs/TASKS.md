# TASKS.md — KeyHub V1.1 执行状态

> 计划书：《WorkBuddy · 新功能开发计划书（V1.1）》——新增独立模块 `keyhub/`。
> 协议：Orchestrator + 少量子代理，R1 → R2 → R3 串行；失败重试 ≤ 2 次；交接物只有 `docs/NOTES.md`。

## 总览

| 轮次 | 代理 | 范围 | 验收门 | 状态 |
|---|---|---|---|---|
| R1 | KeyHub-Core | model / registry / detect / checker / storage / history + 单测 | `testDebugUnitTest` 全绿 | ✅ 完成 |
| R2 | KeyHub-UI | `keyhub/ui` 全部页面 + 集成点接线 + 打包 | `assembleDebug` 出 APK | ✅ 完成 |
| R3 | 验收 | 真实网关冒烟 + 边界用例 + 交付报告 | §七清单全过 | ✅ 完成 |

## 已拍板的 6 个决策（子代理不得重议）

1. 第 5 态「⏳ 待检测」仅初始 UI 态，不算检测结果；四态才是检测结论。
2. 一个「本地保存」开关同时管 Key 列表与历史。
3. 裸 `sk-` → 「OpenAI 兼容（推断）」；**禁止**自动把 Key 发往多个平台试错。
4. 无余额端点的平台：Key 有效 → 「✅ 可用」，余额显示 `—`。
5. 折线图自绘 Canvas，不新增图表依赖。
6. 余额检测并发默认 2（1~5 可调）；429 一律归「查询失败」，绝不判「已失效」。

## 防乱四原则落地位置

| 原则 | 落地 |
|---|---|
| 平台知识单点 | `keyhub/PlatformRegistry.kt`（唯一平台事实源） |
| 分类单点 | `keyhub/KeyClassifier.kt`（状态只由 `classify()` 产出） |
| 包隔离 | 新代码全在 `keyhub/`；只 import `core/model`（错误表）与 `core/security/Redactor`；不碰 probe 包 |
| 单一数据源 | `keyhub/KeyHubStore.kt` 的 `records` / `history` 两个 StateFlow |

## R1 文件清单

| 文件 | 职责 |
|---|---|
| `keyhub/KeyHubConfig.kt` | 超时 / 并发 / 历史上限 / 文件名 / Keystore 别名 |
| `keyhub/KeyHubHttp.kt` | 独立 OkHttp 实例（connect 10s / read 15s） |
| `keyhub/model/KeyHubModels.kt` | Platform / Confidence / KeyStatus / Balance / KeyRecord / HistoryPoint |
| `keyhub/model/CheckOutcome.kt` | 检测原始产出（只描述事实，不含结论） |
| `keyhub/PlatformRegistry.kt` | **平台知识单点**：前缀 / 鉴权 / 端点 / 余额解析路径 |
| `keyhub/KeyClassifier.kt` | **分类单点**：§4.4 矩阵 |
| `keyhub/detect/KeyDetector.kt` | `detect()` + `extractKeys()` |
| `keyhub/checker/BalanceParser.kt` | 多候选路径容错解析 |
| `keyhub/checker/KeyChecker.kt` | 单 Key 一次请求；`EndpointResolver` 接缝 |
| `keyhub/checker/KeyHubEvent.kt` | KeyChecked / BatchProgress / BatchFinished |
| `keyhub/checker/KeyCheckEngine.kt` | 有界并发 + 协作式取消 |
| `keyhub/storage/KeyCipher.kt` | 加解密抽象（Noop / Xor 供测试） |
| `keyhub/storage/KeystoreCipher.kt` | AndroidKeyStore AES256-GCM |
| `keyhub/storage/KeyVault.kt` | keys.enc / history.enc 读写 + 上限裁剪 |
| `keyhub/storage/DataStoreKeyHubPrefs.kt` | 保存开关 + 并发数 |
| `keyhub/KeyHubPrefs.kt` | 偏好抽象 + 内存实现 |
| `keyhub/security/KeyMasker.kt` | mask（前5+***+后4）+ sha256 指纹 |
| `keyhub/AbortFlag.kt` | 协作式中止（不复用 core/engine） |
| `keyhub/KeyHubStore.kt` | **单一数据源** + 增删改 + 检测编排 |

## 日志

- [R1] 脚手架与全部核心文件落地；`testDebugUnitTest` 全绿。
- [R2] `keyhub/ui` 四个页面（列表 / 添加 Sheet / 详情 / 设置 Sheet）+ 自绘 Canvas 折线图；
  集成点接线完成，`assembleDebug` 出 APK。
- [R3] 真实网关冒烟通过；§七清单逐项核对完毕。

## R3 真实冒烟结果

| 用例 | 结果 |
|---|---|
| 6 个平台 + 明显无效 Key（DeepSeek / SiliconFlow / OpenRouter / OpenAI / Groq / Anthropic） | 全部 `INVALID (HTTP 401)` ✅ |
| 自定义平台 + 真实 Key（`https://discovery-api.intern-ai.org.cn/v1`） | `AVAILABLE`，余额 `—` ✅（决策 4） |
| 有余额端点的平台 + 可用 Key | 需要用户自己的 Key，未做（见交付报告） |

## 集成点改动（对照计划书 §5.1）

| 文件 | 计划书要求 | 实际改动 |
|---|---|---|
| `di/AppContainer.kt` | ≤10 行 | +8 行 |
| `ui/MainActivity.kt` | ≤10 行 | +4 行 |
| `ui/ScanViewModel.kt` | 不在清单内 | **+1 行**（`Screen` 枚举加 `KEYHUB`） |

> 说明：本工程没有 NavHost，路由表就是 `Screen` 枚举，所以「加一条路由」= 枚举加一个常量。
> 除此之外没动任何既有文件。

