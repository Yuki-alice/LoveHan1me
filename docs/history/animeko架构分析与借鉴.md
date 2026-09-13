# animeko 架构分析与借鉴笔记

> 参考项目：[open-ani/animeko](https://github.com/open-ani/animeko)（本地 `reference/animeko`，blobless 克隆）
> 定位：社区 Kotlin Multiplatform 流媒体 App 的标杆工程（Android / Desktop / iOS 三端，Compose Multiplatform）
> 目的：为 Han1meViewer 的 KMP 重构（见 `KMP_MIGRATION_PLAN.md` P0~P9）提供架构层面参照

---

## 一、全局结构：45+ 模块的分层拓扑

```
┌─ 薄启动器（几乎无逻辑）─────────────────────────┐
│  app/android    app/desktop    app/ios           │
├─ app/shared/*（全平台共享的 UI + 业务）──────────┤
│  application(组装根)  app-platform  app-data     │
│  ui-foundation  ui-adaptive  ui-settings         │
│  ui-subject  ui-episode  ui-exploration ...      │
│  video-player-api → video-player → torrent-source│
├─ 领域层（api 与实现严格分离）────────────────────┤
│  client          —— 远程 API 客户端(openapi 生成) │
│  datasource/api → datasource/{bangumi,mikan,...} │
│  danmaku/api   → danmaku/{ui,dandanplay,...}     │
│  torrent/api   → torrent/{anitorrent,pikpak}     │
├─ 基础层（极小粒度，20 个模块）───────────────────┤
│  utils/platform(平台收口)  logging  serialization│
│  coroutines  ktor-client  io  http-downloader    │
│  build-config  testing  bbcode  analytics ...    │
└──────────────────────────────────────────────────┘
```

**第一条也是最重要的一条经验：单模块 `shared` 撑不了多久。**
我们 P0 骨架目前是单 `shared` 模块，animeko 的形态告诉我们终局应该长什么样——按「基建 / 领域 / 功能 UI / 启动器」四层拆分，每层都可以独立编译、独立测试。

---

## 二、九个值得直接借鉴的设计

### 1. Convention Plugin 统一构建配置（build-logic）

所有模块不手写 KMP 配置，而是 `id("ani.kmp-library")` 一行套用。核心插件：

| 插件 | 职责 |
|---|---|
| `ani.base` | 通用编译选项、opt-in |
| `ani.kmp-library` | KMP target 全家桶：`jvm("desktop")` + `android` + `ios*`、默认层级模板、统一依赖注入点 |
| `ani.kmp-compose` | 在 kmp-library 之上加 Compose |
| `ani.android-library` / `ani.android-application` | Android 专用 |
| `ani.flatten-source-sets` | 源集目录扁平化（见 §3） |
| `ani.build-config` | 编译期常量生成 |

**对 我们的启示**：P1 之后模块一多，每个 `build.gradle.kts` 手写 `kotlin { android {} jvm("desktop") ios... }` 会复制 20 遍。应在 `:shared` 可编译稳定后，立刻把这套配置提取成 `build-logic` 的 convention plugin（这正是 animeko `ani.kmp-library.gradle.kts` 的翻版，可直接参考其写法——包括它对 AGP `com.android.kotlin.multiplatform.library` 的用法，和我们 P0 踩坑结论一致）。

### 2. 自定义 Source Set Hierarchy：解决「半平台」复用

```kotlin
applyDefaultHierarchyTemplate {
    common {
        group("jvm") { withJvm(); group("android") }   // desktop 与 android 共享的 JVM 代码
        group("skiko") { withJvm(); withNative() }     // 都走 Skiko 渲染的代码
        group("mobile") { group("android"); withIos() }
    }
}
```

这是 animeko 最精妙的一处：`jvm` 中间层让「desktop 和 Android 都能用、但 iOS 不能用」的代码有了归宿，不必在每个 API 上做 `expect/actual` 三份。
**我们的启示**：Han1meViewer 大量逻辑（网络、爬虫、Room、序列化）在 desktop/Android 上是同一套 JVM 实现。迁移时建立同样的 `jvm` 分组，能把 actual 数量砍掉约一半。

### 3. 扁平化源集目录（flatten-source-sets）

```
src/commonMain/kotlin/...   →  commonMain/...
src/androidMain/kotlin/...  →  androidMain/...
```

路径减少 2 层，2309 个 Kotlin 文件的仓库里导航成本显著降低。属于可选的工程洁癖，但对于我们这种「从单平台搬过来、目录会膨胀」的项目，越早定下越好（晚定要批量搬文件）。

### 4. 平台差异收口到唯一模块：`utils/platform`

全仓库**只有这一个模块**允许接触平台 API（`Platform.kt`、`Time.kt`、`Uuid.kt` 等 expect/actual），且它被 convention plugin 自动加进每个模块的 commonMain 依赖。
**我们的启示**：与其让 expect/actual 散落各处，不如定一条铁律——平台 API 只出现在 `:platform` 模块（对应我们 P0 里的 `Platform.kt`，应升级为独立模块并纳入 convention plugin 强制依赖）。

### 5. 数据源插件化：`datasource-api` + ServiceLoader 自动发现

`MediaSource` 接口设计（`datasource/api/.../source/MediaSource.kt`）非常值得抄：

- 接口只做一件事：`suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch>`（分页返回资源）
- 每个站点一个独立模块（bangumi / mikan / dmhy / jellyfin / web-base…）
- 通过 `META-INF/services` + `MediaSourceFactory` 注册，**新增数据源零侵入主工程**（接口 KDoc 里写了完整的新增步骤）
- 职责边界清晰：查询归 `MediaSource`，下载归 `MediaCacheEngine`，解析播放地址归 `VideoSourceResolver`

**我们的启示**：Han1meViewer 的多站点解析（kanimet 及其镜像源）天然同构。迁移时应把「源」抽象成 `MediaSource` 式接口 + 工厂 + 独立模块，而不是现在的一个 parser 包。这也让单源故障不影响其他源，且能像 animeko 一样在设置页做「数据源测试」。

### 6. 共享 UI 按 feature 拆分 + ViewModel 同层

`app/shared/ui-subject` 内部：`SubjectDetailsPage.kt` + `SubjectDetailsViewModel.kt` 都在 **commonMain**，连 `desktopTest` 截图测试也在同一模块。UI 自适应（如 `SubjectDetailsMultiColumnPage`）也在共享层做。
**我们的启示**：KMP 下 ViewModel 不必留在 androidMain——animeko 证明 Compose + ViewModel（JetBrains lifecycle 版本）可以完全 common 化。我们迁移各页面时，目标态是「Page + ViewModel 一起进 commonMain，androidMain 只剩平台胶水」。

### 7. 三端启动器薄如纸

`app/desktop` 全部只有 20 个 kt 文件，内容是：`AniDesktop.kt`（main 入口）、日志配置、启动胶水。业务逻辑为零。
**我们的启示**：验收 KMP 迁移是否到位，就看 `:desktopApp` / `iosApp` 是否只是 `application { Window { App() } }` + 少量平台胶水。凡是发现启动器里写了业务代码，都是分层失败的信号。

### 8. 构建提速专项手段

- **`app-lang` 单独成模块**：文案/资源多且常改，独立编译避免整个 app 重编
- **`test-codegen`**：为每个数据源自动生成单测骨架
- **第三方 fork 内嵌** `app/shared/thirdparty/{paging-compose,image-viewer,reorderable}`：需要打补丁的库直接源码内嵌
- **`includeBuild` 组合构建**：`build-logic`、`mediamp`（播放器抽象）、`anitorrent` 独立仓库但一体构建

### 9. DI 用 Koin（koin-core 跨平台）

`koin-core`（common）+ `koin-android`（平台增强），无编译期处理器、纯 Kotlin DSL，天然 KMP 友好。我们迁移时若需要 DI，Koin 是比 Hilt（Android-only）更顺的选择；Han1meViewer 现有手动单例可先不引入，等模块拆开后用 Koin 收口组装根（对应它的 `app/shared/application` 模块）。

---

## 三、与我们 P0~P9 计划的直接映射

| animeko 实践 | 落到我们的计划 |
|---|---|
| `ani.kmp-library` convention plugin | **P1** 起：`:shared` 配置稳定后提取 `build-logic`，后续模块一行接入 |
| 自定义 hierarchy（jvm/skiko/mobile 分组） | **P1~P2**：commonMain 里 JVM 共享逻辑（网络/序列化）放 `jvm` 分组，减少 actual |
| `utils/platform` 唯一收口 | 把 P0 的 `Platform.kt` 升级为独立 `:platform` 模块，铁律化 |
| `datasource-api` 插件化 + ServiceLoader | **P3~P4**：站源解析重构为 `source-api` + 每源一模块 |
| Page + ViewModel 进 commonMain | **P5~P7**：UI 迁移的目标态，而非简单保留在 androidMain |
| 薄启动器三端 | 作为 **P9 验收标准**之一 |
| Koin 组装根 | 可选：模块拆完后在 `:app-shared` 建 composition root |

## 四、不建议照抄的部分

1. **模块粒度不必一开始就 45+**：animeko 是多人多年项目。我们一人项目，P1 先拆到「platform / data / ui-common / app」4~5 个模块即可，feature 级 UI 模块等页面迁移时按需生长。
2. **`flatten-source-sets` 可缓一缓**：虽然优雅，但会改变 IDEA 默认认知，若团队工具链（如现有 AGP 迁移脚本）依赖标准目录可暂缓。
3. **AGPL 许可**：animeko 是 AGPLv3。**只学架构、读代码做参考，不要直接复制源码**进 Han1meViewer，否则许可会被传染。

## 五、延伸阅读（仓库内对应位置）

- convention plugins 全集：`build-logic/src/main/kotlin/`
- 数据源接口与文档化扩展指南：`datasource/api/src/commonMain/kotlin/source/MediaSource.kt`（KDoc 即文档）
- 平台收口模块：`utils/platform/src/`
- 组装根与模块编排：`settings.gradle.kts` + `app/shared/application/build.gradle.kts`
- 完整文件索引：`reference/animeko/FILE_INDEX.txt`（3619 个文件，按需用 `git show HEAD:<path>` 取内容）
