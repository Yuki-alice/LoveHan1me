# 测试真值与质量基建审计

> 审计方式：只读源码 + 只读命令。未修改任何源文件。所有结论附 `路径:行号`。

## 一句话真值判定

**半真绿**：CI 确实会执行 `:shared` 与 `:video:{contract,engine,ui}` 的 `desktopTest` / `testAndroidHostTest` 并因失败变红（ci.yml:64、:69），但 ≥18 个用例靠 `println("[skip]") + return` 静默变绿、3 个用例零断言、8 个 iOS 用例所在源集从不执行；风险最集中的 `Parser.kt`(1251 行)、`NetworkRepo.kt`(835 行)、自研回退栈与三大 UI 巨物(1115/1082/967 行) 合计只有"跳过或零覆盖"。绿不等于被测。

---

## 2. 假测试筛查

### 2.1 静默跳过（无 `@Ignore`，用 `println("[skip]") + return` 伪装成通过）

全仓库 **没有任何** `@Ignore` / `@Disabled` / `assumeTrue` / `assumeFalse`（对 `shared/src/{commonTest,desktopTest,iosTest}`、`video/*/src`、`app/src/androidTest` 全量 grep 结果为 0）。但存在更隐蔽的形态：测试体开头判断环境，不满足就 `println("[skip] …") ; return` —— JUnit 记为 **passed**，JUnit XML 里看不到任何 skip 标记。

| 文件:行 | 用例数 | 跳过条件 | CI 上是否真跑 |
|---|---:|---|---|
| `shared/src/desktopTest/kotlin/lovehan1me/site/hanime1/JavchuHomePageParseTest.kt:82` | 4 | 缺 `.workbuddy/_javchu_home.html` | ❌ 跳过（`.gitignore:20` 忽略 `.workbuddy/`） |
| `shared/src/desktopTest/kotlin/lovehan1me/site/hanime1/AuthorPlaylistParserTest.kt:25` | 5 | 缺 `.workbuddy/g2b2_*.html` | ❌ 跳过（同上） |
| `shared/src/desktopTest/kotlin/lovehan1me/app/web/CloudflareCdpLiveTest.kt:37` | 1 | 未设 `HAN1ME_CF_LIVE_URL` | ❌ 跳过 |
| `shared/src/desktopTest/kotlin/lovehan1me/feature/player/DesktopMpvPlaybackLiveTest.kt:36` | 1 | 未设 `HAN1ME_MPV_LIVE_URL` | ❌ 跳过 |
| `shared/src/desktopTest/kotlin/lovehan1me/feature/player/DesktopQualitySwitchLiveTest.kt:42` | 1 | 未设 `HAN1ME_QUALITY_LIVE_URL_A/B` | ❌ 跳过 |
| `shared/src/desktopTest/kotlin/lovehan1me/feature/login/AccountChainTest.kt:118` | 2 | 未设 `HAN1ME_LOGIN_EMAIL/PASSWORD` | ❌ 跳过 |
| `shared/src/desktopTest/kotlin/lovehan1me/data/network/EchGateLiveTest.kt:190`、`225`、`263`、`179` | 4 | 缺 `echgate` 产物 / 本机直连未被阻断 | ❌ 跳过 |
| `shared/src/commonTest/kotlin/lovehan1me/core/util/ComposeAssetsSyncTest.kt:38` | 3 | 7 份 assets 全空（Android host 形态） | ⚠️ desktop 跑、Android host 跳过 |
| `video/engine/src/iosTest/kotlin/lovehan1me/feature/player/MediampAvPlaybackEngineTest.kt:66`、`74`、`100`、`120`、`143`、`165`、`193` | 7 | iOS 出网受限 / 未设 `HANIME_TEST_M3U8` | ❌ 该源集 CI 根本不执行 |

**合计 ≥18 个用例在 CI 上是"空跑绿"**。其中解析器回归（`Javchu` + `AuthorPlaylist` 共 9 个）恰恰是唯一覆盖 `Parser.kt` 的测试，却因夹具被 gitignore 而在干净 checkout 上全部失效 —— 本机有夹具时是真跑，CI 上是纸面绿。

### 2.2 零断言测试（只 println，不判定）

| 文件:行 | 用例数 | 断言数 | 行为 |
|---|---:|---:|---|
| `shared/src/desktopTest/kotlin/lovehan1me/ui/theme/ThemeColorAuditTest.kt:66-67` | 1 | **0** | 把配色对比度审计结果写到 `java.io.tmpdir/theme-audit.txt`（:173），全文件无任何 assert |
| `shared/src/desktopTest/kotlin/lovehan1me/feature/video/WatchFetchPerfLiveTest.kt:50-51` | 1 | **0** | 真跑 `NetworkRepo.getHanimeVideo("408112")`，全程 `println("[perf-probe] …")`，只保证"没抛异常"；且 :55 改写全局 `System.setProperty("user.home", …)`，污染同 JVM 其他用例 |
| `shared/src/desktopTest/kotlin/lovehan1me/site/SitePageCaptureLiveTest.kt:44-45` | 1 | **0** | 抓包工具，KDoc :20 明说"不 assert 页面结构"；结果落 `.workbuddy/` |

这三个没有环境门控，**在 CI 上会真发网络请求**。只要 HTTP 层返回非 2xx 被 `runCatching`/`withTimeoutOrNull` 吞掉，结果仍然是绿。

### 2.3 时序靠 sleep 而非同步原语

| 文件:行 | 形式 |
|---|---|
| `shared/src/desktopTest/kotlin/lovehan1me/data/network/EchGateRuntimeTest.kt:119,215,221,223,264,287,292,294,348,377` | 10 处 `Thread.sleep(10~400ms)` 等子进程/网关状态 |
| `shared/src/desktopTest/kotlin/lovehan1me/core/platform/DesktopDownloadLimitTest.kt:23,43,56` | `Thread.sleep(10)` 后断言限速状态 |
| `shared/src/desktopTest/kotlin/lovehan1me/feature/danmaku/DanmakuLayerRenderTest.kt:175` | `Thread.sleep(FRAME_STEP_MS)` 驱动帧步进（:117 注释自认用的是墙上时钟） |
| `shared/src/commonTest/kotlin/lovehan1me/core/util/StartupTraceTest.kt:31,42` | `runBlocking { delay(5) }` 制造时间差 |
| `shared/src/desktopTest/kotlin/lovehan1me/feature/video/PlayerControlsVisibilityInteractionTest.kt:211` | `Thread.sleep(5L)` |
| `video/engine/src/desktopTest/kotlin/lovehan1me/feature/player/MediampPlaybackEngineBaseTest.kt:248` | `while (!predicate()) delay(20L)` 轮询（无超时上限，坏情况下永久挂起） |
| `shared/src/iosTest/kotlin/lovehan1me/core/platform/IosDownloadSmokeTest.kt:105,127` | `delay(500)` / `while (…) delay(250)` |

`EchGateRuntimeTest` 的 10 处 sleep 是典型的 CI 抖动源：Windows runner 负载高时 200~400ms 的窗口不够，用例会随机变红。

### 2.4 同类"真测试"对照（说明并非全假）

反例证明团队有能力写真测试：`shared/src/commonTest/kotlin/lovehan1me/feature/player/PlaybackControllerTest.kt` 用 fake engine 记录 `loads/speeds/adjusts`，断言具体值（:112 `assertEquals(1, c.state.value.selectedQualityIndex)`、:171 `assertEquals(5_000L, req.startPositionMs)`、:207 `assertEquals(listOf(5f, 0.25f), engine.speeds)`）—— 这是有区分力的测试。问题不在"会不会写"，在**覆盖分布**：好测试集中在播放器控制层，而解析/网络/UI 三大风险区靠 skip 或零断言撑数。

### 2.5 未发现的形态（已 grep，结果为 0）

- `assertTrue(true)` / `assertTrue(true, …)` —— 全量 grep **0 处**
- `@Ignore` / `@Disabled` / `assumeTrue` / `assumeFalse` —— **0 处**
- `assertNotNull(mock…)` 型空断言 —— 未发现独立成例的情况

---

## 3. CI 真值矩阵

唯一工作流：`/Users/kurisu/LoveHan1me/.github/workflows/ci.yml`（101 行）。

| Job | runner | 步骤 | 实际 gradle 命令 | 性质 |
|---|---|---|---|---|
| `jvm-android` (:16) | windows-latest | Compile Android + Desktop (:57) | `:video:{contract,engine,ui,surface}:compileAndroidMain` + `compileKotlinDesktop`、`:shared:compileAndroidMain/compileKotlinDesktop`、`:app:compileDebugKotlin`、`:desktopApp:compileKotlin` | 只编译 |
| 同上 | | Assemble debug APK (:60) | `:app:assembleDebug` | 只打包 |
| 同上 | | desktopTest (:64) | `:video:contract:desktopTest :video:engine:desktopTest :video:ui:desktopTest :shared:desktopTest` | **真跑** |
| 同上 | | Android host unit tests (:69) | `:video:{contract,engine,ui}:testAndroidHostTest :shared:testAndroidHostTest` | **真跑** |
| `ios-compile` (:85) | macos-14 | Compile iOS (:100) | `:video:{engine,ui,surface}:compileKotlinIos{Arm64,SimulatorArm64}`、`:shared:compileKotlinIos{Arm64,SimulatorArm64}` | **只编译，零测试** |

### 判定

- ✅ **没有** `-x test`、没有 `continue-on-error`、没有 `|| true` 吞失败（:33 注释还特意声明了这一点，与配置一致）。
- ✅ 对 `shared`/`video:{contract,engine,ui}` 的 desktopTest 与 Android host test，测试失败**会让流水线变红**（Gradle 非 0 退出，后续 step 不执行）。
- ❌ **`shared/src/iosTest`（1 用例）与 `video/engine/src/iosTest`（7 用例）从未被执行** —— `ios-compile` 只做 `compileKotlinIos*`。这 8 个用例是纯代码装饰。
- ❌ **`video/surface` 模块完全不在 CI 的测试列表里**（:64 只列了 contract/engine/ui），且该模块无测试源集。
- ❌ **`desktopApp`、`app`（JVM 侧）、`echgate` 零测试**；`app/src/androidTest`（2 用例，含 `ExampleInstrumentedTest.kt` 模板文件）需要真机/模拟器，CI 从不执行。
- ❌ **测试报告只在 `if: always()` 下上传 artifact（:71-81），没有 test-summary / 失败门禁**；报告不进 PR 界面，失败只看 job 红不红。
- ⚠️ `gradle/actions/setup-gradle@v4` 默认开启 Gradle 缓存；CI 每轮是干净 checkout，不会命中 UP-TO-DATE，但**本机 `./gradlew :shared:desktopTest` 会命中缓存重放旧结果** —— 本机验证必须加 `--rerun-tasks`。

---

## 4. 可观测性与性能基建

| 项 | 结论 | 证据 |
|---|---|---|
| 崩溃捕获 | **有**（三端，但 iOS 自认不可靠） | `app/src/main/kotlin/lovehan1me/HanimeApplication.kt:64` `Thread.setDefaultUncaughtExceptionHandler(CrashHandler(...))`；`app/src/main/kotlin/lovehan1me/app/crash/CrashHandler.kt:9`；共享 expect/actual 在 `shared/src/commonMain/kotlin/lovehan1me/app/crash/CrashRecovery.kt:22`，android actual `:6`、desktop actual `CrashRecovery.desktop.kt:8`（自设 handler）、desktop 入口 `desktopApp/src/main/kotlin/lovehan1me/desktop/Main.kt:102`。iOS 侧 `CrashRecovery.ios.kt:28` 注释自认 "NSSetUncaughtExceptionHandler 只覆盖 ObjC 异常，可靠方案需…" —— 即 Kotlin 崩溃捕不到 |
| 崩溃恢复 UI | 有 | `shared/src/commonMain/kotlin/lovehan1me/app/crash/CrashScreen.kt`；`app/.../ui/activity/CrashActivity.kt:30` 取 `EXTRA_LOGS` |
| 崩溃逻辑测试 | 有 | `shared/src/commonTest/kotlin/lovehan1me/app/crash/CrashRecoveryTest.kt`（5 用例） |
| 日志 | 有统一入口，**无规范** | `video/engine/src/commonMain/kotlin/lovehan1me/core/util/LogUtil.kt` + `LogUtil.{android,desktop,ios}.kt` 三端 actual；`Parser.kt:1229/1233` 等处直接 `LogUtil.d`。未见 tag 规范、级别门禁或脱敏 |
| 内存泄漏检测 | **没有** | 全仓（排除 `reference/`、`build/`）grep `LeakCanary` / `leakcanary` / `StrictMode` → **0 命中** |
| Benchmark / Baseline Profile | **没有** | `settings.gradle.kts:66-79` 只注册 `:app` `:shared` `:desktopApp` `:video:{contract,engine,ui,surface}`；全仓 `*.kts/*.toml` grep `baselineProfile` / `Benchmark` / `macrobenchmark` → 排除 reference 后 **0 命中** |
| 线上 APM / 崩溃上报 | **没有** | grep `sentry` / `bugly` / `umeng` → 0 命中；崩溃报告只落本地盘 |
| 性能观测 | 只有一次性探针 | `WatchFetchPerfLiveTest.kt:50-108` 手工计时 + `println`，见 2.2（零断言） |

---

## 5. 缺失的关键不变式守卫（8 条）

| # | 不变式 | 危险实现位置 | 为什么危险 / 现状 |
|---|---|---|---|
| 1 | **播放状态机合法转换**（`Idle→Preparing→Ready→Ended/Error`，尤其 `Error` 能否回到 `Preparing`） | `video/contract/src/commonMain/.../PlaybackState.kt:3-8`（五态枚举）、`PlaybackStateDerivation.kt` | 五态枚举本身不禁止非法迁移；`Error` 是否为陷阱态、重试后 `isSwitchingQuality`/`hasRenderedFirstFrame` 是否复位，无任何用例钉住。现有 `PlaybackStateDerivationTest`（8 断言）只测派生字段 |
| 2 | **进度边界钳制**（`seekTo/seekBy` 必须钳在 `[0, duration]`） | `shared/src/commonMain/kotlin/lovehan1me/feature/player/PlaybackController.kt:212` `seekTo((state.positionMs + deltaMs).coerceAtLeast(0L))` —— **只钳下界，无上界**；:208 `seekTo` 直传引擎，无钳制 | 长按快进/键盘 seek 可越过片尾，位置 > duration 后的进度条与续播落点行为未定义。`PlaybackControllerTest` 有 14 个用例，但 none 覆盖越界 seek |
| 3 | **分页幂等与边界**（同 page 重复请求结果一致；page 越界/超 maxPage 回落） | `shared/.../data/NetworkRepo.kt:668` `private fun <T> pageIOFlow(...)`、:76-85 `getHanimeSearch(page, …)`；`Parser.kt:1173` `parseMaxPage` 取不到时 `?: 1` | `parseMaxPage` 静默回落 1，页面总数错判不会报错只表现"翻不动"；`pageIOFlow` 无去重/无 in-flight 合并，快速下拉会并发重入。两个文件各 835 / 1251 行，**零测试** |
| 4 | **导航回退栈语义**（切 tab 弹回根、`launchSingleTop` 去重、`replaceTop` 退化） | `shared/.../app/navigation/main/TopLevelBackStack.kt:9-98`（自研回退栈，`linkedMapOf` + `SnapshotStateList`，:26 `updateBackStack` 全量 clear+addAll） | 98 行纯状态机，KDoc :29-35 自述曾有"界面钉在搜索页且不可逆"的历史 bug；**零测试**，且用 `mutableStateListOf` 而非 `rememberSaveable`，进程重建后栈全丢 |
| 5 | **配置变更/旋转后状态保留** | `shared/.../feature/video/VideoRouteHostScreen.kt:236` `isFullscreen`、:239 `sidebarVisible`、:240 `volume`、:241 `brightness`、:245 `startPositionUsed`、:246 `pendingPlayback` —— 全部 `remember { mutableStateOf }` | 1115 行宿主屏里 6 个关键 UI/播放态用 `remember` 而非 `rememberSaveable`（:234-235 的标题还额外 `remember(route.videoCode)`，换源即重置）。旋转/折叠屏展开后全屏态、音量、续播点丢失。全项目 `rememberSaveable` 有 102 处，但此文件 0 处 |
| 6 | **解析器对脏 HTML 的容错**（缺字段降级 vs 抛异常） | `shared/.../site/hanime1/Parser.kt:1212` `throwIfParseNull` 直接 `throw ParseException`；:1196 `childOrNull` 吞 `IndexOutOfBoundsException`；:1173 `parseMaxPage` 静默 `?: 1` | 同一个 Parser 里三种失败策略并存：必需字段抛异常（整页白屏）、可选字段只 log（:1222 `logIfParseNull`）、计数静默回落 1。站点改版一行 → "某个板块空了"不报错。唯一覆盖它的 2 个测试（9 用例）在 CI 上因夹具 gitignore 而跳过（见 2.1） |
| 7 | **切画质不丢位置 / 不丢首帧标记**（`selectQuality` 后 `startPositionMs` 延续、`isSwitchingQuality` 必须撤销） | `shared/.../feature/player/PlaybackController.kt:178` `selectQuality` → :308 `loadQuality` → :330 `startPositionMs = positionMs` | 逻辑有 `PlaybackControllerTest:157-195` 覆盖（好测试），但**真机/真流那一半** `DesktopQualitySwitchLiveTest.kt:82/106` 断言 `hasRenderedFirstFrame` 不丢，在 CI 上因未设 `HAN1ME_QUALITY_LIVE_URL_A/B` 永久跳过 → 真链路零守护 |
| 8 | **缓存键与登录态/域名的一致性**（切站或换账号后首页缓存必须 miss） | `shared/.../data/HomePageCache.kt:21` `homePageCacheKey()` = `"home_${safeHost}_${user}"`、:31 `discoverCacheKey()`；写入点 `NetworkRepo.kt:72` `onBody = { writeCachedHomeHtml(homePageCacheKey(), html) }` | key 由两个 `runCatching{…}.getOrDefault()` 拼成（:22-23），`SettingsRepository` 未初始化时静默退化成 `"default"/"anon"` —— 换域名后可能命中旧域名的缓存 HTML，用旧站结构去喂 `Parser.homePageVer2`（`Parser.kt:79`）。`discover_` key 注释明确说"不含筛选参数"，边界靠约定而非断言 |

---

## 6. 如果只能加 20 个测试，放哪里

优先级按"失效概率 × 失效后用户可见度 × 现有覆盖"：

| 配额 | 落点 | 理由 |
|---:|---|---|
| 6 | `Parser` 脏 HTML 容错（新建 `shared/src/commonTest/.../site/hanime1/ParserDirtyHtmlTest.kt`） | 用**内联字符串夹具**（学 `CommentParserTest.kt:33-40` 的写法，不要 `.workbuddy/` 文件）覆盖 `throwIfParseNull`(:1212) / `logIfParseNull`(:1222) / `parseMaxPage`(:1173) 三条失败路径 + 首页 14 行下标映射。这样 CI 上真跑，不再依赖 gitignore 夹具 |
| 4 | `TopLevelBackStackTest`（新建 `shared/src/commonTest/.../app/navigation/main/`） | 98 行自研栈、零测试、有历史 bug；覆盖切 tab 弹根、`launchSingleTop` 去重、`replaceTop` 退化、栈重建 |
| 3 | `NetworkRepo` 分页（`shared/src/commonTest/.../data/`） | `pageIOFlow`(:668) 的同页幂等、超 maxPage 回落、并发重入去重；配 fake service，不碰网络 |
| 3 | `PlaybackController` 进度边界（扩充现有 `PlaybackControllerTest.kt`） | 补 `seekTo` 越界(:208)、`seekBy` 越过 duration(:212)、`duration=0` 时的除法/比例 |
| 2 | `HomePageCache` 键一致性（`shared/src/commonTest/.../data/`） | 换域名/换 uid 必须 miss；`SettingsRepository` 未初始化时不退化成同一 key |
| 2 | `VideoRouteHostScreen` 状态保留（desktopTest，Compose 测试） | 断言 :236/:240/:245 三个态在重建后保留 —— 需要先改成 `rememberSaveable`，这 2 个测试同时是重构的验收门禁 |
| **20** | | |

---

## 7. 最终判定

**半真绿（半真）**。

- **真的部分**：CI 没有 `-x test` / `continue-on-error`，`:shared` 与 `:video:{contract,engine,ui}` 的 `desktopTest` + `testAndroidHostTest` 是真的执行、失败会变红（ci.yml:64、:69）；`PlaybackControllerTest` 一类的测试用 fake 记录调用并断言具体值，有真实区分力。
- **纸面的部分**：≥18 个用例靠 `println("[skip]") + return` 静默变绿（2.1），3 个用例零断言其中 2 个还会在 CI 上真发网络请求（2.2），8 个 iOS 用例所在源集 CI 从不执行（3），而与风险最集中的三块 —— `Parser.kt`(1251 行) / `NetworkRepo.kt`(835 行) / `TopLevelBackStack`+三大 UI 巨物 —— 对应的要么是跳过、要么是零覆盖。
- **结论**：测试数量（695 个 `@Test`、实际执行 684）与守护强度不成比例。**绿不等于被测**：唯一一次实测记录里，唯一失败是一条环境齐全才跑得起来的 Live 测试（第 8 节），而 CI 上它恰恰因为缺产物而"绿"。当前 CI 的红/绿只能证明"播放器控制层与弹幕/网络工具层没退化"，不能证明解析、分页、导航、状态保留没退化。

### 8. 交叉验证：历史测试报告 XML（只读，未跑任务）

读取 `*/build/test-results/desktopTest/*.xml`（本机遗留产物，未触发任何 gradle 任务）：

```
shared  + video/{contract,engine,ui}  desktopTest 汇总
tests=684  skipped=0  failures=1  errors=0
报告 timestamp 样例：2026-10-06T15:55:31.368Z（shared）、15:55:31.322Z（contract）
```

**这条数据钉死了两件事：**

1. **`skipped=0` 但"跳过"确实发生了** —— `JavchuHomePageParseTest[desktop] tests="2" skipped="0" failures="0"`、`AuthorPlaylistParserTest tests="5" skipped="0"`、`CloudflareCdpLiveTest tests="1" skipped="0"`、`DesktopMpvPlaybackLiveTest tests="1" skipped="0"`、`DesktopQualitySwitchLiveTest tests="1" skipped="0"`、`WatchFetchPerfLiveTest tests="1" skipped="0"`、`SitePageCaptureLiveTest tests="1" skipped="0"`、`ThemeColorAuditTest tests="1" skipped="0"`。JUnit XML 里**没有任何 skip 标记**，静默跳过的用例与真跑过的用例在报告里完全无法区分 —— 这就是"纸面绿"的物证。
2. **唯一失败恰好是 Live 测试**：

   ```
   <testcase name="视频CDN经网关可建立连接[desktop]">
   <failure message="java.lang.AssertionError: 视频 CDN 经网关应当能建立连接，502 说明 CNAME 降级没生效">
   ```

   即 `shared/src/desktopTest/kotlin/lovehan1me/data/network/EchGateLiveTest.kt` 在本机（存在 `echgate` 产物）是**红的**；而在 CI 的干净 runner 上因找不到产物走 `:190/:225/:263` 的 `LIVE SKIP` 分支 → **绿**。同一份代码，环境越完整越红、环境越残缺越绿。这一类测试的绿不携带任何正确性信息。

   注：`EchGateLiveTest` 的 `:162` 用例名为"不经网关直连站点必然失败"，其门类也是"本机直连未被阻断则 SKIP（:179）"——即正例反例都可跳过，最坏情况下 4 个用例一个都不验证。

**684 这个数字也交叉印证了 1. 的清点**：可达 desktop 的源集 `shared commonTest(273)+desktopTest(259)` + `contract(48)` + `engine(28)` + `ui(77)` = 685 `@Test`，实际执行 684（差 1，系个别用例未被 desktop 目标收集）。数字对得上，说明清点无误。

### 审计边界说明

- 本次为只读审计，未修改任何源文件（唯一写入为本文档）。
- 未执行全量编译/全模块 CI；结论均来自源码与配置文件的静态核查。
- 未采信 `docs/`、`README.md`、`POSITIONING.md` 与 git 历史中的任何自述性描述。

## 1. 测试资产清点

全量枚举 `*Test.kt` / `*Spec.kt`（已排除 `reference/`、`player/`、`generated-images/`）：

| 测试源集 | 文件数 | `@Test` 注解数 | 行数 |
|---|---:|---:|---:|
| `shared/src/commonTest` | 34 | 273 | 4175 |
| `shared/src/desktopTest` | 42 | 259 | 7216 |
| `shared/src/iosTest` | 2 | 1 | 142 |
| `shared/src/androidHostTest` | 1 | **0** | 10 |
| `video/contract/src/commonTest` | 5 | 45 | 1011 |
| `video/contract/src/desktopTest` | 1 | 3 | 98 |
| `video/engine/src/commonTest` | 2 | 15 | 250 |
| `video/engine/src/desktopTest` | 3 | 13 | 398 |
| `video/engine/src/iosTest` | 1 | 7 | 228 |
| `video/ui/src/commonTest` | 7 | 65 | 1147 |
| `video/ui/src/desktopTest` | 3 | 12 | 327 |
| `app/src/androidTest` | 2 | 2 | 122 |
| **合计** | **103** | **695** | **15125** |

### 1.1 零测试的核心模块（重点风险）

- `shared/src/commonMain/kotlin/lovehan1me/site/hanime1/Parser.kt`（1251 行）— **无任何 Parser/HomePage/VideoPage 解析单测**。`desktopTest` 里只有 3 个解析器相关文件：`site/hanime1/AuthorPlaylistParserTest.kt`、`site/hanime1/CommentParserTest.kt`、`site/hanime1/JavchuHomePageParseTest.kt`，未覆盖主 Parser。
- `shared/src/commonMain/kotlin/lovehan1me/data/NetworkRepo.kt`（835 行）— **无单测**。
- `video/surface` — **整个模块无测试源集**（`find` 仅命中 `build/` 下的资源生成目录，无 `src/*Test`）。
- `desktopApp`、`echgate`、`app`（业务侧）— **零本地单测**；`app` 只有 `app/src/androidTest/kotlin/lovehan1me/ExampleInstrumentedTest.kt`（Android Studio 模板产物）与 `MediampExoDevicePlaybackTest.kt`（需真机，CI 不跑）。
- 三大 UI/逻辑巨物均**无渲染/语义测试**：
  - `shared/src/commonMain/kotlin/lovehan1me/feature/video/VideoRouteHostScreen.kt`（1115 行）
  - `shared/src/commonMain/kotlin/lovehan1me/feature/search/AdvancedSearchSheet.kt`（1082 行）
  - `shared/src/commonMain/kotlin/lovehan1me/feature/settings/NetworkSettingsScreen.kt`（967 行）
- `shared/src/androidHostTest/kotlin/lovehan1me/core/platform/ExpectedPlayerCapabilities.android.kt`（10 行）不是测试，是给 `commonTest` 提供 `actual` 实现的桩文件 —— Android 侧**零本地 JVM 测试**。

### 1.2 结构性观察

- 695 个 `@Test` 中，**desktopTest 占 259 个（37%）**，其中相当一部分命名为 `*LiveTest`（`EchGateLiveTest`、`CloudflareCdpLiveTest`、`DesktopMpvPlaybackLiveTest`、`DesktopQualitySwitchLiveTest`、`WatchFetchPerfLiveTest`、`SitePageCaptureLiveTest`），这类"Live"测试依赖真实网络/真实 MPV 进程，正常情况下必须被跳过或会随机失败（见第 2 步）。
- 无任何 `*Spec.kt` 文件；命名风格统一为 `*Test.kt`。
