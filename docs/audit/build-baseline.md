# 构建与测试基线（实测报告）

> **性质**：本报告所有数字来自本机实际执行的 Gradle 命令输出与 `build/test-results` 下的 JUnit XML，
> 不采信 README / 注释 / 既有文档。**本阶段未修改任何源文件。**
> 报告时间：2026-10-06 23:40–23:57 CST（= 15:40–15:57 UTC）。宿主：Mac OS X 26.6.2 aarch64。
>
> ⚠️ **本文第 1–5 节是第一轮（未做任何改动）的观测**。第二轮做了稳定化处理，见文末
> [§6 第二轮：live 门禁与基线稳定](#6-第二轮live-门禁与基线稳定) —— **现在的套件是稳定绿的**，
> 请以 §6 为准。

---

## 1. 环境

### 实读来源

| 项 | 取值 | 来源 |
|---|---|---|
| Gradle | **9.4.1** | `./gradlew -v` 实跑输出 |
| Launcher JVM | 21.0.12.1 (Amazon.com Inc. 21.0.12.1+9-LTS) | `./gradlew -v` |
| Daemon toolchain | **JDK 21**（`toolchainVersion=21`） | `gradle/gradle-daemon-jvm.properties` |
| Wrapper 分发 | `mirrors.cloud.tencent.com/gradle/gradle-9.4.1-bin.zip` | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | **2.4.10** | `gradle/libs.versions.toml` → `kotlin` |
| AGP | **9.2.1** | `androidGradle` |
| KSP | **2.3.12** | `ksp` |
| Compose Multiplatform | **1.12.0** | `composeMultiplatform` |
| CMP material3 | 1.12.0-alpha03 | `composeMultiplatformMaterial3` |
| Compose BOM（Android 侧） | 2026.06.01 / material3 1.5.0-alpha25 | `composeBom` / `compose-material3` |
| Room | 2.8.4 | `roomRuntime` |
| Ktor | 3.5.2 | `ktor` |
| Coil3（KMP 侧） | 3.6.1 | `coil3Kmp` |
| mediamp | 0.5.0 | `mediamp` |
| materialkolor | 5.0.1 | `materialkolor` |
| compileSdk / minSdk | **37 / 29** | `gradle.properties` → `han1me.android.*` |

### 构建开关（`gradle.properties`）

```
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
```

- **配置缓存实测生效**：多次运行均出现 `Configuration cache entry stored. / reused.`，无插件不兼容报错。
- 构建期间唯一的通用告警（非 KSP/Kotlin 告警）：
  `WARNING: The option setting 'android.disallowKotlinSourceSets=false' is experimental.`
  以及 `The com.github.ben-manes.versions plugin id is deprecated; apply io.github.ben-manes.versions instead.`

### 编译器标志（`build-logic` 约定插件）

`build-logic/src/main/kotlin/han1me-kmp-library.gradle.kts:97` → `freeCompilerArgs.add("-Xexpect-actual-classes")`

- ✅ **不存在** `-Xstring-concat` 配置问题（全仓库 grep `.kts/.gradle/.properties/.toml` 零命中）。
- ✅ **未开启** `allWarningsAsErrors` / `suppressWarnings`——告警纯属信息性，不阻断构建。
- 其余 `freeCompilerArgs` 均为 `-opt-in=...` 形式的实验性 API opt-in：
  `:shared`（material3 Expressive）、`:video:ui`（同为 Expressive）、`:app`（`RequiresOptIn` + `-jvm-default=enable`，JVM target 21）。

---

## 2. 编译基线

### 2.1 命令与结果

共 7 个 Gradle 模块 × 12 个目标：

```bash
./gradlew --offline \
  :video:contract:compileAndroidMain :video:contract:compileKotlinDesktop \
  :video:engine:compileAndroidMain   :video:engine:compileKotlinDesktop \
  :video:ui:compileAndroidMain       :video:ui:compileKotlinDesktop \
  :video:surface:compileAndroidMain  :video:surface:compileKotlinDesktop \
  :shared:compileAndroidMain         :shared:compileKotlinDesktop \
  :app:compileDebugKotlin            :desktopApp:compileKotlin
```

> target 名说明：`:desktopApp` 用的是 `kotlin("jvm")` 插件，**任务名是 `compileKotlin`**（不是 `compileKotlinDesktop`）；
> 其余 KMP 模块走 `jvm("desktop")` 自定义目标（见 `shared/build.gradle.kts:172` 注释"jvm(\"desktop\") 是自定义目标名"），
> 故为 `compileKotlinDesktop` / `compileAndroidMain`。名字已实跑验证，非猜测。

| # | 运行 | 结果 | 耗时 | 任务明细 |
|---|---|---|---|---|
| A | 增量（首次） | **BUILD SUCCESSFUL** | **1m 01s**（墙钟 64s） | 109 actionable：**23 executed**, 86 up-to-date |
| B | **全量重编** `--rerun-tasks` | **BUILD SUCCESSFUL** | **1m 35s** | **100 actionable：100 executed**（199 条 task 行全执行） |

**关于「增量」的陷阱**：运行 A 中只有 `:shared:compileAndroidMain` 与 `:app:compileDebugKotlin` 真正在编译，
其余 10 个目标被 `build/` 目录里 15:25 的既有产物判定为 UP-TO-DATE。**因此 A 不足以证明"今天能编"**。
故补了运行 B（`--rerun-tasks`），强制 12 个目标连同 KSP、资源生成、依赖解析全部重跑，`100/100 executed`，
这才是有资格写进 CI 的编译基线。

### 2.2 结论：**12 / 12 编译目标全绿，0 error**

```
e: 行数 = 0
```

告警仅在数量级层面整理，见第 4 节。

---

## 3. 测试基线

### 3.1 命令

```bash
# 先强制删除旧结果，保证 XML 不被历史产物污染
rm -rf shared/build/test-results/desktopTest video/*/build/test-results/desktopTest

./gradlew --offline --continue \
  :shared:desktopTest       --rerun \
  :video:contract:desktopTest --rerun \
  :video:engine:desktopTest   --rerun \
  :video:ui:desktopTest       --rerun \
  :video:surface:desktopTest  --rerun
```

关键：**必须带 `--rerun`**。实测仅删除 `test-results` 目录后直接跑，Gradle 判定 Test task `UP-TO-DATE`（EXIT=0，5s，
一个测试都没跑），这是本次踩到的第一个坑——**删 XML 不等于重跑**。

另一个坑：**`build/test-results` 里混着多个 target 的结果**（`desktopTest` / `iosSimulatorArm64Test` /
`testAndroidHostTest`）。第一轮直接 glob `**/*.xml` 统计时把 `10:10Z` 的历史 iOS/Android 结果也算进了
"今天"的总数（虚高到 1062/1472），必须先按 **task 名目录**过滤再按 **timestamp** 验真。

### 3.2 基线表（本机最新版运行结果）

| 模块 | tests | failures | errors | skipped | XML timestamp 窗口（UTC） |
|---|---:|---:|---:|---:|---|
| `:shared` | **531** | **1** | 0 | 0 | 15:51:40.546Z – 15:53:13.895Z |
| `:video:contract` | 48 | 0 | 0 | 0 | 15:51:40.275Z – 15:51:40.881Z |
| `:video:engine` | 28 | 0 | 0 | 0 | 15:51:40.400Z – 15:51:40.593Z |
| `:video:ui` | 77 | 0 | 0 | 0 | 15:51:40.444Z – 15:51:42.734Z |
| `:video:surface` | **0（NO-SOURCE）** | — | — | — | 无测试源集 |
| **合计** | **684** | **1** | **0** | **0** | 全部落在单次运行窗口内 |

- XML 来源：`<module>/build/test-results/desktopTest/*.xml`（`:shared` 98 个文件，`:video:contract` 6，
  `:video:engine` 5，`:video:ui` 9）。
- `:video:surface:desktopTest` 输出 `NO-SOURCE`——该模块**没有任何测试源集**，`find video/surface/src -path "*Test*" -name "*.kt"` 命中 0。
  这是一个真实的覆盖缺口，不是命令写错。

### 3.3 ⚠️ 关键发现：这条基线**不稳定** —— 5 次全量运行，4 红 1 绿

失败恒定只有 **1 个用例**，且恒定落在 `:shared:desktopTest`，但**每次失败的用例不是同一个**：

| 运行 | 时间 (UTC) | 结果 | 失败用例 |
|---|---|---|---|
| 1 | 15:41–15:43 | 🔴 FAIL | `PlayerControlsVisibilityInteractionTest` :: `D11 进页面控件就亮_单击画面把播放意图写进mediamp[desktop]`<br>`AssertionError: 再点一下没暂停：播放意图被上一次点击卡死了` |
| 2 | 15:47–15:48（仅 `:shared`） | 🟢 **PASS** | — |
| 3 | 15:51–15:53 | 🔴 FAIL | `EchGateLiveTest` :: `经ECH网关javchu拿到200[desktop]`<br>`NoRouteException: 无可用出口（Hanime）：候选出口全部不可用` |
| 4 | ~15:54 | 🔴 FAIL | `EchGateLiveTest` :: `视频CDN经网关可建立连接[desktop]`<br>`AssertionError: 502 说明 CNAME 降级没生效` |
| 5 | ~15:56 | 🔴 FAIL | 同运行 4 |

耗时：全量 `desktopTest` 单次墙钟 **95–98 s**。

**两个红源的性质完全不同，必须分开处理：**

1. **`EchGateLiveTest`（运行 3/4/5，占红的多数）——环境问题，不是代码问题。**
   源码实读：`shared/src/desktopTest/kotlin/lovehan1me/data/network/EchGateLiveTest.kt:259`
   该用例会 `startGate(exe)` 拉起**真实的 `echgate` 二进制**、指向真实站点发真实请求（`javchu.com`、视频 CDN），
   只在"找不到可执行产物"时才 `println("LIVE SKIP")` 提前 return。**产物在本仓存在 → 它每轮都真跑网络**。
   结论：其成败取决于当时的公网可达性与 ECH 网关握手，与本次改动无关。
   佐证：它是整个桌面套件里**最慢的 suite（27.18 s）**，其余 Live 用例都是 0.0–0.01 s 的空转早退
   （靠 `System.getenv("HAN1ME_CF_LIVE_URL")` 之类的环境变量开门）。

2. **`PlayerControlsVisibilityInteractionTest`（运行 1）——Compose 交互用例，疑似竞态。**
   单独隔离重跑 **5 次全绿**：
   `./gradlew --offline :shared:desktopTest --rerun --tests "lovehan1me.feature.video.PlayerControlsVisibilityInteractionTest"`
   → RUN 1..5 RC=0（3–8 s / 次）。
   但它在全量套件里出现过 1 次红灯 → 典型的**执行顺序/资源竞争**型抖动，而非确定性缺陷。

### 3.4 Live 用例清单（`:shared:desktopTest` 中真实执行者）

| 测试类 | tests | 本次结果 | 耗时 |
|---|---:|---|---:|
| `lovehan1me.data.network.EchGateLiveTest` | 4 | 🔴 1 failed | 27.18 s |
| `lovehan1me.feature.video.WatchFetchPerfLiveTest` | 1 | 🟢 | 6.80 s |
| `lovehan1me.app.web.CloudflareCdpLiveTest` | 1 | 🟢（早退） | 0.00 s |
| `lovehan1me.feature.login.FormLoginLiveTest` | 1 | 🟢（早退） | 0.00 s |
| `lovehan1me.feature.player.DesktopMpvPlaybackLiveTest` | 1 | 🟢（早退） | 0.00 s |
| `lovehan1me.feature.player.DesktopQualitySwitchLiveTest` | 1 | 🟢（早退） | 0.00 s |
| `lovehan1me.site.SitePageCaptureLiveTest` | 1 | 🟢（早退） | 0.01 s |

### 3.5 最慢 suite（供后续优化排序）

```
27.18s  shared:EchGateLiveTest                  ← 真实网络
 7.66s  shared:DanmakuLayerRenderTest           ← Compose 渲染
 6.80s  shared:WatchFetchPerfLiveTest
 2.18s  shared:EchGateRuntimeTest
 1.28s  shared:PlayerControlsVisibilityInteractionTest
```

### 3.6 顺带记录：其它 target 的历史结果（**非本轮实跑，仅存量**）

以下结果来自今天早些时候（约 10:10–10:45Z）的存量 XML，**本轮未重跑**，不作为基线结论，但说明
"这套测试在 iOS/Android 单元target 上也曾全绿"：

| 模块 | `iosSimulatorArm64Test` | `testAndroidHostTest` |
|---|---|---|
| `:shared` | 266 / 0 fail（10:44:58Z） | 265 / 0 fail（10:44:17Z） |
| `:video:contract` | 45 / 0 fail（10:27:51Z） | 45 / 0 fail（10:10:51Z） |
| `:video:engine` | 22 / 0 fail（10:27:55Z） | 15 / 0 fail（10:11:04Z） |
| `:video:ui` | 65 / 0 fail（10:33:16Z） | 65 / 0 fail（10:11:04Z） |

---

## 4. 编译告警分类统计

采样：**全量重编（运行 B，100/100 executed）** 的 `w:` 输出——
原始 **80 行**，因 `commonMain` 会被 Android / Desktop / iOS 各自编译一遍，去重后为 **45 条唯一诊断**。
(`file, line, col, message` 四元组相同即视为同一条)

| 分类 | 唯一诊断数 | 说明 |
|---|---:|---|
| **deprecated API 使用** | **22** | 详见下表细分 |
| **`inline` 收益不显著** | **8** | 全部集中在 `video/ui/.../player/ui/support/Platform.kt`（43/48/53/58/63/68/73/79 行 ×2 次编译） |
| **不必要的 safe call `?.`** | **7** | 接收者已非空，多余的保护性调用 |
| **常量/不可达分支** | **3** | `Condition is always 'true'`、Elvis 左侧恒非空、Elvis 恒返左侧 |
| **No cast needed** | **2** | `MediampPlaybackEngineBase.kt:104:52`、`MediampExoPlaybackEngine.kt:64:65` |
| **需要 opt-in** | **2** | `shared/.../data/BackupManager.kt:97/107` 用到 `kotlinx.serialization` 内部 API |
| **冗余转换方法** | **1** | `shared/.../navigation/settings/HomeSettingsRoute.kt:140:58` |
| `Modifier.composed` | **0** | ✅ 无命中 |
| expect/actual 不匹配 | **0** | ✅ 无命中（约定插件已加 `-Xexpect-actual-classes`） |

### deprecated 细分

| 被弃用的 API | 次数 |
|---|---:|
| `val monthNumber: Int`（kotlinx-datetime） | 8 |
| `typealias Instant = Instant` | 4 |
| `val dayOfMonth: Int`（kotlinx-datetime） | 3 |
| `var statusBarColor: Int` | 3 |
| `var navigationBarColor: Int` | 2 |
| `fun unsafeCheckOpNoThrow(...)` | 1 |
| `var isStatusBarContrastEnforced: Boolean` | 1 |

### 告警按模块分布（原始行数）

| 模块 | w: 行数 |
|---|---:|
| `:shared` | 57 |
| `:video:ui` | 16 |
| `:video:contract` | 4 |
| `:video:engine` | 3 |
| `:app` / `:desktopApp` / `:video:surface` | 0 |

告警最多的文件：
`video/ui/src/commonMain/.../player/ui/support/Platform.kt` (16) ·
`shared/src/androidMain/.../feature/player/AndroidVideoPageHost.kt` (6) ·
`shared/src/commonMain/.../feature/home/dailycheckin/YearMonth.kt` (5)

> 性质判断：**全部为"可清理的信息性告警"，无一类指向潜在缺陷**（无空安全漏洞、无 ABI/expect-actual 风险、
> 无 Compose 编译器劣化项）。`allWarningsAsErrors` 未开启，故不影响 CI。

---

## 5. 结论：今天的仓库健康度

> **一句话：代码能可靠地编（12/12 目标全绿、0 error，全量重编 1m35s），但测试套件不可靠地绿——
> 684 个桌面用例全跑约 95 s，5 次全量运行 4 次红，且每次红的那一个都不是同一个用例。**

拆开看：

1. **构建**：健康。`100/100 task executed` 的干净全量编译通过，配置缓存 / 构建缓存 / 并行均正常，
   告警 45 条唯一诊断全部是 deprecation 与死代码级噪音。
2. **测试（结构性）**：`:video:contract` / `:video:engine` / `:video:ui` **三轮全部 100% 绿且稳定**。
3. **测试（风险）**：
   - `EchGateLiveTest` 是**假自动化的手写 microphone 网络用例**——拉真网关、打真站点，网络一抖就红。它是红的主要来源。
   - `PlayerControlsVisibilityInteractionTest` 是**顺序敏感的 Compose 交互用例**，隔离 5/5 绿、全量偶发红。
4. **覆盖缺口**：`:video:surface` **零测试**。

### 给"拔高攻坚"阶段的建议（尚未执行，不在本阶段授权范围内）

- 在做任何性能优化之前，**先把基线固定下来**：给 `EchGateLiveTest` 之类真网络用例加显式环境变量门禁
  （仿照 `CloudflareCdpLiveTest` 的 `System.getenv("HAN1ME_CF_LIVE_URL")` 早退模式），
  否则后续每一次性能对比都会被网络抖动污染。
- `PlayerControlsVisibilityInteractionTest` 建议单独隔离到独立 Gradle task 或加 `@Ignore`-外的方法级 retry，
  先把它和 concurrent Compose 测试的相互作用根因查清。
- `:video:surface`（mediamp 渲染面 + Compose 共存的唯一一层）建议补最基础的冒烟测试。

---

## 附录：本报告使用的全部命令（可复现）

```bash
# 环境
./gradlew -v

# 编译基线（增量）
./gradlew --offline :video:contract:compileAndroidMain :video:contract:compileKotlinDesktop \
  :video:engine:compileAndroidMain :video:engine:compileKotlinDesktop \
  :video:ui:compileAndroidMain :video:ui:compileKotlinDesktop \
  :video:surface:compileAndroidMain :video:surface:compileKotlinDesktop \
  :shared:compileAndroidMain :shared:compileKotlinDesktop \
  :app:compileDebugKotlin :desktopApp:compileKotlin

# 编译基线（全量权威版）
./gradlew --offline --rerun-tasks <同上 12 个 task>

# 测试基线
rm -rf shared/build/test-results/desktopTest video/*/build/test-results/desktopTest
./gradlew --offline --continue \
  :shared:desktopTest --rerun :video:contract:desktopTest --rerun \
  :video:engine:desktopTest --rerun :video:ui:desktopTest --rerun \
  :video:surface:desktopTest --rerun

# 抖动复现（隔离重跑可疑用例 5 次）
./gradlew --offline :shared:desktopTest --rerun \
  --tests "lovehan1me.feature.video.PlayerControlsVisibilityInteractionTest"
```

原始日志：
`/tmp/lh_compile.log`（增量编译）· `/tmp/lh_clean_compile.log`（全量重编）·
`/tmp/lh_test.log`（测试首跑）· `/tmp/lh_final_test2.log`（权威测试基线）·
`/tmp/lh_flaky_*.log`（隔离重跑 ×5）· `/tmp/lh_rep_*.log`（全量重复 ×2）

---

## 6. 第二轮：live 门禁与基线稳定

针对第 5 节"5 次全量 4 次红"的结论，只改了**两个测试文件**（产品源码零改动）：

- `shared/src/desktopTest/kotlin/lovehan1me/data/network/EchGateLiveTest.kt`
- （未改动）`shared/src/desktopTest/kotlin/lovehan1me/feature/video/PlayerControlsVisibilityInteractionTest.kt`

### 6.1 EchGateLiveTest：环境变量门禁

沿用仓库既有惯例（`CloudflareCdpLiveTest` 的 `HAN1ME_CF_LIVE_URL`），新增 `HAN1ME_ECHGATE_LIVE`（非空即开），
四个用例统一在开头调用一个 `assumeLiveOrSkip(case)`。缺产物的原逻辑降级为**第二层** `skipWithoutExe(case)`。

> **为什么没直接照抄 `println + return`**：那样在 XML 里算 **PASSED**，门禁生没生效在报表上完全看不出来。
> 这里改用 JUnit4 的假设机制 `Assume.assumeTrue`（`:shared:desktopTest` 的 classpath 实测确认为
> **JUnit 4.13.2**，经 `kotlin-test-junit:2.4.10` 与 `compose.ui:ui-test-junit4:1.12.0` 引入），
> 于是未开闸时 4 个用例落在 `<skipped/>`。

双向验证（都实测过）：

| 场景 | XML |
|---|---|
| 不设环境变量（日常 `desktopTest`） | `tests=4 failures=0 skipped=4`，耗时 ~0s |
| `HAN1ME_ECHGATE_LIVE=1` | `skipped=0`，真跑去发请求（实测打出 `LIVE no-gate outcome = Failure(SocketTimeoutException)`，6.6s） |

手工跑法已写进该类 KDoc。

### 6.2 PlayerControlsVisibilityInteractionTest：抖动根因（**未修，留作决策**）

隔离跑 5/5 绿、全量偶发红。**门禁上线后再没复现过（6 轮全绿）**，结合代码里的自述，
结论是**同 JVM 内的跨用例污染**，而不是该用例自己的逻辑错。证据：

1. **它只在全量套件里红，隔离必绿** —— 典型的执行顺序/资源竞争特征。
2. **EchGateLiveTest 门禁掉之后 6 轮全绿**（见 6.3）。此前它是全套件最慢的 suite（27s），
   会在同一个 JVM 里拉起真实网关子进程 + N 条排空线程 + 真实 socket I/O。
3. **该仓库自己写下了这些污染**（原文引述，均在 `EchGateLiveTest.kt` 注释里）：
   - "同进程的其它 live 用例会 `setProperty("java.net.useSystemProxies","true")`，
     那是 **JVM 级全局状态、不会自动还原**"；
   - "熔断健康度是**进程全局的**……冷却期是 5 分钟"（`RouteRegistry.reset()`）；
   - `EchGate.publish(...)` 是全局 publish。
4. **另有一处真实的顺序依赖（本次未修）**：`PlayerTestFakes.kt` 里
   `SettingsRepository.install()` **全 JVM 只许成功一次**，而 `installInMemorySettingsStore()`
   用 `runCatching` 把后续失败**静默吞掉** —— 于是"谁先跑到"决定了整个 JVM 用哪份 store。
   （注释原文："[install] 全 JVM 只许成功一次，故用 runCatching 兜住同类里第二个用例。"）

判定：**不需要动 `PlaybackController` 的时间模型**。真正的加固点在测试隔离性（每 JVM 一个测试用例，
或给 JVM 级全局状态加 reset）。但那属于测试基建改造，超出本次"两个文件"的授权，故只报告不动手。

另外报告该用例自身一处**值得改进但本次没有动手**的隐患（它不是这次的红源）：它用合成时间戳
`frameNanos` 驱动 Compose 的**帧时钟**，而被测生产行为走的是**真实时钟** ——
`combineClickable` 的双击判定窗口是实时的 `ViewConfiguration.doubleTapTimeoutMillis`、
自动隐藏倒计时是实时的 `3.seconds`、`indicatorState.showPausedLong()` 也按真实时长挂起。
两套时钟并存意味着"这一次点击被判成单击还是双击"本质上取决于墙钟调度。

### 6.3 新基线：连跑 6 轮，全绿

命令（每轮都带 `--rerun`，保证真执行而非 UP-TO-DATE；第 1–3 轮是完整 5 模块，4–6 轮是 `:shared` 单模块）：

```bash
./gradlew --offline --continue \
  :shared:desktopTest --rerun :video:contract:desktopTest --rerun \
  :video:engine:desktopTest --rerun :video:ui:desktopTest --rerun \
  :video:surface:desktopTest --rerun
```

| 轮次 | 范围 | tests | failures | errors | **skipped** | 耗时 | XML timestamp 窗口（UTC） |
|---|---|---:|---:|---:|---:|---:|---|
| 1 | 全 5 模块 | 684 | 0 | 0 | **4** | 30s | 16:25:16.203Z – 16:25:42.376Z |
| 2 | 全 5 模块 | 684 | 0 | 0 | **4** | 37s | 16:25:49.137Z – 16:26:20.859Z |
| 3 | 全 5 模块 | 684 | 0 | 0 | **4** | 30s | 16:26:30.388Z – 16:26:55.405Z |
| 4 | `:shared` | 531 | 0 | 0 | **4** | 26s | 16:27:45.584Z – 16:28:09.789Z |
| 5 | `:shared` | 531 | 0 | 0 | **4** | 27s | 16:28:13.496Z – 16:28:36.989Z |
| 6 | `:shared` | 531 | 0 | 0 | **4** | 27s | 16:28:40.848Z – 16:29:05.051Z |

每轮窗口互不重叠且都在该轮耗时区间内 → 确为本轮真跑，不是缓存重放。

### 6.4 skipped 增量说明（重点）

| | 门禁前 | 门禁后 | 差值 |
|---|---:|---:|---:|
| `:shared` skipped | **0** | **4** | **+4** |
| `:shared` tests（分母） | 531 | 531 | 0 |
| 其它模块 skipped | 0 | 0 | 0 |

**+4 全部来自 `EchGateLiveTest` 的 4 个用例**，由 executed 变成 skipped：

```
不经网关直连站点必然失败[desktop]      executed → skipped
经ECH网关直连站点拿到200[desktop]      executed → skipped
视频CDN经网关可建立连接[desktop]       executed → skipped
经ECH网关javchu拿到200[desktop]       executed → skipped
```

**总结论：没有任何用例被删除。** 分母 `tests` 恒为 684/531 不变，只是 4 个从"跑过并依赖公网"
变成"显式跳过"。这 4 个仍会**每天被编译**，KDoc 里写了手工跑法（`HAN1ME_ECHGATE_LIVE=1`），
且 6.1 的双向验证表证明设了变量它们就真的会跑。

附带收益最直观的是**时间**：全量耗时从 **95–98s 降到 30–37s**（省掉的就是
EchGateLiveTest 那 27s 真实网络），单轮 `:shared` 从 ~60s 降到 26s。

## 7. 第三轮留档：图片加载器单例化（A1）

`HanimeImageLoader` 的 jvm/ios actual 由"每次 `remember` 各建一个 `ImageLoader`"改为
**进程级单例**（`PlatformLock` 保护，inspection 分支不进缓存，`StartupTrace.mark("coil")` 只打一次），
新增 `HanimeImageLoaderSingletonTest`（5 用例）。反向验证：去掉缓存后 D1/D3/D5 变红。

## 8. 第四轮：搜索分页并发控制（C）

### 8.1 同构重入点清单（仓库全量，"UI 回调持有分页序号 + 接收端无请求标识"）

| # | 位置（修前） | 形态 | 判重 | 请求标识/作废 | 风险 |
|---|---|---|---|:---:|:---:|
| 1 | `SearchScreen.kt:441` → `SearchViewModel` | UI `page++; executeSearch()` | 无 | 无 | **高（本轮已修）** |
| 2 | `MyListRoutes.kt:54–58`（收藏） | UI `getMyFavVideoItems(page); favVideoPage=page+1` | 无 | 无 | 高 |
| 3 | `MyListRoutes.kt:97–101`（稍后再看） | 同上 | 无 | 无 | 高 |
| 4 | `MyListSubViewModel.loadItems`（Fav/WatchLater/LocalWatchLater 共用接收端） | 接收端无 token | 无 | 无 | 高 |
| 5 | `LocalPlayListViewModel.loadMore:129` | `if(isLoadingMore) return` | 有 | 无 | 中 |
| 6 | `MySubscriptionsViewModel.loadMySubscriptions:40` | `if(isLoadingMore) return` | 有 | 无 | 中 |
| 7 | `ArtistViewModel.loadMore:84–117` | `loading/endReached` 守卫 | 有 | 无 | 中 |
| 8 | `OnlineWatchHistoryViewModel.loadNextPage:71–76` | state 守卫 + `loadJob?.cancel()` | 有 | 有（取消=作废） | 低 |

> 本轮按派单范围**只修 #1**；#2–#8 清单仅枚举、未动源码（见 8.5 建议）。

### 8.2 修法：分页序列的唯一所有者收进 VM，抽 `PagingGate`

- `core/domain/state/PagingGate.kt`（新，97 行）：`tryBegin()`（判重）/ `isCurrent(token)`（陈旧拒绝）/
  `finish(token)` / `cancel()` / `reset()` / `restart()`。不依赖网络与协程，故**可确定性断言**。
- `SearchViewModel.kt`（+96/−39）：新增 `pagingGate`；`startFirstPage()`（`restart`，page 归 1）与
  `loadNextPage()`（`tryBegin` 判重 + `page++`）取代"UI 传 page + 9 个筛选参数"的入口；
  `launchSearch(page, token)` 内**先过闸门再合并**，`finally { finish(token) }`。
- `SearchScreen.kt`（+8/−13）：`executeSearch()` → `startFirstPage()`；`doSearch()` 不再写 `page=1`；
  `SearchScreen.kt:441` 的 `{ viewModel.page++; executeSearch() }` → `{ viewModel.loadNextPage() }`。

### 8.3 守卫测试与反向验证（三条，全部证实非空用例）

`PagingGateTest.kt`（新，commonTest，8 用例）：①判重 ②陈旧响应整段丢弃（走真实 `mergeSearchPage`）
③作废在途 + 新凭证。

| 反向验证 | 临时改动 | 期望变红 | 实测 |
|---|---|---|---|
| RV1 判重 | 注释 `tryBegin` 的 `if (inFlight) return null` | 判重用例 | **2 条红** |
| RV2 请求标识 | `isCurrent` 恒 `true` | 陈旧响应相关用例 | **6 条红**（含端到端"旧响应晚到不并入"） |
| RV3 复刻策略 | 注释测试内 `if (!gate.isCurrent(token)) return committed` | 端到端陈旧用例 | **1 条红**（精确命中） |

三条改动均已**逐字还原**，还原后 8/8 绿。

### 8.4 新基线（本轮实跑，`--rerun-tasks --offline`）

```
:shared:desktopTest   tests=544  failures=0  errors=0  skipped=4   BUILD SUCCESSFUL
  544 = 531（二轮基线）+ 5（A1 Singleton）+ 8（C PagingGate）
  skipped=4 全部是 EchGateLiveTest 的 4 个 live 用例，无其它跳过
新套件时间戳：PagingGateTest 17:10:50Z / HanimeImageLoaderSingletonTest 17:11:16Z（本轮窗口内）
:shared:compileKotlinIosSimulatorArm64 / :compileKotlinIosArm64  —— 0 error（commonMain 改动三端可编译）
```

### 8.5 建议

#2/#3（`MyListRoutes` 的收藏/稍后再看）与 #4（`MyListSubViewModel.loadItems`）**与 #1 是同一个缺陷**，
且 #2/#3 更糟：其 `page` 的"读取"与"自增"分跨两次调用（非原子）。建议**下一轮统一**用同一 `PagingGate`
处理（接收端持有闸门 + page，UI 只发一次调用）。#5–#7 已有判重、仅缺请求标识，风险中等；
#8 已用 `loadJob.cancel()` 间接作废，风险最低。本轮按派单范围**未动**这些点。

## 9. 第四轮续：桌面 Coil 单例出口修正（A1.5）

### 9.1 症状与证据

桌面 `setSingletonImageLoaderFactory`（`Main.kt:118`）自建 `ImageLoader.Builder` +
`KtorNetworkFetcherFactory(httpClient = { createHanimeHttpClient() })`，两个后果都不会让编译失败：

1. 它与 `rememberHanimeImageLoader` 用的**不是同一份** ImageLoader → "进程级单例"在桌面名存实亡
   （首页/详情一份缓存、单例注册另一份）；
2. 出口复用 `createHanimeHttpClient()`（API/HTML 出口，带 `HCookieJar`）→ 登录态
   `hanime1_session` 被绑到图床 host `vdownload.hembed.com` 发出去。

### 9.2 修法

- `shared/src/jvmMain/.../ui/component/HanimeImageLoader.jvm.kt`：新增 **public**
  `sharedHanimeImageLoader(context)`，体内 `checkNotNull(hanimeImageLoaderOrNull(context, inspection = false))`
  —— 复用同一把 `loaderLock`、同一个 `singletonLoader`，**绝不产生第二个实例**。
  （放在 `jvmMain` 即可对 `:desktopApp` 可见：约定插件把 `jvmMain` 作为 `jvm("desktop")` 与
  `android` 共享的 JVM 中间层。）
- `desktopApp/src/main/kotlin/lovehan1me/desktop/Main.kt:117-126`：注册口改为
  `sharedHanimeImageLoader(context)`；删掉 `KtorNetworkFetcherFactory` 与 `createHanimeHttpClient`
  依赖；出口回到 `createCdnFetchClient`（**不注入站点 Cookie**）。

### 9.3 守卫测试（两条）

| 类型 | 位置 | 断言 |
|---|---|---|
| 行为 | `CdnFetchClientTest.图片出口不携带站点 Cookie` | `assertSame(CookieJar.NO_COOKIES, createCdnFetchClient().cookieJar)` |
| 配置不变式 | `DesktopCoilSingletonWiringTest`（新，源码扫描） | `Main.kt` **代码**含 `setSingletonImageLoaderFactory` + `sharedHanimeImageLoader(`，且不含 `createHanimeHttpClient` / `KtorNetworkFetcherFactory` |

配置守卫沿用 `:video:contract` 的 `ModuleLayeringTest` 定位法（向上找 `settings.gradle.kts`）；
并**先剥注释**再断言 —— 说明性文字里出现旧标识符属正常，要拦的是代码又接回去。

### 9.4 反向验证（两条，均已逐字还原）

| 反向验证 | 临时改动 | 期望 | 实测 |
|---|---|---|---|
| RV-A | `buildCdnFetchClient` 加 `.cookieJar(HCookieJar())` | 行为守卫红 | **1 红**（`图片出口不携带站点 Cookie`，1/5） |
| RV-B | 代码里再接回 `createHanimeHttpClient()`（保留委派以隔离断言） | 配置守卫红 | **1 红**，消息=「会把 hanime1_session 绑到图床 host…」 |

（RV-B 首次用"整体改回旧块"时命中的是"未委派"断言；遂改为保留委派、仅接回 API 客户端，精确命中**泄漏**断言。）

### 9.5 新基线（本轮实跑）

```
:shared:desktopTest --rerun-tasks --offline   tests=546 failures=0 errors=0 skipped=4  BUILD SUCCESSFUL
  546 = 544（上一轮）+ 2（A1.5 两条守卫）
  skipped=4 仍全部是 EchGateLiveTest 的 4 个 live 用例
:desktopApp:compileKotlin --offline           SUCCESS
```
