# 后期攻坚：增量实施方案

> **状态：第 3 阶段已落地（2026-10-07，基线 733/0/0/4，含 surface 2 条）。**
> 完成度：§1（任务 A / A1.5）✅｜§3（任务 C）✅｜§2（任务 B）✅（含守卫④）
> ｜ §3.5（任务 E）✅（接线守卫 + 本地分支测试补齐 E5 缺口）｜ §4（任务 D）✅（见 §4 落地记录）｜ A2 ✅｜ Artist ✅
> 各任务规格见对应小节；接续入口与总纲见 `docs/audit/00-synthesis.md`。

> 唯一活计划（本项目约定：`docs/plan/` 同时只允许一份）。
> 依据：`docs/audit/` 下五份取证报告（架构 / 产品 / 质量 / 构建基线 / 总纲）。
> 撰写：主理人齐活林；B/C 落地细节由架构师高见远补齐，并复核全文（见 §0.1）。

## 0.1 复核修订（架构师高见远，本轮）

**总体结论：本计划的判断成立**——"把 animeko 已验证的 `@Immutable/@Stable` 纪律铺到自研代码，而不是重写"，方向对、取舍对，§1/§4/§5 我认可。需更正/补强如下（改动已直接落进本文件）：

1. **§2 路径错**：`shared/.../core/domain/model/HanimeResolution.kt` **不存在**。`ResolutionLinkMap` 真址是 `shared/src/commonMain/kotlin/lovehan1me/site/hanime1/HanimeResolution.kt:13`（`HanimeVideo.kt:3` 的 import 即为 `lovehan1me.site.hanime1.ResolutionLinkMap`）。
2. **§3 行号错**：`SearchScreen.kt:423` 是下拉刷新的 `doSearch()`，**不是** `page++`；`page++` 真址是 `SearchScreen.kt:441`。
3. **§2 的方法前提有风险（新增，最重要）**：稳定性报告开关**不在 build-logic**（那里只有 `han1me-kmp-library` 一个约定插件，只应用 KMP+AGP，**不碰 Compose**）；Compose 是逐模块用版本目录 alias 应用的，原落点在 `shared/build.gradle.kts` 且**已回退**（全仓 `composeCompiler` grep = 0）。更关键：CMP 1.12 + Kotlin 2.4.10 开 metrics/reports **可能直接 `Internal compiler error`**（文档来源、未复验）。→ **B 的验收不得绑定在编译器报告上**，改用确定性守卫（§2.4）。
4. **§1 口径复核**：**表述正确**——OkHttp 层本就共享（`CdnFetchClient.kt` 的 `CDN_CLIENTS`），重复的是 **ImageLoader 实例**；iOS 侧每次新建 `HttpClient(Darwin)` 更重。**注意**：§1 已由工程师施工到磁盘（`HanimeImageLoader.jvm.kt`/`.ios.kt` 现含 `PlatformLock` 单例 + `resetHanimeImageLoaderForTest()`，比原计划多了 `inspection` 形参，方向一致），文内 §1 的原始行号已过期。
5. **我明确不建议本轮做**（补 §5）：① 不引入 `com.github.skydoves.compose.stability.analyzer` 第三方插件（新依赖未评估）；② 不把 compose 配置收编成 `han1me-compose` 约定插件（要改 6 个模块，结构变更）；③ 不给 `HanimeVideo` 之外的模型（`HanimeInfo`/`Artist`/`Playlist`）批量加 `@Immutable`（涟漪大，另立任务）。

## 0. 施工前提（违反则不施工）

| 前提 | 当前状态 |
|---|---|
| 测试基线可信 | ✅ 已达成：给 live 用例加了环境变量门禁，`HAN1ME_ECHGATE_LIVE` 未设时 4 个用例记 skipped |
| 基线复现 | ✅ 主理人多次独立复跑并读 XML 校验；**当前口径 = 550 tests / 0 fail / 0 err / 4 skipped**（531 → 536 A1 → 544 C → 546 A1.5 → 549 B → 550 B+守卫④），XML timestamp 属当轮 |
| 禁改清单 | `PlaybackUiState` 位置投影、`video` 四层依赖方向、`EchGate` 门面就绪发布路径 |
| 硬约束 | 新建自建 OkHttp 客户端必须 `ServiceCreator.registerConnectionPoolEvictor { … }`（换网复位登记制） |
| 验收铁律 | 每个修复必须带守卫测试，并做**反向验证**（临时关掉规则 → 用例必须转红）；`SKIP` 单独计数 |

---

## 1. 任务 A：图片加载器进程级单例化（本轮施工）

### 问题（已核实）
`shared/src/jvmMain/kotlin/lovehan1me/ui/component/HanimeImageLoader.jvm.kt:20` 与
`shared/src/iosMain/kotlin/lovehan1me/ui/component/HanimeImageLoader.ios.kt:19` 都用
`remember(context, isInspectionMode) { … }` 构造 `ImageLoader`。

`remember` 的作用域是**调用点**，因此 8 个调用点各自持有一个 ImageLoader：

| 调用点 | 文件:行 |
|---|---|
| 通用包装默认参数 | `shared/src/commonMain/kotlin/lovehan1me/ui/component/HanimeImageLoader.kt:36` |
| 列表单元格 | `shared/src/commonMain/kotlin/lovehan1me/ui/component/Utils.kt:73` |
| 首页内容块 | `shared/src/commonMain/kotlin/lovehan1me/feature/home/homepage/HomePageContent.kt:84` |
| 搜索结果网格 | `shared/src/commonMain/kotlin/lovehan1me/feature/search/SearchResultsGrid.kt:160` |
| 图片查看器 | `shared/src/commonMain/kotlin/lovehan1me/feature/home/preview/PreviewImageViewer.kt:72` |
| 预览页 | `shared/src/commonMain/kotlin/lovehan1me/feature/home/PreviewScreen.kt:69` |
| 取色主题 | `shared/src/commonMain/kotlin/lovehan1me/ui/theme/SubjectTheme.kt:84` |

### 影响（要说准，别说错）
- **不是** OkHttp 客户端重复：`createCdnFetchClient` 已用 `CDN_CLIENTS` 缓存基础客户端
  （`shared/src/jvmMain/kotlin/lovehan1me/data/network/CdnFetchClient.kt:35`），这一层没问题。
- **是 ImageLoader 实例重复**：每个实例自带独立的 Coil 内存缓存与磁盘缓存 → 同一张封面
  在不同屏幕/单元格各解码一份、缓存互不命中；快滑时内存抖动、低端机 OOM 风险。
- iOS 侧更重：`HanimeImageLoader.ios.kt:23` 每次调用都新建一个 `HttpClient(Darwin)`。

### 施工内容（3 个文件，**零调用点改动**）

1. `shared/src/jvmMain/kotlin/lovehan1me/ui/component/HanimeImageLoader.jvm.kt`
   - 新增**非 @Composable**的内部取用函数（可被 desktopTest 直接调用）：
     `internal fun hanimeImageLoaderOrNull(context: PlatformContext): ImageLoader?`，语义：返回进程级单例，首次调用时构造。
   - 新增 `internal fun resetHanimeImageLoaderForTest()`（仅供测试复位，生产代码不调用）。
   - 保留现有 `@Composable actual fun rememberHanimeImageLoader()` 签名不变，内部改为委托单例；
     **inspection 分支（`LocalInspectionMode`）必须保持每次新建**，绝不缓存 —— 预览需要各自的 context。
   - 单例必须是线程安全的（`synchronized` 双检或 `lazy(SYNCHRONIZED)`），保持
     `StartupTrace.mark("coil")` 只在**真正首次构造**时打一次。
2. `shared/src/iosMain/kotlin/lovehan1me/ui/component/HanimeImageLoader.ios.kt`
   - 同样处理；`HttpClient(Darwin)` 随单例只建一次。inspection 分支同样不缓存。
3. `shared/src/desktopTest/kotlin/lovehan1me/ui/component/HanimeImageLoaderSingletonTest.kt`（新增守卫测试）
   - 断言：两次连续取用返回**同一实例**（`assertSame`）。
   - 断言：`resetHanimeImageLoaderForTest()` 之后再取，得到**新实例**（证明复位有效、测试不串）。
   - 反例断言：inspection 语义不被缓存（若把该分支提取为可测函数，则断言两次取用**不同实例**）。
   - 并发断言：N 个线程同时首次取用，全部得到同一实例（钉住线程安全）。

### 反向验证（不通过则视为未完成）
把 `hanimeImageLoaderOrNull` 的缓存去掉、改成每次 `ImageLoader.Builder(context)…build()` →
`HanimeImageLoaderSingletonTest` 的"同一实例"用例必须**转红**。把验证输出（转红的那条用例名 + 断言消息）贴回来。

### 本轮**不做**（防扩散，另立任务）
- 不动 `desktopApp/src/main/kotlin/lovehan1me/desktop/Main.kt:118` 的
  `setSingletonImageLoaderFactory`（它注册的 `createHanimeHttpClient()` 与 CDN 出口不同源，
  统一它属独立决策，需先确认两者差异是否有意为之）。
- 不动 Getchu 那套同构的 loader（`GetchuImageLoader.jvm.kt:18` / `.ios.kt:19`），留作 A2。
- 不改任何调用点、不改 `HanimeAsyncImage` 公共签名。

### 验证命令与回滚
- 编译：`./gradlew :shared:compileKotlinDesktop :shared:compileAndroidMain --offline`
- 测试：`./gradlew :shared:desktopTest --rerun-tasks --offline`
- 回滚：本任务只动 2 个 actual + 新增 1 个测试文件，`git checkout -- <两个 actual>` 即回滚。

### 1.5 复核：`Main.kt:118` 的 Coil 单例注册是否应统一出口（只读取证）

**背景命题**（工程师在 A1 后提出）：`desktopApp/.../Main.kt:118` 用
`setSingletonImageLoaderFactory { … KtorNetworkFetcherFactory(httpClient = { createHanimeHttpClient() }) … }`
注册了 Coil 单例（API 出口），而真正取图的 `HanimeAsyncImage` 恒显式传
`imageLoader = rememberHanimeImageLoader()`（CDN 出口）→ 推得“桌面上并存两个 ImageLoader、两套 Coil 缓存、两套出口不同源”。

**裁决：命题里“运行时并存两套缓存”不成立；`Main.kt:118` 当前是死配置，但它同时是一颗“错出口”的地雷。建议 ①。**

#### 证据 1 · 谁会真的用那个单例 → **零**

全仓枚举（已排除 `reference/` 与 `**/build/**`）Coil 取图入口：

- **单例注册点唯一**：`SingletonImageLoader|setSingletonImageLoaderFactory` 在真实源码仅
  `desktopApp/src/main/kotlin/lovehan1me/desktop/Main.kt:55`（import）与 `:118`（注册），无第二处注册。
- **不带显式 `imageLoader` 的取图点 = 零**。所有真实 `AsyncImage(` / `HanimeAsyncImage(` 均显式指定 loader：
  - 包装本身：`shared/src/commonMain/kotlin/lovehan1me/ui/component/HanimeImageLoader.kt:36`
    （默认参 `rememberHanimeImageLoader()`）、内部 `:38-41` 显式透传。
  - 裸 `AsyncImage(`（共 7 处，全部显式）：
    `.../home/preview/getchupreview/GetchuPreviewDetailContent.kt:78`（loader `:80`）、`:223`（loader `:225`）；
    `.../getchupreview/Component.kt:50`（loader `:52`）；
    `.../home/preview/PreviewImageViewer.kt:96`（`:98` `resolvedImageLoader`）、`:117`（`:119`）、`:213`（`:215`）；
    `.../ui/component/Utils.kt:71`（loader `:73` `rememberHanimeImageLoader()`）。
  - `PreviewImageViewer.kt:72-73`：`defaultImageLoader = rememberHanimeImageLoader()`；
    `resolvedImageLoader = imageLoader ?: defaultImageLoader` —— 兜底也是 CDN 出口。
  - 其余 20+ 处走 `HanimeAsyncImage(`（默认自带 CDN loader）；getchu 屏走 `rememberGetchuImageLoader()`。
- **无其它 Coil 入口**：真实源码无 `SubcomposeAsyncImage` / `rememberAsyncImagePainter` / `LocalImageLoader`
  provider（grep 命中全为 `**/build/**` 的 dSYM 产物）。
- `shared/jvmMain`、`shared/desktopMain`、`desktopApp`、`video/*` 内**无**任何无 loader 的取图点。

→ 该单例在运行时不承载任何一次图片请求；「两套 Coil 缓存并存」不成立（实际被使用的只有 CDN 那一个 loader 及其缓存）。
但注册仍在组合期执行，构成潜在兜底。

#### 证据 2 · 图片 host → 出口对应关系

| host | 来源 file:line | 需要的出口 | cookie / CF？ |
|---|---|---|---|
| `vdownload.hembed.com/image/…` | `shared/src/commonMain/.../feature/preview/ComposePreviewDataSource.kt:59`（预览假数据） | **CDN 出口** `createCdnFetchClient()`（`EgressPurpose.Image`、ECH 改写、`attachSiteCookies=false`） | 不在 Cloudflare 后（`shared/src/desktopTest/.../EchGateLiveTest.kt:265`）；无 cookie |
| `www.getchu.com/brandnew/…` | `ComposePreviewDataSource.kt:468` | **getchu 出口** = `createCdnFetchClient(extraInterceptors = listOf(getchuBrandHeaderInterceptor))` | 需 getchu 专用 cookie（`GetchuImageLoader.jvm.kt:29-46`），非站点 CF cookie |
| `picsum.photos` | `ComposePreviewDataSource.kt:119` 等 | 任意（占位假图） | — |
| API/HTML host（hanime1.me 等） | 非图片 | **API 出口** `createHanimeHttpClient()` = `ServiceCreator.hClient`（`cookieJar(HCookieJar())` + CF 拦截器 + 磁盘缓存） | 需 cookie / CF |

**关键判定**：**不存在依赖 API 出口 cookie / CF clearance 的图片 host**。所有图床（CDN、getchu）都归各自的 CDN 系出口；
API 出口只服务 HTML/API。因此 `Main.kt:118` 把单例指向 API 出口，对图片而言是**错的出口**——
一旦兜底真的生效，图片会走带站点 cookie 的 API 出口（既非必需，又把站点 cookie 发给第三方图床，属泄漏面）。

#### 证据 3 · 裁决与最小改动

**建议 ①（推荐）：把注册的单例指向 CDN 出口，与 `rememberHanimeImageLoader()` 共用同一进程级实例。**
- 理由：注册现状是“错出口的兜底”；① 既消除地雷，又让“万一某处漏传 loader”时自动落到与显式路径**同出口、同缓存**的实例
  （真·统一，仍只有一套缓存）。
- 最小改动（2 处符号）：
  - `shared/src/jvmMain/kotlin/lovehan1me/ui/component/HanimeImageLoader.jvm.kt`：把进程级取用函数
    `hanimeImageLoaderOrNull(context)`（当前 `internal`）提升为 `public`（或加一个 public 转发函数）。
  - `desktopApp/src/main/kotlin/lovehan1me/desktop/Main.kt:118-128`：改为
    `setSingletonImageLoaderFactory { context -> hanimeImageLoaderOrNull(context) }`，
    删掉 `KtorNetworkFetcherFactory(createHanimeHttpClient())` 那套 API 出口组件。
- 验证：写一条 desktopTest，断言 `SingletonImageLoader.get(context) === hanimeImageLoaderOrNull(context)`；
  反向验证——把单例临时改回 API 出口，该断言必须**转红**。另因单例当前无人使用，改后图片行为应逐字不变（无调用点副作用）。

**备选 ②：直接删除 `Main.kt:118-128` 死配置。** 更省（删 11 行），代价是放弃该兜底——若将来有人新增裸 `AsyncImage(`
漏传 loader，会退回 Coil 自建（无 ECH）客户端、图加载失败（即注释描述的旧故障）。若团队判定“所有取图点已强制显式 loader、
兜底无必要”，② 亦可接受，但需在代码评审上加约束。

**不采纳 ③（保持现状）**：现状不是“中性死配置”，而是“错出口的活地雷”，保留无正当理由。

> 边界：本轮**只读**，未编辑产品源码、未运行 Gradle。所有 file:line 均对应当前磁盘状态。

---

## 2. 任务 B：核心模型稳定性改造

### 2.1 问题（已核实，路径已更正）
- `shared/src/commonMain/kotlin/lovehan1me/site/hanime1/HanimeResolution.kt:13`：
  `typealias ResolutionLinkMap = LinkedHashMap<String, HanimeLink>` —— 具体**可变**集合类 → 编译器判 unstable。
- `shared/src/commonMain/kotlin/lovehan1me/core/domain/model/HanimeVideo.kt:103` `var isWatchLater`、`:109` `var isSelected` —— 含可变字段 → 整类 unstable。
- **传导终点（精确）**：`shared/src/commonMain/kotlin/lovehan1me/feature/video/VideoIntroductionScreen.kt:275`
  的 `internal fun VideoIntroductionContent(video: HanimeVideo, …)` —— 因 `HanimeVideo` unstable，详情页整棵子树在 `video` 每次 `copy()`（收藏/评分）后无法跳过重组。

### 2.2 量化开关的真实位置（复核结论，务必按此做）
- 项目 **只有一个** 约定插件：`build-logic/src/main/kotlin/han1me-kmp-library.gradle.kts`，它只 `apply` KMP + AGP-KMP。**项目里没有任何现成的 Compose 编译器配置落点。**
- Compose 是**逐模块**用版本目录 alias 应用：`alias(libs.plugins.jetbrains.compose)` + `alias(libs.plugins.compose.compiler)`（后者 id = `org.jetbrains.kotlin.plugin.compose`，`version.ref = kotlin` = **2.4.10**，见 `gradle/libs.versions.toml [plugins]`）。应用该插件的模块：`:shared`、`:video:ui`、`:video:engine`、`:video:surface`、`:desktopApp`、`:app`（`:video:contract` 不应用）。
- **"原本的稳定性报告写在哪"**：据 `docs/perf-audit-2026-10-06-shared.md:15-19`（AI 生成取证文档，**按项目铁律不可直接采信**），此前是加在 `shared/build.gradle.kts` 的 `composeCompiler { metricsDestination / reportsDestination }` 块里；我**独立核验该块确已回退**（全仓 `composeCompiler` grep = 0）。结论：**原落点＝模块内 `shared/build.gradle.kts`，已删除，磁盘上当前无任何报告产物。**
- **头号风险**：同文档称 CMP 1.12 + Kotlin 2.4.10 开报告后 `:shared:compileKotlinDesktop` 报 `Internal compiler error`（5m11s）。**此结论仅来自文档、未复验**，但意味着**量化手段本身可能不可用** → B 的成败不得绑定报告。

### 2.3 量化设计（改前 vs 改后）
- **首选：有界尝试（不作为验收门槛）**。在**根** `build.gradle.kts` 加一段*可整段删除*的临时块，**只开 `reportsDestination`、只影响已应用 Compose 的模块**，缩小崩溃面：
  ```kotlin
  subprojects {
      plugins.withId("org.jetbrains.kotlin.plugin.compose") {
          extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension>("composeCompiler") {
              reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
          }
      }
  }
  ```
  产物：`<module>/build/compose-reports/*-classes.txt`（每类 stable/unstable）与 `*-composables.txt`。改前后各跑一次，把 `HanimeVideo` 由 `unstable → stable` 的前后行贴进 `docs/evidence/model-stability-before.txt` / `-after.txt`。
  **若仍崩**：记 `docs/evidence/model-stability-blocked.md`（命令 + 崩溃行），**直接走 2.4 兜底，不阻塞 B**。
- **兜底（无编译器也能跑，且是本轮真正验收证据）**：见 2.4。**反直觉但关键**：Compose 稳定性是**声明类型的编译期属性，运行期不可见** —— `toResolutionLinkMap()` 返回对象运行期仍是 `LinkedHashMap`，所以"`is MutableMap` 断言"这类**运行时检查证明不了**稳定性；运行时能直接断言的只有"字段是否 final / getter 声明返回类型"。
- **参照（项目"借鉴 animeko"的真实源头）**：`reference/animeko/build-logic/src/main/kotlin/ani.kmp-compose.gradle.kts:50-54` 用 `extensions.configure<ComposeCompilerGradlePluginExtension> { stabilityConfigurationFiles.add { … } }`（该文件顶部还应用了 `skydoves/compose.stability.analyzer`，dump 落 `build/stability`）。**本轮不引入**，仅备选记录。

### 2.4 改造方案（小步，零调用点改动）
改动面（**实测，非推断**）：
- `HanimeVideo.kt:103 var isWatchLater` / `:109 var isSelected`：**全仓无任何 `.isWatchLater =` / `.isSelected =` 赋值**（ripgrep 命中仅 reference/ 与构造实参）。写入只在构造：`site/hanime1/Parser.kt:438/444`、`feature/video/VideoViewModel.kt:128/135/143`。读点：`VideoViewModel.kt:404`（`copy(isSelected = …)` 造新对象，`val` 仍可编译）、`:443`、`VideoIntroductionDialogs.kt:360`、`VideoRouteActions.kt:167`。
- `MyList` / `MyListInfo` 两个类**均未标 `@Serializable`**（在 `HanimeVideo` 上是 `@Transient`）→ 改 `val` 对序列化零影响。
- `ResolutionLinkMap` 消费点**全部只读**：`VideoRouteHostScreen.kt:566/891`（`.map`）、`VideoIntroductionDialogs.kt:171`（`.keys.toList()`）、`videoUrls.isEmpty()`、`commonTest/VideoCacheLookupTest.kt:33-34`；构造点 `androidMain/HanimeCacheManager.kt:150/155`、`VideoCacheStore.kt:25`、`ComposePreviewDataSource.kt:437` 全用 `linkedMapOf(…)`（即 `Map`）。

落地（**2 个文件、3 处符号，同一提交**）：
1. `shared/src/commonMain/kotlin/lovehan1me/core/domain/model/HanimeVideo.kt`
   - `:103 var isWatchLater` → `val`；`:109 var isSelected` → `val`。
   - 类头加 `import androidx.compose.runtime.Immutable` + `@Immutable`（置于 `data class HanimeVideo` 前）。
   - **理由**：只做 `var→val` + typealias 仍不够 —— `Map` 接口在 Compose 里**本身仍判 unstable**，`@Immutable` 才是"契约式短路"（正是要给自研代码铺的 animeko 纪律）。承诺成立的前提是"已实测无写入点"，把这条写进 KDoc；日后新增可变字段必须先改这里。
2. `shared/src/commonMain/kotlin/lovehan1me/site/hanime1/HanimeResolution.kt:13`
   - `typealias ResolutionLinkMap = LinkedHashMap<String, HanimeLink>` → `= Map<String, HanimeLink>`（去具体可变类，与 `@Immutable` 配套）。
   - `toResolutionLinkMap()` 现返回 `resArray.filterNotNull().toMap(linkedMapOf())`，仍是 `Map`，**无需改**。
3. **兼容层：不需要**。以上源码级兼容。
- **原子性**：`@Immutable` 与 `var→val` **必须同提交**；只加 `@Immutable` 而留 `var` 就是"撒谎的契约"，2.5 守卫会转红。

### 2.5 守卫测试 + 反向验证（硬验收）
新增 `shared/src/desktopTest/kotlin/lovehan1me/core/domain/model/ModelStabilityGuardTest.kt`（desktopTest 才有 `java.io.File`/JVM 反射；先例：`ModuleLayeringTest` 走源码扫描、`SearchResultCapTest` 走纯函数）：
- **用例①（反射，钉 `val`）**：`HanimeVideo.MyList::class.java.getDeclaredField("isWatchLater").modifiers and java.lang.reflect.Modifier.FINAL != 0`；`MyListInfo.isSelected` 同。`var` 的 backing field 非 final，`val` 是 final —— **只有真改成 `val` 才过**。
- **用例②（反射，钉不可变声明类型）**：`HanimeVideo::class.java.getMethod("getVideoUrls").returnType == java.util.Map::class.java && != java.util.LinkedHashMap::class.java`。typealias 展开进 JVM 签名：改前 `LinkedHashMap`、改后 `Map`。
- **用例③（源码扫描，钉 `@Immutable`）**：`@Immutable` 是 `AnnotationRetention.BINARY`，**Java 反射看不到**，故扫源码：读 `HanimeVideo.kt` 全文，断言含 `@Immutable` 且紧邻 `data class HanimeVideo`（沿用 `ModuleLayeringTest.repoRoot()` 的定位法）。
- **反向验证（逐条，缺一即未完成）**：① 把 `:103/109` 改回 `var` → 用例① 必须转红；② typealias 改回 `LinkedHashMap` → 用例② 必须转红；③ 删 `@Immutable` 行 → 用例③ 必须转红。各跑一次，把**转红用例名 + 断言消息**贴回。

### 2.6 会牵连的现有用例
- 预期 **0 破坏**：`val` 不改 `copy()` 签名；`Map` 是 `linkedMapOf()` 的超类型；无对 `ResolutionLinkMap` 的显式类型断言（已扫描）。
- 现存 531 桌面用例**零覆盖**稳定性语义 → 本任务**必须自带**上面 3 条守卫才算完成。

### 2.7 验证命令 / 回滚（需在工程师空闲时执行，勿与施工构建互锁）
- 编译：`./gradlew :shared:compileKotlinDesktop --offline`
- 测试：`./gradlew :shared:desktopTest --tests "lovehan1me.core.domain.model.ModelStabilityGuardTest" --offline`
- 全量回归：`./gradlew :shared:desktopTest --rerun-tasks --offline`（对照 §0 的 531 / 0 fail / 4 skipped）
- 回滚：只动 2 个源文件 → `git checkout -- shared/src/commonMain/kotlin/lovehan1me/core/domain/model/HanimeVideo.kt shared/src/commonMain/kotlin/lovehan1me/site/hanime1/HanimeResolution.kt`；根 `build.gradle.kts` 的临时报告块单独删除。

---

## 3. 任务 C：分页并发控制

### 3.0 行号更正
`page++` 真实位置是 `shared/src/commonMain/kotlin/lovehan1me/feature/search/SearchScreen.kt:441`
（`{ viewModel.page++; executeSearch() }`）；`:423` 是下拉刷新的 `doSearch()`，不是 `page++`。

### 3.1 全仓同构模式清查（已实测）——真重入**只有 Search 一处**
| 位置 | 分页驱动 | in-flight 守卫 | 判定 |
|---|---|---|---|
| `SearchViewModel.kt:163-215` + `SearchScreen.kt:441` | **UI 侧 `viewModel.page++`**（`page` 是 public var，`SearchViewModel.kt:74`） | **无**：每次 `getHanimeSearchResult` 新起 `viewModelScope.launch`，无 Job 记录、无 cancel、无请求标识 | ❌ **真重入** |
| `ArtistViewModel.kt:84-117` | VM 内 `page += 1`（`:99`） | 有 `if (loading) return`（`:86`） | ⚠️ **弱**：`load()`（`:60`）绕过守卫直接 `loadMoreInternal`，且**不 cancel** 上一请求、**不校验 userId** → 快速切作者会把两个作者的页并进 `_works` |
| `OnlineWatchHistoryViewModel.kt:71-116` | `_loadedPageCount`（`:96`）派生页号 | `loadJob?.cancel()`（`:85`）+ `_isLoadingMore` | ✅ **达标，本任务参照实现** |
| `SubscriptionContent.kt:98-106` / `WatchHistoryScreen.kt:388-396` / `MyplayListBottomSheet.kt:405-410` / `PlaylistContent.kt:55-60` | 网格侧 `snapshotFlow{layoutInfo}.map{…}.distinctUntilChanged()` 触底 | 由各自 VM 守（本轮不逐一改） | ✅ 网格侧已去重（源码已实测在） |

### 3.2 Search 最小改法（单 Job + 请求标识）
1. `shared/src/commonMain/kotlin/lovehan1me/feature/search/SearchViewModel.kt`
   - 加 `private var searchJob: Job? = null`、`private var requestToken: Long = 0L`。
   - `getHanimeSearchResult(…)` 内：`val token = ++requestToken; searchJob?.cancel(); searchJob = viewModelScope.launch { … collect { state -> if (!shouldApplySearchResponse(token, requestToken)) return@collect; …原逻辑… } }`。
   - 新增**可测纯函数**（与 `mergeSearchPage` 同风格，置文件顶层）：
     - `internal fun shouldApplySearchResponse(token: Long, current: Long): Boolean = token == current`
     - `internal fun nextPageOrNull(inFlight: Boolean, currentPage: Int): Int? = if (inFlight) null else currentPage + 1`
   - 新增 `fun prepareNextPage(): Boolean { if (searchJob?.isActive == true) return false; page += 1; return true }`。
2. `shared/src/commonMain/kotlin/lovehan1me/feature/search/SearchScreen.kt:441`
   - `{ viewModel.page++; executeSearch() }` → `{ if (viewModel.prepareNextPage()) executeSearch() }`。
   - **保留** `executeSearch()` / `doSearch()` 不动，避免触碰筛选参数装配；`doSearch` 的 `page = 1` 与 VM 内 `searchJob?.cancel()` 组合即可丢弃旧响应。
- **为什么两层都要**：`cancel()` 让旧收集器停；`token` 兜住"cancel 生效前，已越过检查点的 `_searchFlow.update`"这一窗口。测试断言的是 token 层（**无时序依赖**，headless 可跑）。

### 3.3 守卫测试 + 反向验证
新增 `shared/src/desktopTest/kotlin/lovehan1me/feature/search/SearchPaginationGateTest.kt`（与 `SearchResultCapTest` 同目录、同"抽纯函数"手法）：
- 用例①：`shouldApplySearchResponse(1, 2) == false`、`shouldApplySearchResponse(2, 2) == true`（陈旧响应必须被拒）。
- 用例②：`nextPageOrNull(inFlight = true, currentPage = 5) == null`、`nextPageOrNull(false, 5) == 6`（在途时不得再翻页）。
- **反向验证**：把 `shouldApplySearchResponse` 改成恒 `true` → 用例① 必须转红；把 `nextPageOrNull` 改成恒 `currentPage + 1` → 用例② 必须转红。
- **不可替代**：`mergeSearchPage` 已按 `videoCode` 去重，会**掩盖**列表里的"重复条目"症状，但**掩盖不了**"翻页号跳号 / 陈旧页覆写 `_searchStateFlow`" —— 现有去重用例不能替代本守卫。

### 3.4 Artist 弱守卫加固（同批，成本低）
`ArtistViewModel.kt`：`load()` 内加 `loadJob?.cancel()`；`loadMoreInternal` 收到结果前校验 `loadedUserId == userId`，不符即丢弃；把 `loading` 收敛为 `loadJob?.isActive == true` 单一事实源。守卫复用 3.3 的 `nextPageOrNull`。

### 3.5 验证命令 / 回滚
- 测试：`./gradlew :shared:desktopTest --tests "lovehan1me.feature.search.SearchPaginationGateTest" --offline`
- 回归：`./gradlew :shared:desktopTest --rerun-tasks --offline`
- 回滚：只动 `SearchViewModel.kt` + `SearchScreen.kt`（Artist 可选），`git checkout -- <文件>` 即回滚。

### 3.6 本轮有序任务清单（A/B/C 汇总）
| 序 | 任务 | 动哪些文件（完整相对路径） | 改哪个符号 / 签名 | 依赖 | 优先级 |
|---|---|---|---|---|---|
| T1 | A 图片加载器单例化（**已施工，待验收**） | `shared/src/jvmMain/.../ui/component/HanimeImageLoader.jvm.kt`、`shared/src/iosMain/.../ui/component/HanimeImageLoader.ios.kt`、新增 `shared/src/desktopTest/.../ui/component/HanimeImageLoaderSingletonTest.kt` | `hanimeImageLoaderOrNull(context, inspection)` / `resetHanimeImageLoaderForTest()` | 基线绿 | P0 |
| T2 | B typealias 去可变 | `shared/src/commonMain/kotlin/lovehan1me/site/hanime1/HanimeResolution.kt:13` | `ResolutionLinkMap = Map<String, HanimeLink>` | 无 | P0 |
| T3 | B 模型不可变 + 契约 | `shared/src/commonMain/kotlin/lovehan1me/core/domain/model/HanimeVideo.kt:103/109` + 类头 | `isWatchLater`/`isSelected` → `val`；加 `@Immutable` | 无（与 T2 **同提交**） | P0 |
| T4 | B 守卫测试 | 新增 `shared/src/desktopTest/kotlin/lovehan1me/core/domain/model/ModelStabilityGuardTest.kt` | 3 用例 + 3 条反向验证 | T2,T3 | P0 |
| T5 | C Search 单 Job + token | `shared/src/commonMain/kotlin/lovehan1me/feature/search/SearchViewModel.kt`、`.../SearchScreen.kt:441` | 加 `searchJob`/`requestToken`、`prepareNextPage()`、`shouldApplySearchResponse()`、`nextPageOrNull()` | 基线绿 | P0 |
| T6 | C 守卫测试 | 新增 `shared/src/desktopTest/kotlin/lovehan1me/feature/search/SearchPaginationGateTest.kt` | 2 用例 + 2 条反向验证 | T5 | P0 |
| T7 | C Artist 弱守卫加固 | `shared/src/commonMain/kotlin/lovehan1me/feature/home/artist/ArtistViewModel.kt` | `load()` 加 cancel；`loadMoreInternal` 校验 userId | T5 | P1 |
| T8 | B 量化（有界尝试，非门槛） | 根 `build.gradle.kts`（临时块，可整段删） | `reportsDestination` | 工程师空闲 | P2 |

**实现顺序**：`T2 ≡ T3`（同提交）→ `T4`；`T5` → `T6`；`T7` 依附 `T5`；`T8` 独立、**允许失败但不阻塞 B**。
**第一步该动的文件**：`shared/src/commonMain/kotlin/lovehan1me/core/domain/model/HanimeVideo.kt`（连同 `HanimeResolution.kt` 同提交）—— 单文件、零调用点、守卫可离线独立跑，且是"把 `@Immutable` 纪律铺到自研代码"的第一个样板。

---

### 3.5 下一轮：MyList 收藏 / 稍后再看（同一缺陷，且更糟）

任务 C 只修了搜索一处（派单范围所限）。工程师枚举出的其余同构点里，下面这几处与 C 是**同一缺陷**，
但比搜索更糟 —— `page` 的**读取与自增跨两条语句、非原子**：

| 位置 | 代码 | 问题 |
|---|---|---|
| `shared/src/commonMain/kotlin/lovehan1me/app/navigation/main/MyListRoutes.kt:54-58` | `val page = fav.favVideoPage` → `getMyFavVideoItems(…, page)` → `fav.favVideoPage = page + 1` | 快速触底两次会读到**同一个 page**：重复请求 + 追加重复页 |
| 同文件 `:91-101` | WatchLater 版同上 | 同上 |
| 同文件 `:48-52` / `:91-95` | `onRefresh` 里先 `page=1`、触发后再 `page=2` | 刷新与加载共用一个可变序号，手工维护易错 |
| `shared/src/commonMain/kotlin/lovehan1me/feature/library/MyListSubViewModel.kt:33-67` | `loadItems` 内 `scope.launch`，**未保留 Job、无 cancel、无请求标识**；`isRefreshing` 是普通 `var` 且在 `collect` 里被改值 | 两轮加载重叠时，后到的一轮可能读到 `isRefreshing == false` 而**误判为追加**；`loadedPageCount`（`:50`）还会被旧响应回退 |

**收敛方向**：与 C 同构 —— 接收端持 `PagingGate` + 页序号，UI 只发一次调用，
`onRefresh` 走 `restart`。注意 `:55` 已有 `distinctBy(videoCode)`，它掩盖了重复条目，
**但不能掩盖乱序与 `loadedPageCount` 回退** —— 守卫测试要钉的是后者，别被去重骗过去。

---

## 4. 任务 D：覆盖缺口补齐（排期在后）

| 目标 | 为什么 | 建议做法 |
|---|---|---|
| `Parser.kt`(1251 行) | 现有唯一覆盖依赖 gitignore 的 `.workbuddy/` 夹具，CI 干净 checkout 上零覆盖 | 用**内联 HTML 字符串**夹具重写 6 个脏 HTML 容错用例 |
| `:video:surface`（0 测试） | mediamp × Compose 唯一共存层，改它没有安全网 | 先补最小 smoke：宿主渲染面能挂载不崩 |
| `TopLevelBackStack`(98 行) | 自研导航回退栈无测试 | 4 个用例：push/pop/清栈/回退到根 |
| `PlaybackController` seek 边界 | `PlaybackController.kt:212` 只钳下界 | 3 个用例钉上界与负值 |

### 4.1 落地记录（2026-10-07，第 3 阶段，一次性收口）

- **seek 上界钳制**（prod 改动）：`PlaybackController.seekTo` 改为双界钳
  （`durationMs>0` 时 `coerceIn(0, duration)`，未知时长只保下界）；`seekBy`
  经 `seekTo` 间接得双界。`PlaybackControllerTest` +3（超上界/越过片尾/
  未知时长），反向验证撤掉钳制后 3 条恰好转红。
- **`TopLevelBackStackTest`**（commonTest，5 用例）：push/pop、切 tab 弹根、
  singleTop 去重、replaceTop 退化、popTo 切片。反向验证（去掉弹根）恰好 1 条转红。
- **`ParserDirtyHtmlTest`**（desktopTest，3 用例，内联夹具）：缺字段跳过、
  空页面/垃圾不抛、`NoMoreData` 信号。反向验证（选择器错位）恰好 1 条转红。
- **`HomePageCacheKeyTest`**（commonTest，2 用例）：抽纯函数 `cacheKeyFor`
  （原两函数委托），钉换域/换用户 miss、前缀隔离、空值回退。反向验证恰好 1 条转红。
- **`:video:surface` 冒烟**：补 `commonTest{ kotlin("test") }` 依赖（与 contract
  同款），新增 `PlatformVideoSurfaceGuardTest`（2 用例：expect 签名无 mediamp/
  三端 actual 齐备，注释剥离后断言）。反向验证（正文区注入标记）恰好 1 条转红。
  真机挂载仍未验（需设备），headless 能钉的只是契约。
- **A2 Getchu 单例化**：jvm/ios 双 actual 收成进程级单例（`PlatformLock` +
  inspection 不缓存，与 A1 同构），`GetchuImageLoaderSingletonTest` 4 用例，
  反向验证（去缓存）2 条转红。
- **Artist 弱守卫**：`load()` 改 cancel-and-restart（`loadJob` 跟踪，在途可打断），
  `load()`/`loadMoreInternal()` 收集分支加 `shouldApplyArtistResponse` 代际校验；
  `ArtistStaleGuardTest` 2 用例，反向验证（恒 true）2 条转红。
- 全量：`733/0/0/4`（shared 578 + contract 48 + engine 28 + ui 77 + surface 2），
  skipped 4 全属 `EchGateLiveTest` 门禁；`:shared` iOS 模拟器 + Android 编译绿。
- 遗留：`LocalFav/LocalWatchLater.refresh()` 重订阅语义仍只靠"与旧版逐句等价"
  （需 fake DAO 才可测，本轮不为测试改产品代码）；`NetworkRepo` 分页单测、
  真机挂载/真机周仍待设备。

---

## 5. 明确不做（避免过度重构）

- 不动 `video` 四层分层与 `PlaybackUiState` 的位置投影 —— 已验证有效的取舍。
- 不引入 Paging3 —— 现有取舍有据，缺的是护栏不是重写。
- 不收敛 Ktor / OkHttp 双引擎。
- 不把 `VideoCardItem.kt:344` 桌面/iOS 的空函数体当 bug 修 —— 它是代码显式登记且有单测钉住的已知取舍。
- 首页推荐 Feed、搜索联想/热搜属"内容平台化"新需求，走独立需求流程，不在本次加固范围。
