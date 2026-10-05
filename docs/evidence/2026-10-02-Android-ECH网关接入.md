# Android ECH 网关接入取证

> 计划：`docs/plan/Android-ECH网关补齐.md`；决策：`docs/decisions.md`。
> 范围：里程碑「可行性打通 / 接入与拆分 / 打包与 CI」的工程侧。
> **真机验收（V1–V4、16 KB 模拟器冒烟）不在本文件内** —— 需真机 + 受限网络执行。

## AAR 产出与 16 KB 页对齐

**结论**：`gomobile bind` 一次成功；两个 ABI 的全部 LOAD 段对齐都是 16 KB，
V7 的静态检查项达标 —— 风险表里"不达标先评估升级 Go/gomobile"这条先拟对策用不上。

- 取证日：2026-10-02
- 环境：macOS（darwin arm64）/ Go 1.26.6 / gomobile（`~/go/bin/gomobile`，
  自报 `binary is out of date` 但 `bind` 正常）/ Android NDK 28.2.13676358 / JDK 21
- 重跑：
  1. `ANDROID_HOME=$HOME/Library/Android/sdk sh echgate/build-android.sh`
  2. `unzip -o -q app/libs/Echgate.aar -d /tmp/aarcheck`
  3. `<NDK>/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-objdump -p /tmp/aarcheck/jni/*/libgojni.so | grep LOAD`
- 观测：
  - 产物 `app/libs/Echgate.aar`，10,478,091 B，
    sha256 `0c63df85defc8401a605b69a8c4d5c32654ffde13a17b4988079bd59e5e92ab0`（31 秒构建完成）
  - AAR 内容：`jni/arm64-v8a/libgojni.so`、`jni/x86_64/libgojni.so`、`classes.jar`、
    `AndroidManifest.xml`（`package="go.gate.gojni"`、`minSdkVersion 29`）、`proguard.txt`
  - 两个 `.so` 各 4 个 LOAD 段，全部 `align 2**14`（= 0x4000 = 16 KB）
  - **`proguard.txt` 自带 `-keep class go.** { *; }` 与 `-keep class gate.** { *; }`** ——
    它作为 consumer rules 自动并入，`proguard-rules.pro` 不需要为 gomobile 生成的类补规则
    （计划文档里"为 gomobile 生成的类补 keep 规则"这一步实际只需核对，不需新增）
  - `javap` 得到的 Java API：`gate.Gate.startFlat(String,String,String,String,String,String,String,long) → gate.Server`；
    `gate.Server.addr()` / `gate.Server.close()`；生成包名 `gate`，JNI 库名 `gojni`
- 失效条件：`gate` 包源码或 Go / gomobile / NDK 版本变更后，AAR 指纹与对齐结论**同时**失效
  （16 KB 对齐是链路产物、不是源码承诺），必须重跑本节命令。

## D1 验证窗出口对齐：候选 a 可行性核验（通过）

**结论**：候选 a 通过 —— Android 的 WebView 可以经 `androidx.webkit` 的 `ProxyController`
把出站覆盖到网关的 CONNECT 隧道，且判据能与桌面复用同一处，不产生第二处判定。

- 取证日：2026-10-02
- 环境：`androidx.webkit:webkit:1.17.1`（Google Maven，pom 依赖仅 annotation 1.8.1 /
  annotation-experimental 1.3.0 / core 1.1.0 / jspecify 1.0.0）；`:app` minSdk 29、compileSdk 37
- 重跑：以下三条依据都可复核 ——
  - **官方 API**：`ProxyController` 自 androidx.webkit **1.1.0** 起提供；
    `getInstance()` 在 `WebViewFeature.PROXY_OVERRIDE` 不受支持时抛
    `UnsupportedOperationException`，故使用前必须 `isFeatureSupported`（本仓已按此门控）。
    `setProxyOverride(ProxyConfig, Executor, Runnable)` 文档原文是
    "Sets ProxyConfig which will be used by **all WebViews in the app**"，
    并注明 "calling setProxyOverride will cause any existing system wide setting to be ignored"
    —— 即**进程级、顶掉系统代理**，所以本仓在验证页销毁时 `clearProxyOverride`。
  - **代理规则形状**：`ProxyConfig.Builder.addProxyRule` 被文档钉死为 `[scheme://]host[:port]`，
    "Scheme is optional, if present must be **HTTP, HTTPS or SOCKS** and defaults to HTTP"
    ⇒ `http://127.0.0.1:<port>` 合法；对 https 目标即标准 CONNECT 语义。
    **API 级别无关**（WebView 可独立更新），唯一的门槛是 WebView 版本对 `PROXY_OVERRIDE`
    的支持，而本仓验证窗本就要求 WebView ≥ 120。
  - **桌面先例（同一判定）**：`shared/src/desktopMain/kotlin/lovehan1me/app/web/CloudflareCdp.kt`
    用 `currentEgressState().gate.connectTunnelUrl()` 拼 `--proxy-server=`，
    注释写明理由正是"两边同出口"。
  - **网关侧隧道实现**：`echgate/gate/gate.go` 的 `handleConnect`
    （DoH 解析 + 逐 IP 拨号 + 裸 TCP，回 `HTTP/1.1 200 Connection Established`）。
- **边界（必须写清，否则会被读成"验证窗在受限网络下也能用"）**：
  CONNECT 隧道里客户端自己做 TLS、SNI 仍是明文，**网关帮不上 ECH 的忙**
  （`echgate/main.go` 两条通道一节原文："浏览器走它没意义（这正是最初"不做 CONNECT"的原因，
  结论仍成立）"）。所以 a 解决的是**同出口** —— `cf_clearance` 绑 UA + 出口 IP，
  App 经网关出站的出口是本机 IP、WebView 默认走系统代理＝代理 IP，不一致就会"验证过了仍然要验证"。
  **a 不解决 SNI 阻断下验证页自身加载不了**；后者三端同构、属既有事实，
  故"验证页加载失败时给引导"仍可作为独立增量另立一项。
- 失效条件：`androidx.webkit` 依赖被移除；网关删除 CONNECT 隧道或不再做 DoH 解析；
  或 `PROXY_OVERRIDE` 的支持度成为瓶颈（届时回落"原样加载 + 引导文案"）。

## 运行时门面拆分与 Android 进程内起服

**结论**：原 `EchGateProcess` 的骨架下沉为 jvm 共享门面 `EchGateRuntime`，
桌面 / Android 只在 `EchGateStarter` 处分叉；消费方（拦截器 / 设置页 / 站点切换）
不再引用任何具体运行时。`gate.*` 的引用只能落在 `:app`（AGP 不允许 library 模块
消费本地 `.aar`），与 iOS 把起服放在壳工程是同一种分工。

- 取证日：2026-10-02
- 环境：同"全量验证"一节的工具链
- 重跑：`./gradlew :shared:compileAndroidMain :shared:compileKotlinDesktop :app:compileDebugKotlin :desktopApp:compileKotlin --console=plain`
- 观测：
  - 新增门面 `shared/src/jvmMain/kotlin/lovehan1me/data/network/EchGateRuntime.kt`
    （骨架 + `EchGateStarter` 接口 + `ensureEchGateway` 的 jvm actual）
  - 桌面运行时迁至 `shared/src/desktopMain/kotlin/lovehan1me/data/network/DesktopEchGateStarter.kt`
    （逻辑不变，只是不再自己管"何时拉起"与线程）
  - 新增 Android 起服器 `app/src/main/kotlin/lovehan1me/echgate/AndroidEchGateStarter.kt`，
    由 `HanimeApplication.onCreate` 装配、按开关后台起服，并加 `StartupTrace.mark("ech-gate")`
  - 新增 `shared/src/androidMain/kotlin/lovehan1me/app/web/GateWebViewProxy.kt`（D1 候选 a）
  - `shared/src/jvmMain/.../EchGateProcess.kt` 已删除；计划文档内三处指向它的
    `file:line` 引用已同步更新（否则 V8 会红）
  - 首轮 `:app:compileDebugKotlin` 通过（含本地 AAR 依赖解析，1m33s）
- 失效条件：`EchGateStarter` 接口增删方法会让两端 starter 同时失配，必须同步改；
  `:app` 若被拆出 library 模块，本地 AAR 接线需改为本地 maven 仓坐标。

## 守卫测试反向验证（4/4 PASS，0 SKIP）

**结论**：新增的 4 条门面守卫都"关掉即转红"，不是恒真断言。

- 取证日：2026-10-02
- 环境：`:shared:desktopTest`（JVM）；脚本为临时脚本（`/tmp/rv_echgate.py`，未入库）
- 重跑：`python3 /tmp/rv_echgate.py`（每轮先删
  `shared/build/test-results/desktopTest/*EchGateRuntimeTest*.xml`，
  `finally` 还原源码；还原后再重跑全量基线）
- 观测：

  | 被关掉的规则 | 期望转红的用例 | 实测 |
  |---|---|---|
  | `stop()` 先落 `Stopped` | `主动停止落 Stopped 且不记成失败` | PASS（连带"起服途中被停止…"也红，符合预期） |
  | `leaveStartingOnFailure` 只在 `Starting` 时覆盖 | `起服途中被停止不会把 Stopped 覆盖成失败` | PASS |
  | 未装配 starter 时落 `Failed` 并返回 false | `未装配运行时 start 落失败而非卡住` | PASS |
  | 起服不留终态时兜一次 `Failed` | `一个不留终态的起服器不会把状态卡在 Starting` | PASS |

- 失效条件：门面重构后片段位置变化会让脚本报 SKIP；脚本把 SKIP 计为**未覆盖**，
  不会静默变绿，但需按新位置更新片段后再跑。

## 全量验证（编译 4 组 / 单测 8 组 / iOS 两架构）

**结论**：接入后工程侧全绿，无失败、无跳过。

- 取证日：2026-10-02
- 环境：Gradle 9.4.1 / JDK 21 / Kotlin 2.4.10 / AGP 9.2.1；`:app` minSdk 29、
  compileSdk 37、targetSdk 36
- 重跑：与 `.github/workflows/ci.yml` 的三个 job 逐条对应 ——
  1. 编译与打包：`./gradlew :video:contract:compileAndroidMain :video:contract:compileKotlinDesktop :video:engine:compileAndroidMain :video:engine:compileKotlinDesktop :video:ui:compileAndroidMain :video:ui:compileKotlinDesktop :video:surface:compileAndroidMain :video:surface:compileKotlinDesktop :shared:compileAndroidMain :shared:compileKotlinDesktop :app:compileDebugKotlin :desktopApp:compileKotlin :app:assembleDebug :app:assembleRelease --console=plain` → **BUILD SUCCESSFUL in 8m47s**
  2. 单测：`./gradlew :video:contract:desktopTest :video:engine:desktopTest :video:ui:desktopTest :shared:desktopTest :video:contract:testAndroidHostTest :video:engine:testAndroidHostTest :video:ui:testAndroidHostTest :shared:testAndroidHostTest --console=plain`
  3. iOS：`./gradlew :app:assembleDebug :video:engine:compileKotlinIosArm64 :video:engine:compileKotlinIosSimulatorArm64 :video:ui:compileKotlinIosArm64 :video:ui:compileKotlinIosSimulatorArm64 :video:surface:compileKotlinIosArm64 :video:surface:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --console=plain` → **BUILD SUCCESSFUL in 2m13s**
- 观测（用例数由 `build/test-results/**/*.xml` 汇总，不靠控制台推断）：
  - `desktopTest`：**421 用例，0 失败，0 错误，0 跳过**
  - `testAndroidHostTest`：**257 用例，0 失败，0 错误，0 跳过**
  - ⚠️ **口径更正**：以上两行原文写作"4 模块"，实际只覆盖 `:shared` ——
    统计 glob `*/build/test-results/...` 漏掉了嵌套的 `video:contract/engine/ui`，
    并把非 Gradle 模块目录 `player/` 的残留报告（1 / 36 用例）算了进来。
    四模块合计的正确数字见最新一轮（`desktopTest` 576、`testAndroidHostTest` 346，
    见 `docs/evidence/2026-10-02-16KB模拟器冒烟.md` 与竞态修复取证的全量验证一节）。
  - 编译（Android + Desktop）与 iOS 两架构编译：全部 BUILD SUCCESSFUL
- 失效条件：单测基线随代码演进失效，须以最近一次实跑为准；本节的用例数
  只对上述 commit 成立。

## 打包接线、R8 与体积实测

**结论**：AAR 作为 `:app` 的本地文件依赖接线成功；AAR 自带的 keep 规则足够，
无需新增；发布包（限 arm64）体积增量约 **2.56 MB**。

- 取证日：2026-10-02
- 环境：同上；`app/build.gradle.kts` 的 ABI splits 当前按
  `gradle.startParameter.taskRequests.toString().contains("Release")` 判定
- 重跑：
  1. `./gradlew :app:assembleDebug :app:assembleRelease --console=plain`
  2. `unzip -lv app/build/outputs/apk/release/LoveHan1me-v26.3.2.apk | grep libgojni`
  3. `grep -c '^gate\.\|^go\.' app/build/outputs/mapping/release/usage.txt`
  4. 单独构建（不要与 Release 同批）：`./gradlew :app:assembleDebug`
- 观测：
  - **R8**：`mapping.txt` 里 `gate.*` / `go.*` 共 16 个顶级条目全部是
    `X -> X:`（原样保留），`usage.txt` 里被移除的 `gate.*` / `go.*` 条目数为 **0**
    ⇒ AAR 的 consumer 规则（`-keep class go.** { *; }` / `-keep class gate.** { *; }`）
    生效，`proguard-rules.pro` 无需为 gomobile 生成的类补任何规则
  - **体积**（压缩后，即对 APK 的真实贡献）：
    | 产物 | 体积 |
    |---|---|
    | `lib/arm64-v8a/libgojni.so` | 未压缩 6,978,264 B / APK 内 2,687,068 B（release） |
    | `lib/x86_64/libgojni.so` | 未压缩 7,552,728 B / APK 内 3,209,380 B（debug） |
    | release APK 合计 | 14.61 MB（含上述 2.56 MB） |
    | debug APK（单独构建，含两 ABI） | 106.39 MB |
    | AAR 本体 | 10,478,091 B（`.so` 未压缩 12.8 MB + 13.35 MB） |
  - **ABI splits 的一个坑**：把 `assembleDebug` 与 `assembleRelease` 放在同一条 Gradle
    命令行里，`startParameter` 里含 "Release" ⇒ **debug 包也会命中 splits**，
    只剩 `arm64-v8a`（101.48 MB，无 x86_64）。想在模拟器上调试必须**单独**
    跑 `./gradlew :app:assembleDebug`（实测该命令产出的包同时含两个 ABI）。
- 失效条件：`:app` 的 splits 规则或 minify 配置变更后，本节体积与 keep 结论同时失效；
  AAR 重构建后 `.so` 尺寸会变，须重跑并更新本表。
