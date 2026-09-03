# Han1meViewer → Kotlin Multiplatform 重构规划

> 目标：把现有纯 Android（Jetpack Compose）应用，演进为 **Android + Windows/macOS/Linux 桌面 + iOS** 三端共享 UI 与业务逻辑的 KMP 工程。
> 编制日期：2026-09-01 ｜ 复审重排：2026-09-02
> 状态：**P0 / P1-pre / P1 / P2a 已完成；P2b 步骤 2·3 已完成（DataStore/LogUtil/SettingsRepository + Room 4 库全部下沉）**，
> 仅余 P2b 步骤 1（`applicationContext` 全局 Context 收口）顺延；下一阶段 P3（网络层）。

本文替代之前的提案版 `KMP_MIGRATION_PLAN.md`。旧提案有三处关键结论经核实为**错误**，已在第 2 节逐条纠正——这直接决定了工作量从「重写级」降为「搬迁级」。

***

## 1. 现状审计（实测数据）

代码规模：`app/src` 下 **337 个 Kotlin 文件 / 53,589 行**。单模块 `:app`，`settings.gradle.kts` 仅 `include(":app")`。

### 1.1 平台耦合点分布（按涉及文件数）

| 依赖                      | 涉及文件  | 说明                                                    |
| ----------------------- | ----- | ----------------------------------------------------- |
| `androidx.compose.*`    | 156   | UI 主体                                                 |
| `androidx.lifecycle.*`  | 55    | ViewModel / `collectAsStateWithLifecycle`             |
| `coil`                  | 21    | 图片加载                                                  |
| `okhttp3`               | 25    | 网络与拦截器、DNS、Cookie                                     |
| `androidx.room`         | 24    | 5 个 Database / 8 个 DAO / 12 个 Entity                  |
| `java.time`             | 16    | 需换 `kotlinx-datetime`（项目已引入）                          |
| `java.io`               | 17    | 文件与流                                                  |
| `retrofit2`             | 8     | 5 个 Service 接口                                        |
| `java.net`              | 8     | `InetAddress` 等                                       |
| `androidx.work`         | 4     | 下载与更新任务                                               |
| `androidx.glance`       | 5     | 「冲了么」桌面小组件（Android 专属）                                |
| `androidx.media3`       | 3     | ExoPlayer 播放内核                                        |
| `androidx.documentfile` | 2     | SAF 下载目录                                              |
| `androidx.biometric`    | 1     | 应用锁                                                   |
| **`org.jsoup`**         | **2** | **仅** **`Parser.kt`（48 处选择器）与** **`GetchuParser.kt`** |

### 1.2 关键结构

- **导航层**：`ui/navigation/` 共 29 个文件 / 4,460 行，基于 Navigation 3 typed routes。

- **播放器**：`ui/player/` 共 8 个文件 / 1,290 行，**已存在** **`PlaybackEngine`** **接口 + 4 个实现**（Exo / System / Mpv / Cast），平台差异已被封装。

- **设置**：`DataStoreManager` 约 130 个配置项，基于 Preferences DataStore。

- **工具层**：`io.github.daisukikaffuchino.utils` 10 个文件 / 549 行，Android 耦合集中（含 `lateinit var applicationContext: Context`）。

- **原生层**：`externalNativeBuild` + `mpv-lib`（JNI，仅 arm64-v8a）。

### 1.3 最大文件（迁移时需要重点照顾）

| 行数   | 文件                                            |
| ---- | --------------------------------------------- |
| 1936 | `ui/screen/video/VideoPlayerUi.kt`            |
| 1574 | `ui/screen/video/VideoIntroductionScreen.kt`  |
| 1184 | `logic/Parser.kt`                             |
| 1058 | `ui/screen/video/VideoRouteHostScreen.kt`     |
| 914  | `ui/screen/search/SearchScreen.kt`            |
| 850  | `ui/screen/settings/NetworkSettingsScreen.kt` |

### 1.4 复审补充实测（2026-09-02，重排依据）

P0\~P2a 落地后对剩余耦合面的定向审计，五条结论直接驱动 §6 重排：

1. **ViewModel 层几乎纯净**：全部 ViewModel 文件中仅 `SearchViewModel` 含 Android import。
   → P3 完成后 `NetworkRepo` + 全部 ViewModel 可随网络层一起下沉，不需要单独阶段。
2. **网络拦截器链依赖数据层**：`HDns` / `HCookieJar` / `HProxySelector` 均依赖 `SettingsRepository` 与 `LogUtil`；
   `CloudflareInterceptor` 构造需要 `applicationContext`；`ServiceCreator` 共管 3 个 client（`hClient` / `downloadClient` / `getchuClient`）。
   → **P2b（DataStore + Context 收口）是 P3 的硬前置**，P2b 内部必须按「Context → DataStore → Room」排序。
3. **解析层的真实耦合不止 jsoup**：`Parser.kt` 除 jsoup 外还依赖 `R` 资源（`applicationContext.getString`）、
   `TagLocalizer` / `DisplayTextLocalizer`（外部 utils 库 + R + `java.util.Locale`）。
   → 原计划把字符串资源排在 P6 太晚，**资源系统 KMP 化必须提前到 P4 前置**。
4. **UI 层 Android 耦合高度集中**：`BackHandler` / `LocalContext` / `AndroidView` / Glance 等共 37 文件 / 109 处，
   其中 `CheckInWidgetProvider`（Glance 专属 16 处）、`HomeSettingsRoute`（15）、`DownloadSettingsRoute`（5）、`VideoPlayerUi`（4），其余文件均 ≤3 处。
   → 约 120 个 UI 文件近乎零耦合，P6 是机械劳动；真正的难点集中在个位数文件。
5. **三方 UI 库只占 3 个文件**：`kyant m3color`（Theme.kt，22 处）、`aboutlibraries`（OpenSourceLicensesScreen）、
   `compose-avatar-cropper`（AvatarCropScreen）。→ 处置方案见 P6 表格，不构成整体阻塞。

***

## 2. 对旧提案的三处关键纠正

> 这三条是本次重构工作量评估的分水岭。旧提案据此把导航与 UI 判为「高风险重写」，实际都不是。

### ❌ 纠正 1：Compose 迁移不需要改 import

旧提案称「`androidx.compose.*` import 全量改为 `org.jetbrains.compose.*`」——**错误**。

Compose Multiplatform 与 Jetpack Compose 共用**同一套包名**（`androidx.compose.foundation`、`androidx.compose.material3`、`androidx.compose.runtime`…）。区别只在 **Maven 坐标**：Android 目标解析到 Google 的 `androidx.compose.material3:material3`，其他平台解析到 JetBrains 的 `org.jetbrains.compose.material3:material3`，由 Gradle Module Metadata 自动完成。

> 依据：JetBrains 官方文档 *Relationship between Compose Multiplatform and Jetpack Compose*。

**结论**：156 个文件的 Compose 迁移是**换依赖坐标**，不是改 import。只需在 `libs.versions.toml` 声明 CMP，源码基本零改动。

### ❌ 纠正 2：Navigation 3 不用重写成 Decompose

旧提案称「`androidx.navigation3` 是 Android-only，需重写为 Decompose」——**错误**。

- `androidx.navigation3:navigation3-runtime` **本身已是 KMP**（JVM/Native/Web）。

- `NavDisplay` 所在的 `navigation3-ui` 由 JetBrains 提供跨平台移植：`org.jetbrains.androidx.navigation3:navigation3-ui`，Compose Multiplatform 自 1.10 起全平台支持。

- 已核实该文件 `commonMain/androidx/navigation3/ui/NavDisplay.kt`，并带 `iosMain` / `webMain` 平台实现。

**结论**：4,460 行导航代码**保留**，只需把 artifact 从 Google 坐标换成 JetBrains 坐标。省掉最大一块重写工作量。

### ❌ 纠正 3：网络层可以不完全重写

旧提案称「Retrofit → Ktor，拦截器/错处模型不同，调用层重写」——**部分错误**。

Ktor 提供 **OkHttp 引擎并支持注入既有 OkHttpClient**：

```kotlin
HttpClient(OkHttp) {
    engine { preconfigured = existingOkHttpClient }
}
```

Android 与桌面端都是 JVM，可在保留 `HDns`（含 DoH）、`HCookieJar`、`HProxySelector`、`CloudflareInterceptor`、`SpeedLimitInterceptor` 全链路的前提下，把调用层迁到 Ktor。仅 iOS（Darwin 引擎）需要降级处理自定义 DNS/DoH。

**结论**：25 个文件的 OkHttp 基础设施**大部分复用**，只重写 8 个文件的 Retrofit Service 调用层。

### ✅ 额外利好：播放器已有抽象

`PlaybackEngine` 接口已存在（`load/play/pause/seekTo/setPlaybackSpeed/attachSurface/release`），4 个实现。P5 只需在 `expect/actual` 层面按平台提供工厂，桌面端补 VLCJ/mpv 实现，iOS 端补 AVPlayer 实现，1936 行的 `VideoPlayerUi.kt` 可整体下沉到 `commonMain`。

***

## 3. 目标架构

```
:shared            Kotlin Multiplatform 模块
├── commonMain     UI(Compose MP) + ViewModel + 导航(Navigation3) + 业务 + 解析 + 网络接口 + Room
├── androidMain    ExoPlayer/MPV/Cast 引擎、OkHttp 引擎、WorkManager、Glance、Biometric、SAF
├── desktopMain    VLCJ/mpv 引擎、OkHttp 引擎、窗口与托盘、文件系统
└── iosMain        AVPlayer 引擎、Darwin 引擎、UIKit 桥接

:app            现有 Android 应用（迁移期保持可编译，P8 末移除）
:desktopApp     Compose Desktop 入口（Windows / macOS / Linux）
:iosApp         Xcode 工程，链接 :shared 产出的 framework
```

**核心原则**：自下而上增量迁移，**`:app`** **在每一个阶段结束时都必须可编译**。不做 big-bang 替换。

***

## 4. 版本矩阵（已实测核对）

| 组件                           | 版本                             | 依据                                                                                                                                              |
| ---------------------------- | ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Kotlin                       | `2.4.10`                       | 沿用项目现值                                                                                                                                          |
| AGP                          | `9.2.1`                        | 沿用；CMP 1.12 要求 ≥ 9.1.1 ✅                                                                                                                        |
| Gradle                       | `9.4.1`                        | 沿用 wrapper                                                                                                                                      |
| **Compose Multiplatform**    | **`1.12.0`**                   | 与 Kotlin 2.4.10 对齐（CMP 仓库提交 #5695）                                                                                                              |
| CMP material3                | **`1.9.0`**                    | ⚠️ JetBrains 的 material3 移植未跟随主版本，CMP 1.12.0 插件内置即为 1.9.0（已从 `ComposePlugin$Dependencies.class` 实证）；`androidx.compose.material3` 无 KMP 变体，不能直接用 |
| navigation3-ui (JetBrains)   | `1.1.1`                        | Maven Central 最新稳定                                                                                                                              |
| navigation3-runtime (Google) | `1.1.1`                        | nav3-ui 1.1.1 的 POM 声明依赖                                                                                                                        |
| lifecycle-\* (JetBrains)     | `2.11.0`                       | 与项目现有 lifecycle 2.11.0 对齐                                                                                                                       |
| Room                         | `2.8.4`                        | 沿用；2.7+ 起支持 KMP                                                                                                                                 |
| sqlite-bundled               | `2.7.0`                        | Room KMP 需要自带 SQLite 驱动                                                                                                                         |
| Ktor                         | `3.5.2`                        | Maven Central 最新稳定                                                                                                                              |
| ksoup                        | `0.6.0`                        | jsoup 的 KMP 实现                                                                                                                                  |
| Coil 3                       | `:app` 3.4.0 / `:shared` 3.6.1 | ⚠️ Coil 3.4.0 内置 skiko 与 CMP 1.12 不对齐会报 Skiko 版本冲突警告，共享模块需用 3.6.1；P6 迁移 UI 时 `:app` 一并对齐                                                        |
| KSP                          | `2.3.2`                        | 沿用（P2 引入 Room 代码生成）                                                                                                                             |

> **iOS 目标**：只声明 `iosArm64` + `iosSimulatorArm64`。`iosX64` 已被 Compose Multiplatform 移除（CMP 提交 #5638），不要再加。

***

## 5. 依赖映射表（修正版）

| 现状                                                         | KMP 方案                                                                                                         | 平台    | 风险                                    |
| ---------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ----- | ------------------------------------- |
| `androidx.compose.*` (BOM)                                 | `compose.runtime/foundation/material3/ui`（CMP DSL）                                                             | A/D/I | **低**（换坐标，源码不动）                       |
| `androidx.navigation3.*`                                   | `org.jetbrains.androidx.navigation3:navigation3-ui` + Google `navigation3-runtime`                             | A/D/I | **低**（换坐标）                            |
| `androidx.lifecycle.*`                                     | `org.jetbrains.androidx.lifecycle:lifecycle-{runtime,viewmodel}-compose`                                       | A/D/I | 低                                     |
| `retrofit` + `okhttp`                                      | `ktor-client-*`；Android/Desktop 用 **OkHttp 引擎并注入现有 client**                                                    | A/D/I | **中**（仅改 Service 调用层）                 |
| `org.jsoup`                                                | `com.mohamedrejeb.ksoup:ksoup-html`                                                                            | A/D/I | 中（仅 2 文件，需快照对比 48 处选择器）               |
| `androidx.room`                                            | Room KMP + `sqlite-bundled` + `kspCommonMainMetadata`                                                          | A/D/I | 中（数据库 Builder 与 Migration 需改造）        |
| `androidx.datastore`                                       | DataStore 本身支持 KMP（`PreferenceDataStoreFactory.createWithPath`）；`SharedPreferencesMigration` 下沉到 `androidMain` | A/D/I | **低**（比换 multiplatform-settings 更省事）  |
| `coil3`                                                    | 保留，网络层换 `coil-network-ktor3`                                                                                   | A/D/I | 低                                     |
| `media3` / `mpv-lib`                                       | 保留在 `androidMain`；桌面补 VLCJ/mpv，iOS 补 AVPlayer                                                                  | 分平台   | **高**（核心能力，靠已有 `PlaybackEngine` 接口收敛） |
| `androidx.work`                                            | `expect/actual`：`DownloadWorkController`（接口**已存在**）                                                            | A/D/I | 中                                     |
| `androidx.glance`                                          | 留在 `androidMain`，桌面/iOS 无对应能力                                                                                  | A     | 低                                     |
| `androidx.biometric`                                       | `expect/actual`，桌面/iOS 降级                                                                                      | A     | 低                                     |
| `androidx.documentfile` (SAF)                              | `expect/actual` 存储接口                                                                                           | A     | 低                                     |
| `aboutlibraries` / `sonner` / `m3color` / `avatar-cropper` | 逐个核对 CMP 支持，不支持的下沉到 `androidMain`                                                                              | 待核    | 中                                     |
| `java.time`                                                | `kotlinx-datetime`（项目已引入）                                                                                      | A/D/I | 低                                     |
| `buildConfig`                                              | 沿用 AGP；跨端常量走 `commonMain` 常量或 BuildKonfig                                                                      | A/D/I | 低                                     |

> A=Android，D=Desktop(JVM)，I=iOS

***

## 6. 分阶段实施计划

### P0 — 版本锁定 + 工程骨架 ✅ **已完成**

**交付**：

- `settings.gradle.kts`：`include(":app", ":shared", ":desktopApp")`

- `shared/build.gradle.kts`：`android` 目标（`com.android.kotlin.multiplatform.library`）+ `jvm("desktop")` + `iosArm64` + `iosSimulatorArm64`

- `shared` 四个源集 + `expect/actual fun platformName()` 示范

- `shared/src/commonMain/.../App.kt`：Navigation 3 `NavDisplay` 示例，三端共用

- `shared/src/iosMain/.../MainViewController.kt` + `iosApp/`（Swift 壳 + xcodegen 工程）

- `desktopApp`：`compose.desktop.application`，`mainClass` 指向 `App()`

- `libs.versions.toml`：新增 CMP / Ktor / ksoup / Room / JetBrains AndroidX 条目

**验收**：三端空壳可编译（见第 8 节验证记录）。

#### ⚠️ 执行中实测踩坑（AGP 9 / CMP 1.12 新变化，后续阶段必读）

1. **AGP 9.0 起** **`com.android.library`** **与** **`org.jetbrains.kotlin.multiplatform`** **插件互斥**，会直接报错。
   必须改用 `com.android.kotlin.multiplatform.library`，且配置写在 `kotlin { android { ... } }` 块里。
   注意该插件**不支持 buildConfig / 变体 / externalNativeBuild**——这些能力留在 `:app`，跨端常量用 BuildKonfig。
2. **CMP 1.12 起废弃** **`compose.runtime`** **这类插件简写**，需在版本目录显式声明 `org.jetbrains.compose.*` 坐标。
3. **material3 特殊**：JetBrains 移植版最新只有 `1.9.0`（CMP 1.12.0 插件内置即 1.9.0），不要写成 1.12.0，否则依赖解析直接失败。
4. **自定义目标名不生成类型化访问器**：`jvm("desktop")` 对应的 `desktopMain` 需用 `val desktopMain by getting {}` 访问。
5. **插件版本需在根工程** **`apply false`** **声明一次**，否则子模块请求 `org.jetbrains.kotlin.jvm` 会报"已在 classpath 但版本未知"。
6. **Coil 3.4.0 的 skiko 与 CMP 1.12 冲突**：`coil-core-jvm:3.4.0 → skiko 0.9.22.2`，CMP 1.12 要 0.150.1。
   `:shared` 用 Coil 3.6.1 规避，`:app` 对齐留到 P6。

***

### P1-pre — 构建标准化：build-logic convention plugin ✅ **已完成（2026-09-01）**

借鉴 animeko 的 `ani.kmp-library`（详见 `animeko架构分析与借鉴.md` §二.1/§二.2），把 KMP 构建配置收敛到约定插件：

**交付**：

- `build-logic/`（composite build，与既有 `buildSrc` 共存）：

  - `han1me-kmp-library` 约定插件：统一应用 KGP + `com.android.kotlin.multiplatform.library`，
    声明 `android` / `jvm("desktop")` / `ios*` 全部 target，统一 compileSdk/minSdk/namespace/jvmTarget

  - **自定义 source set 层级**：`group("jvm") { withJvm(); group("android") }`——
    desktop 与 Android 共享的纯 JVM 代码今后放 `jvmMain`，大幅减少 expect/actual 数量

- `gradle.properties` 新增 `han1me.*` 配置项（compileSdk/minSdk/namespace.base/ios.enabled）

- `libs.versions.toml` 新增 `android-gradle-plugin` / `kotlin-gradle-plugin`（仅供 build-logic 编译）

- `shared/build.gradle.kts` 瘦身：只保留 Compose 插件、iOS framework 导出、源集依赖

- `settings.gradle.kts`：`pluginManagement { includeBuild("build-logic") }`

**收益**：后续 P3\~P7 拆出的每个新 KMP 模块，`build.gradle.kts` 只需 `id("han1me-kmp-library")` 一行。

**验收**：见第 8 节验证记录（`:shared` 全 target + `:desktopApp` + `:app` 回归）。

**踩坑记录**：

1. 版本目录里 `module = ...` 条目只能放 `[libraries]` 段；误放 `[plugins]` 段会报
   `expected to find any of 'id' or 'version' but found unexpected key 'module'`。
2. precompiled script plugin 中，脚本顶层 `val` 是生成类的**成员属性**；
   在 `android { }` 这类 lambda 里会被接收者同名属性遮蔽——`compileSdk = compileSdk`
   会变成自我赋值导致 "compileSdk version is not set"。变量命名必须避开 DSL 属性名。
3. `applyDefaultHierarchyTemplate { }` 为实验性 API，需 `@file:OptIn(ExperimentalKotlinGradlePluginApi::class)`。

***

### P1 — 纯 Kotlin 层下沉 ✅ **已完成（2026-09-02）**

**实况（与计划假设的偏差）**：原计划以为 logic/model 整目录零耦合可直接搬，实测 **27/37 个文件是纯 Kotlin、10 个文件存在平台耦合**，需分批处理。

**本次完成（27 文件，`shared/src/commonMain/`，包名不变）**：

- `logic/model` 16 个：AppLanguage / AppSettings / GetchuPreview / HanimeInfo / HanimePreview / HomePage /
  ListsExport / MultiItemEntity / MyListItems / OnlineWatchHistorySort / ParamEnum / Playlists /
  SettingsStore / UserAccount / VideoComments / VideoItemType

- `logic/state` 4 个：PageLoadingState / PageState / VideoLoadingState / WebsiteState

- `logic/exception` 全部 6 个

- `util/CommentUtils.kt`

- 2 处平台写法改写：`System.currentTimeMillis()` → `kotlin.time.Clock.System.now().toEpochMilliseconds()`
  （**注意**：Kotlin 2.4 已把 Clock 移入 stdlib `kotlin.time`；`kotlinx.datetime.Clock` 0.8.0 无 `System`）

- `:shared` 补 `kotlinx-coroutines-core` 依赖；`:app` 增加 `implementation(project(":shared"))`

**需后续阶段处理（仍留在 :app，共 7 文件 + 3 util）**：

- `Announcement`（androidx.annotation.Keep + Compose 字符串）、`DownloadState`（@IntDef）

- `MySubscriptions` / `ReportReason` / `SearchOption`（Parcelable + 依赖外部 Android utils 库 `LanguageHelper` + R 资源）

- `DownloadGroupModel`（依赖 Room 实体）、`HanimeVideo`（依赖 `ResolutionLinkMap` + utils.mapToArray）

- `util/Cookies`（OkHttp + SettingsRepository）、`util/TagLocalizer`、`util/DisplayTextLocalizer`（外部 utils 库 / R / java.util.Locale）
  → 跟随 P2（Room/DataStore）、P3（Ktor 换 OkHttp）、P6 处理；外部库 `io.github.daisukikaffuchino.utils` 的 KMP 化或内联是前置条件

**验收**：`:shared` commonMain/Desktop/iOS + `:desktopApp` + `:app:compileDebugKotlin` 全部通过。

**跨模块重构必踩的坑**：类下沉到 `:shared` 后，`:app` 侧对该类型**跨模块属性的 smart cast 全部失效**
（"Smart cast is impossible, because X is a public API property declared in different module"）。
修法统一：先绑定局部 `val` 再判空/转型（如 `when (val s = x.state)`、`val t = x.title`）。
本次共修 6 处：AccountScreen ×2、HomePageViewModel ×1、MyPlaylistComponents ×1、PreviewInfoCard ×2。

***

### P2a — Room KMP 基建 + MiscellanyDatabase spike ✅ **已完成（2026-09-02）**

**交付**：

- `:shared` 接入 **Room 官方 Gradle 插件**（`androidx.room`，version 与 room-runtime 同为 2.8.4）+ `com.google.devtools.ksp`，
  各 target 注入 `room-compiler`：`add("kspAndroid"/"kspDesktop"/"kspIosArm64"/"kspIosSimulatorArm64")`；
  `room { schemaDirectory(...) }`（官方插件强制要求，即使 exportSchema=false）

- `MiscellanyDatabase`（含 `HKeyframeEntity` + `HKeyframeDao`）整体下沉 commonMain，改为
  `@Database + @ConstructedBy(XxxConstructor::class) + expect object XxxConstructor : RoomDatabaseConstructor<Xxx>`——
  **actual 由 Room 编译器自动生成**（`kspKotlinDesktop` 产物可见），无需手写三份 actual

- 建库入口做成 `expect fun createMiscellanyDatabase(filePath: String)`，actual 分平台：

  - androidMain：`Room.databaseBuilder(context=..., name=filePath)`（**Android 的 Room 没有纯路径 overload，必须 Context**，
    走系统 sqlite、不 setDriver）；Context 经 `Han1meDatabaseContext` holder 由 :app 注入

  - desktopMain / iosMain：`name=filePath` + factory + `setDriver(BundledSQLiteDriver())`

- `:app` 侧：`val MiscellanyDatabase.Companion.instance` 扩展属性注入路径 + 懒加载，
  DatabaseRepo / BackupManager 等调用点**零改动**

- `room-runtime-kmp` / `sqlite-bundled` 由 implementation 升为 **api**（:app 消费 RoomDatabase 等类型）

**关键排坑**：

1. `Room.databaseBuilder` 只存在于 room-runtime 的**平台源集**（common 元数据里没有）——
   `compileCommonMainKotlinMetadata` 会报 Unresolved 'databaseBuilder'，**必须经 expect/actual 下沉到平台层**
2. **Android 与 desktop/iOS 的 builder 姿势不同**（Context vs 路径+driver），
   不能像最初设想那样放一个 jvmMain actual 同时覆盖 android+desktop，需拆 androidMain / desktopMain
3. 重写 @Database 类时若删掉 `companion object`，:app 里挂 Companion 的扩展属性会报 `Unresolved reference 'Companion'`
4. Room KMP 生成 expect/actual 类会刷 Beta 警告——约定插件已统一加 `-Xexpect-actual-classes`

**验收**：`:shared` commonMain/Desktop/iOS/android + `:app` + `:desktopApp` 全部编译通过（HKeyframe 读写在 android 侧行为不变，
数据库文件路径仍为 `getDatabasePath("miscellany.db")`，由框架 sqlite 打开，与旧库文件格式兼容，无需迁移）。

**余下**（P2b，未做）：其余 4 个库、DataStore KMP、`utils.applicationContext` 全局替换——已按 2026-09-02 复审重排如下。

***

### P2b — 数据层收尾：DataStore + LogUtil + Room 4 库 ✅ **步骤 2/3 已完成（2026-09-02）**

> 重排依据（§1.4-2）：网络拦截器链（`HDns`/`HCookieJar`/`HProxySelector`）与 `CloudflareInterceptor` 都压在
> `SettingsRepository` / `LogUtil` / `applicationContext` 上，因此 P2b 必须先于 P3，且**内部顺序不可调换**。

**步骤 1：全局 Context 收口（横切耦合源）** ⬜ **未做，顺延**

- `io.github.daisukikaffuchino.utils/AndroidUtil.kt` 的 `lateinit var applicationContext` 是最大单点耦合，
  参照 P2a 已验证可行的 `Han1meDatabaseContext` holder 模式替换：commonMain 定义注入接口，各平台入口初始化。

- `CloudflareInterceptor(applicationContext)` 等构造处改为依赖注入。

- 实测结论：步骤 2/3 并**不**依赖步骤 1——`applicationContext` 的使用被完全收敛在 `:app` 侧的
  `XxxDatabaseInstance.kt` 扩展与 `HanimeApplication` 入口，commonMain 零引用，故先做了 2/3。

**步骤 2：DataStore KMP + LogUtil KMP 化（P3 硬前置）** ✅ **已完成**

- `LogUtil` 下沉 commonMain（包名不变），`internal expect fun platformLog` 三端 actual
  （Android `Log.println` / desktop `println` / iOS `NSLog`）；`BuildConfig.DEBUG` 依赖改为
  `HanimeApplication.attachBaseContext` 中 `LogUtil.enabled = BuildConfig.DEBUG` 注入。

- `DataStoreManager` 下沉 commonMain，改用 `PreferenceDataStoreFactory.createWithPath(produceFile = { okio.Path })`，
  依赖 `datastore-preferences-core`（多平台 core）；路径由 `expect fun dataStoreFilePath(fileName)` 提供
  （Android 复用 `Han1meDatabaseContext` → `filesDir/datastore`，desktop `~/.han1meviewer/datastore`，
  iOS `NSDocumentDirectory`）。

- `SharedPreferencesMigration`（两条：`<pkg>_preferences` + `<pkg>`）、`runBlockingIo`、`withInitLock`
  统一由 `internal expect`（`DataStorePlatform.kt`）分流；Android 侧另有 `DataStoreManager.initialize(context)`
  扩展工厂承接 Context。

- `SettingsRepository` + `HorizontalCardCountConfig` / `SearchGridColumnsConfig` 下沉 commonMain；
  `java.net.URI` 换为纯 Kotlin `parseRootUrl`。

**步骤 3：Room 4 库按难度递增下沉（套用 P2a 模板：`@ConstructedBy`** **+ Companion 扩展属性注入）** ✅ **已完成**

| 顺序 | 库                       | Entity/DAO | 手写 Migration | 结果                                                                                       |
| -- | ----------------------- | ---------- | ------------ | ---------------------------------------------------------------------------------------- |
| 1  | `LocalListDatabase`     | 2/1        | 0            | ✅ 铺模板                                                                                    |
| 2  | `CheckInRecordDatabase` | 1/1        | 4 组（版本 5）    | ✅ `Cursor` 遍历改 `prepare/step/getText`，保留 `fallbackToDestructiveMigration(true)`          |
| 3  | `HistoryDatabase`       | 3/3        | 3 组          | ✅ `contentValuesOf + db.update(CONFLICT_REPLACE)` → `UPDATE OR REPLACE ... ?` 带参         |
| 4  | `DownloadDatabase`      | 4/3        | 4 组          | ✅ 全 DDL/DML，`execSQL` 直译；`DownloadState`/`DownloadGroupModel`/`VideoWithCategories` 跟随下沉 |

**实际踩坑（补充 P2a 记录）**：

- Room KMP 的 `Migration.migrate` 签名是 `migrate(connection: SQLiteConnection)`，不是 `SupportSQLiteDatabase`；
  `execSQL` 是 `androidx.sqlite` 的**顶层扩展函数**，带参写入要走 `connection.prepare(sql)` + `bindText/bindInt/step/reset/clearBindings`。

- `HistoryDatabase` 与 `DownloadDatabase` 的迁移对象同名（`Migration1To2`…），**必须保持嵌套在各自类内**，
  提到顶层会撞名。

- `androidx.annotation` 的 `@IntDef` / `@IntRange` 在 commonMain 可用（1.9.1 的 `commonMain` klib 已含），
  `DownloadState` / `HanimeDownloadEntity` 无需改注解。

- `HFileManager`（Context/Environment/java.io.File）仍在 `:app`，`HanimeDownloadEntity.suffix` 里的
  `HFileManager.DEF_VIDEO_TYPE` 暂内联为 `"mp4"`（同值），待 `HFileManager` 下沉后回填。

**验收**（2026-09-02，clean 后全量）：
`:shared:compileCommonMainKotlinMetadata` / `:shared:compileAndroidMain` / `:shared:compileKotlinDesktop` /
`:shared:compileKotlinIosSimulatorArm64` / `:desktopApp:compileKotlin` / `:app:compileDebugKotlin` 全绿。
`:app` 侧仅新增 4 个 `XxxDatabaseInstance.kt` 扩展 + 补 `import ...logic.instance`，业务逻辑零改动。

***

### P3 — 网络层：Ktor + 复用 OkHttp 链路（含 ViewModel 下沉）

**前置**：P2b 步骤 1/2（拦截器链依赖 `SettingsRepository` + `LogUtil`，未收口前无法下沉）。

**动作**：

1. Android / Desktop：`HttpClient(OkHttp) { engine { preconfigured = ... } }`——`ServiceCreator` 共管 3 个 client
   （`hClient` / `downloadClient` / `getchuClient`），全部注入 Ktor，现有 DNS/DoH/Cookie/Proxy/拦截器**链路保留**。
2. iOS：`HttpClient(Darwin)`，自定义 DNS 与 DoH 降级为系统解析（设置页对 iOS 隐藏或标注不可用）。
3. 5 个 Retrofit Service 接口（HanimeBase / HanimeMyList / HanimeComment / HanimeSubscription / Getchu）改为 Ktor 调用封装；
   `Response<ResponseBody>` → `HttpResponse.bodyAsText()`。
4. `NetworkRepo` 的 `websiteIOFlow` / `pageIOFlow` / `videoIOFlow` 包装保持不变，ViewModel 无感。
5. **范围修正（2026-09-02 复审）**：原计划"P3 完成后随网络层一起下沉 NetworkRepo + ViewModel"不可行——
   实测 `NetworkRepo` 依赖 `R` 资源、`applicationContext`、`java.io.File` 与 `Parser`，全部压在 P4 交付物上。
   修正为：**P3 只做网络基础设施**（常量下沉、OkHttp 拦截器链进 `jvmMain`、Ktor 三引擎、5 个 Service 重写），
   `NetworkRepo` 留 `:app` 仅适配调用点；**NetworkRepo + ViewModel 下沉推迟到 P4 完成后**（作为 P4b 或并入 P4 收尾）。
   本阶段顺带完成 P2b 步骤 1 全局 Context 收口（`CloudflareInterceptor` 构造依赖在此必然撞上）。

**风险**：中。主要工作在 Service 调用层与 multipart 上传（`updateUserAccountAvatar`）。

**验收**：三端能拉首页、搜索、详情；Android 端 Cloudflare / 登录态行为与现状一致。

***

### P3a — 垂直切片里程碑：首页跑通桌面端（新增，去风险）

> 在 P4/P5 大迁移开工前，用最小代价让**真实首页**在 `desktopApp` 跑起来（真网络 + 真解析 + Coil 图片 + Navigation3）。
> 目的：把 UI/资源/窗口适配的风险提前到只涉及 1 个页面的时机暴露，避免"底层全绿、UI 一沉就翻车"。

**动作**：

- 仅下沉 `HomePageScreen` + 其直接组件（不含播放器相关入口）。

- 桌面窗口尺寸 / 鼠标滚轮 / 无 BackHandler 行为差异就地记录，反哺 P6。

- 涉及少量字符串的先用硬编码占位，资源系统留给 P4（勿在切片里顺手做）。

**验收**：desktopApp 启动即见首页列表、图片可加载、可点击进二级页（非播放器）。

***

### P4 — 资源系统 + 解析层：CMP Res → ksoup（重排版）

> 重排依据（§1.4-3）：原计划"仅 2 个文件换 ksoup"低估了耦合——`Parser.kt` 除 jsoup 外还依赖 `R` 资源
> （`applicationContext.getString`）与 `TagLocalizer` / `DisplayTextLocalizer`（外部 utils 库 + R + `java.util.Locale`）。
> **字符串资源 KMP 化是 ksoup 迁移的前置**，原计划把它排在 P6 太晚。

**动作**：

1. `values*/strings.xml`（含 `values-zh-rCN` / `values-zh-rTW` / `check_in_strings`）迁 CMP Resources
   （`composeRes` 目录 + `Res.string.*`）；`values-night` 之外的 drawable 按需迁 vector。
2. 外部库 `io.github.daisukikaffuchino.utils` 的 `LanguageHelper` 等内联进 common（该库无 KMP 版本）；
   `TagLocalizer` / `DisplayTextLocalizer` / `Announcement` / `MySubscriptions` / `ReportReason` / `SearchOption` 等
   R/Parcelable 耦合 model 一并处理（Parcelable → kotlinx.serialization 或平台 expect/actual）。
3. jsoup → ksoup：`Parser.kt`（1184 行）与 `GetchuParser.kt`，48 处选择器逐一验证。

**风险**：中。ksoup 对 jsoup 选择器是子集实现；资源迁移量大但机械（strings.xml 可脚本转换）。

**验收**：建立 fixtures 快照测试——抓取真实页面 HTML 存为测试资源，对比迁移前后解析结果逐字段一致；
三端语言切换（简中/繁中/英文）生效。

***

### P5 — 播放器：PlaybackEngine 下沉（拆两步）

**动作**：

1. **第一步（接口抽象）**：`PlaybackEngine` 接口移到 `commonMain`——`attachSurface` / `detachSurface` 的
   `android.view.Surface` 参数改为平台抽象 `expect class PlayerSurface`；`PlaybackEngineFactory.create` 改 expect/actual。
2. `VideoPlayerUi.kt`（1936 行）**拆层**：通用控制层（手势 / 倍速 / 进度 / 弹幕 / 设置面板等大部分代码）下沉 commonMain；
   平台渲染层（`SurfaceView` / `AndroidView` / `MediaRouteButton`，§1.4-4 量化约 4 处集中耦合）留 expect/actual。
3. **第二步（补引擎）**：Android 三个实现（Exo / MPV / Cast）留 `androidMain`；桌面端补 mpv JVM 绑定
   （与 Android MPVLib 行为一致，优先于 VLCJ）；iOS 端补 AVPlayer 实现。
4. `VideoRouteHostScreen.kt`（1058 行）随控制层一起下沉。

**风险**：**高**。这是项目核心能力，三端引擎能力不对等（Cast 仅 Android、MPV 仅 Android/桌面）。

**缓解**：先定义最小可用接口（播放/暂停/seek/倍速/进度/尺寸），高级特性（H 帧、Anime4K 着色器、Cast）按平台能力开关降级；
P3a 的垂直切片经验（窗口适配 / 输入差异）在此复用。

***

### P6 — UI 层大迁移 + 导航换坐标（吸收原 P7）

> 重排依据（§1.4-4/5）：Android API 耦合集中在 37 文件/109 处，且头部集中（Widget 16、设置路由 15+5、播放器 4）；
> 约 120 个 UI 文件近乎零耦合。导航（原 P7）风险低，直接并入本阶段。

**动作**：

1. `ui/screen`、`ui/component`、`ui/theme`、`ui/navigation` 移到 `commonMain`，依赖坐标换 CMP DSL，源码基本不改（见纠正 1）。
2. 导航换坐标：`androidx.navigation3:navigation3-ui` → `org.jetbrains.androidx.navigation3:navigation3-ui`；
   `TopLevelBackStack` 基于 `SnapshotStateList` 无反射依赖，直接可用；跨进程恢复如需再做
   `rememberNavBackStack(SavedStateConfiguration { serializersModule = ... })` 显式序列化注册。
3. Android API 耦合点逐项替换：

   - `BackHandler` → `org.jetbrains.androidx.navigationevent` 或 CMP 等价物

   - `rememberLauncherForActivityResult` → expect/actual

   - `parseAsHtml` / `toUri` → 纯 Kotlin 实现

   - `StringRes` / `R` → 已在 P4 建好的 CMP `Res`
4. 三方库处置（仅 3 文件）：

| 库                        | 使用点                      | 处置                                                      |
| ------------------------ | ------------------------ | ------------------------------------------------------- |
| `kyant m3color`          | Theme.kt 22 处            | 单文件色彩科学库，直接内联 common（无平台依赖）                             |
| `aboutlibraries`         | OpenSourceLicensesScreen | 核对 14.x multiplatform compose artifact；不行则手写 licenses 页 |
| `compose-avatar-cropper` | AvatarCropScreen         | Android 专属，expect/actual；桌面/iOS 降级为平台裁剪能力               |

1. `:app` 的 Coil 3.4.0 → 3.6.1 对齐（消除 skiko 冲突，见 P0 踩坑 6）。
2. `CheckInWidgetProvider`（Glance）整体留 `androidMain` 不迁。

**风险**：中。量大但机械；按「theme → component → 无耦合 screen → 高耦合 screen」顺序分批，每批三端编译回归。

**验收**：三端渲染同一首页与设置页；Android 端功能与迁移前一致。

***

### P7 — 平台专属能力收口（原 P8）

| 能力            | Android                | Desktop   | iOS           |
| ------------- | ---------------------- | --------- | ------------- |
| 下载任务          | WorkManager            | 协程 + 自管队列 | 后台 URLSession |
| 桌面小组件         | Glance                 | 无         | 无             |
| 应用锁           | Biometric              | 密码对话框     | FaceID        |
| 下载目录          | SAF `DocumentFile`     | 系统文件对话框   | 沙盒目录          |
| 播放内核          | ExoPlayer / MPV / Cast | mpv       | AVPlayer      |
| 自定义 DNS / DoH | ✅                      | ✅         | ❌ 降级          |

**风险**：中。

***

### P8 — 三端联调与发布（原 P9）

- `androidApp`（或直接沿用 `:app`）跑通实机（Redmi / Android 14 / API 34 已就绪）。

- `desktopApp`：`packageReleaseDistributionForCurrentOS` 出 Dmg / Msi / Deb。

- `iosApp`：Xcode 工程链接 `:shared` framework，处理 Safe Area、全屏、签名。

- `:app` 移除（或退化为纯 Android 壳），`:shared` 成为唯一业务/UI 载体。

- CI：**iOS 只能在 macOS runner 上构建**，Windows/Linux runner 只能编 Android + Desktop。

***

## 7. 风险总览

| 风险                              | 等级    | 缓解                                                  |
| ------------------------------- | ----- | --------------------------------------------------- |
| 播放器三端能力不对等                      | **高** | 已有 `PlaybackEngine` 接口收敛；先做最小接口 + UI 拆层，高级特性按平台开关降级 |
| 手写 Migration 的 KMP API 替换       | 中     | P2a 基建已趟平；逐库递增下沉，每库做升级路径验证                          |
| ksoup 与 jsoup 选择器差异             | 中     | fixtures 快照测试，2 个文件影响面可控                            |
| 资源系统迁移（R → CMP Res）波及面          | 中     | 提前到 P4 与解析层一起做，避免 P6 才暴露；strings.xml 脚本化转换          |
| DataStore 迁移（SharedPreferences） | 低     | 下沉 `androidMain`，其他平台无历史包袱                          |
| iOS 端无自定义 DNS/DoH               | 中     | 设置页按平台隐藏；iOS 走系统解析                                  |
| `aboutlibraries` 等三方库 CMP 支持    | 中     | 已量化仅 3 文件（§1.4-5），处置表见 P6                           |
| 构建时间（三端全量）                      | 中     | 开启 Gradle 配置缓存与构建缓存；CI 分平台并行                        |

***

## 8. 当前进度与验证记录

- [x] P0 版本锁定与工程骨架 ✅

- [x] P1-pre 构建标准化（build-logic convention plugin）✅

- [x] P1 纯 Kotlin 层下沉（27 文件）✅

- [x] P2a Room KMP 基建 + MiscellanyDatabase spike ✅

- [x] P2b 步骤 2 DataStore + LogUtil + SettingsRepository KMP 化 ✅（2026-09-02）

- [x] P2b 步骤 3 Room 4 库下沉（LocalList / CheckInRecord / History / Download）✅（2026-09-02，主模型复审通过）

- [ ] **P2b 步骤 1 全局 Context 收口（`utils.applicationContext`）← 顺延至 P3 开工一并处理**

**P2b 复审记录（主模型，2026-09-02）**：逐文件对照备份审阅 + 复跑六端验收全绿。通过项：
DataStore 落盘路径与旧 `preferencesDataStoreFile` 完全同址（`filesDir/datastore/settings.preferences_pb`）、
两条 SharedPreferencesMigration 顺序保留、`initialize(context)` 以 androidMain 扩展函数保持签名兼容、
LogUtil API 面完整且 DEBUG 开关由 Application 入口恢复、`java.net.URI` → 纯 Kotlin `parseRootUrl` 语义等价、
4 库 Migration SQL 与备份逐条等价（`contentValuesOf`+`CONFLICT_REPLACE` → `UPDATE OR REPLACE`），
数据库文件名逐一核对（`check_in_records` 无后缀这一点易翻车，已正确沿用）。
两个待办：① `HanimeDownloadEntity.suffix` 实际是行为变化（常量 `"mp4"` → 按扩展名解析，无扩展名回退 mp4），
非报告所述"内联同值常量"，多数场景等价、待 HFileManager 下沉后复核；② desktop/iOS 的
`DataStoreManager.initialize()` 入口未接线，留给 P3a 垂直切片。

- [x] P3 网络层（网络基础设施部分）✅（2026-09-02，主模型复审通过）
  - 常量下沉 / CloudflareVerifier 回调注入 / 拦截器链 jvmMain / 5 Service Ktor 重写 / NetworkRepo 适配 / retrofit 零残留

  - NetworkRepo + ViewModel 下沉按范围修正推迟到 P4 后（见 P3 阶段注记）

  - P2b 步骤 1 全局 Context 收口：网络链路部分已完成（CloudflareInterceptor/cache），其余（NetworkRepo/Parser/HImageMeower 的 applicationContext）顺延 P4

**P3 复审记录（主模型，2026-09-02）**：逐文件对照 /tmp/han1me\_p3\_backup 审阅 + 亲跑六端验收全绿。通过项：
ServiceCreator 拦截器顺序与缓存配置逐行等价（Cloudflare 用 createCloudflareInterceptor() 可空注入保持位置）、
`rebuildNetwork` 语义精确对齐（含原版不重建 subscriptionService 的怪癖）、5 个 Service 方法签名/表单键/默认值
与备份 @Field 注解逐一比对（GetchuService 27 字段全对，报告"28"为笔误）、multipart 头像上传 4 个 part 的
Content-Type/Content-Disposition 显式声明正确、CloudflareVerifier 在 Application.onCreate 注册（先于任何网络请求，无竞态）、
runBlocking 仅出现在 androidMain 拦截器与 :app 异常路径（合法）。遗留改进项：`throwRequestException` 的
runBlocking 可改为 suspend 消除（调用点全在协程内，非阻塞正确性无影响）；**冒烟测试未执行（无设备）——
恢复设备后必须补：首页列表（hClient 全链路）、中文搜索 query 编码、头像 multipart wire 三项**。

- [x] P3a 垂直切片：桌面端网络验证切片 ✅（2026-09-02，复审通过；真站冒烟经用户代理修复后通过；
  发现 ksoup 坐标坑——P0 选的 mohamedrejeb 是零引用死依赖，P4 已换 fleeksoft:ksoup:0.2.6）

- [x] P4 资源系统（最小化）+ 解析层 ksoup 化 ✅（2026-09-02，复审通过；187 条选择器逐字 diff 一致）

- [x] P4b Repo 层 + ViewModel 下沉 ✅（2026-09-02，主模型复审通过）
  - HanimeDatabases 统一入口（三端路径与原 Instance 逐库一致）+ ioDispatcher/decodeEucJp/
    sslHandshakeException expect + Getchu 编码三处逐处一致（preview=EUC-JP/detail=UTF-8/ajax=EUC-JP）

  - :app logic 层残留恰为 P6 声明闭包

- [ ] P5 播放器（接口抽象 → 补引擎）

- [ ] P6 UI 层大迁移（拆三轮）
  - [x] P6a 第一检查点 ✅（2026-09-03，主模型复审通过）
    - A 全量 strings（874/852/852 对账精确，22 条 translatable 仅留 values，26 条裸参数补 %N$ 编号）；
      B assets→composeResources/files（h\_keyframes 走构建清单 index.txt×27，注意新增文件需手动更新清单）；
      C LanguageHelper KMP 化（PreferredLanguage 轻量解析，Android 保持 AppCompatDelegate 语义）；
      E-Announcement 下沉

    - 小瑕疵（P6b/c 顺手收）：README.md 盲拷进 files/h\_keyframes/ 应删

  - [x] P6a-F + P6b UI 骨架批次 ✅（2026-09-03，事故后由主模型亲手恢复完成）
    - F：VideoCacheStore/DownloadWorkController expect 化（provider 注册制，HanimeApplication.onCreate
      注册，Android 实现依赖 :app 的 HCacheManager/WorkManager）；G：theme 5/6 下沉（Theme.kt 留 :app
      ——Kyant0 m3color 无 KMP 坐标；material3 CMP 升 1.12.0-alpha03）；H：component 12/16 下沉 +
      HapticFeedback expect（4 文件回摆记债务：SettingsSegments/LoadMoreFooter/PageContent/UsageNoticeDialog）；
      I：SonnerToast 下沉 commonMain（String-only）+ :app toastText 助手 79 处适配

    - **P6b 事故与恢复（2026-09-03）**：flash 的 toast 适配脚本两轮失误造成 121 文件括号破坏（209 编译
      错误，工作树无 git 无备份）。主模型恢复：用户 GitHub fork（daisukiKaffuChino/Han1meViewer main，
      2026-08-20）作基线 → 78 纯括号文件基线覆盖 → 43 实质差异文件"去括号等价回退"脚本（保留 210 行
      合法适配/回退 179 行括号垃圾）→ 13 处零星手工修复 → clean 六端全绿。shared 零影响。

    - P6b 收尾顺延：desktop 骨架屏切换（Main.kt 仍指向验证屏）+ 冒烟

  - [ ] P6c Search/视频/预览批次：SearchOption SparseArray+R-int 收口（SearchViewModel/SearchScreen/
    AdvancedSearchSheet 联动）→ TagLocalizer/DisplayTextLocalizer → Search/Video/Preview VM + 对应 UI

  - 边界裁定（2026-09-03）：SearchOption/SonnerToast 的 R-int 依赖深嵌 UI，"模型先行下沉"会造成
    27+ 文件两轮返工，裁定 VM 跟随对应 UI 批次下沉

- [ ] P7 平台专属能力收口

- [ ] P8 三端发布

**编译验证记录**（2026-09-01，本机 macOS + Xcode + JDK 21 + Android SDK 37）：

| 验证项                               | 任务                                        | 结果               |
| --------------------------------- | ----------------------------------------- | ---------------- |
| 共享模块 commonMain（含 Navigation3 示例） | `:shared:compileCommonMainKotlinMetadata` | ✅                |
| 共享模块 Android 目标                   | `:shared:compileAndroidMain`              | ✅                |
| 共享模块桌面目标                          | `:shared:compileKotlinDesktop`            | ✅                |
| 桌面应用入口                            | `:desktopApp:compileKotlin`               | ✅                |
| 共享模块 iOS 模拟器目标（K/N）               | `:shared:compileKotlinIosSimulatorArm64`  | ✅                |
| 原 Android 应用回归                    | `:app:compileDebugKotlin`                 | ✅（仅原有 Cast 弃用警告） |

**环境**：macOS + Xcode ✅（可编 iOS）、JDK 21（`~/devtools/jdk-21`）✅、Android SDK 37 ✅（构建时自动补装了 NDK 28.2）。
iOS 框架编译由 Gradle 直接验证，无需 Xcode；跑 iOS 壳工程见 `iosApp/README.md`。

**本机构建命令**（`ANDROID_HOME` 需显式导出，shell 非交互环境不读 `~/.zshrc`）：

```bash
export JAVA_HOME=~/devtools/jdk-21
export ANDROID_HOME=~/Library/Android/sdk
./gradlew :shared:compileCommonMainKotlinMetadata :shared:compileAndroidMain \
          :shared:compileKotlinDesktop :desktopApp:compileKotlin \
          :shared:compileKotlinIosSimulatorArm64 :app:compileDebugKotlin
```

