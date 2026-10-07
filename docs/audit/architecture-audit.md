# LoveHan1me 架构与性能诊断（纯代码层）

> 方法：只读源码，全部结论附 `路径:行号`。不采信任何文档/注释/提交记录；凡注释与代码冲突，一律以代码为准并在文中点出。
> 范围：`:app` `:shared` `:desktopApp` `:video:{contract,engine,ui,surface}`（不含 `reference/` `echgate/` `player/`）。
> 规模基线：`shared` 86235 行（commonMain 60303 / androidMain 4852 / desktopMain 3142 / iosMain 3352 / jvmMain 3043）、`video/ui` 10173、`video/engine` 4141、`video/contract` 2469、`app` 780、`desktopApp` 795。测试：97 个含 `@Test` 的文件。

## 一句话结论

**这不是业余项目，而是一个"网络层与播放抽象已达准工业水准，但 UI 层的 Compose 素养与状态治理明显落后"的项目**：统一出口、超时/重试/旧页预展、CF 绕过、以及"把播放位置挡在组合期之外"的状态投影，做得比多数商业客户端还细；但承载主要流量的两个界面（详情页、搜索无限列表）在**图片资源、模型稳定性、分页并发**三处各有一个正在发生、用户能感知的缺陷。差距不在能力，而在于没有人对 UI 层性能做系统性的 ownership。

## 六维评分

| 维度 | 分 | 一句话理由 | 最严重证据 |
|---|---|---|---|
| 1 分层与依赖方向 | **3** | 契约层确实干净、有可执行护栏；但声明与实际不符，且模块边界无法按包名验证 | `settings.gradle.kts:74` 称"ui 禁 mediamp"，而 `video/ui/build.gradle.kts:29` 显式 `api(libs.mediamp.api)`，源码 24 处 `org.openani.mediamp` 引用 |
| 2 状态管理 | **3** | 播放侧 truth/request 分离 + 组合期只读投影是教科书级；但搜索态仍是普通 `var` 加手工版本号，详情页 UI 状态裸在 God Composable 里 | `SearchViewModel.kt:74-90` 配 `:125 filterRevision` / `:128 bumpFilterRevision()`（该文件 116-124 行注释自承认 2026-09-16 出过真 bug） |
| 3 Compose 性能与稳定性 | **3** | 移植来的播放器有 40+ 处 `@Stable/@Immutable`，自有 6 万行只有 2 处；核心模型把详情页的可跳过性直接打没了 | `HanimeResolution.kt:13` `typealias ResolutionLinkMap = LinkedHashMap<String, HanimeLink>` + `HanimeVideo.kt:103/109` 两个 `var` → 消费点 `VideoIntroductionScreen.kt:275` |
| 4 数据层 | **4** | 统一 `ioRequest`（CF 验证等待 + 单次续跑）+ sealed 三态 + OkHttp 10MB 缓存 + 三条出口字符串实测齐全，只差分页治理 | `NetworkRepo.kt:612-690`；短板在 `SearchViewModel.kt:189-213` 的分页无并发控制 |
| 5 并发与生命周期 | **4** | 自有代码 **0 处 `GlobalScope`、0 处裸 `Thread`/`Executor`**；生产源集仅 2 处 `runBlocking`，且均有成立理由 | `MainViewController.kt:36`（主线程 + Default 作用域，不死锁）；`NetworkSettingsRoute.kt:698`（在 `Dispatchers.IO` 内阻塞） |
| 6 平台差异治理 | **4** | 83 个 expect，所有平台源集 **0 个 `TODO()`**；平台特化只占约 13% 代码量，是真 KMP 而非三份并行实现 | 短板是覆盖率：iosTest 仅 2 文件/142 行，desktopTest 42 文件/7216 行 |

## Top 10 问题清单

### P1 · 每个列表项各自构造一个 Coil ImageLoader（资源 × N）
- **证据**：`ui/component/Utils.kt:73` `imageLoader = rememberHanimeImageLoader()` → `HanimeImageLoader.jvm.kt:20` `return remember(context, isInspectionMode) { ImageLoader.Builder(context)... }`；入口 `VideoCardItem.kt:158 RetryableImage`（这张卡在首页/搜索/历史/收藏到处都是）；全仓 17 处调用。
- **影响**：`remember` 按**组合槽位**记忆而非进程单例。一个网格页可见 N 张卡就有 N 个 ImageLoader 同时存活，各自独立的内存/磁盘缓存命名空间；同一张封面在两个页面各解码一份，占用随可见卡片数线性增长。桌面 `Main.kt:118` 注册的 Coil 单例被这些显式实参整个绕过。**症状**：快滑列表时内存抖动、同一封面反复闪回占位图、低端设备 OOM。
- **建议方向**：收敛为进程级单例，经 `LocalImageLoader` / `setSingletonImageLoaderFactory` 下发，`HanimeAsyncImage` 不再默认构造 loader。

### P2 · 核心领域模型不稳定，详情页失去可跳过性
- **证据**：`HanimeResolution.kt:13` 把 `videoUrls` 定义成 `LinkedHashMap` 的 typealias（可变集合类型）；`HanimeVideo.kt:103 var isWatchLater`、`:109 var isSelected`。二者流向 `VideoIntroductionScreen.kt:275 video: HanimeVideo`。对照：自有 commonMain 里 `@Stable/@Immutable` 仅 2 处，移植层 `video/ui` 有 40+ 处。
- **影响**：模型与其宿主组组合均被推断为 unstable，详情页任意一次重组（切 tab、收藏成功上报、高手更快程）都会重算整棵简介树（含相关视频 LazyColumn 与标签 FlowRow）。**症状**：详情页滑动/交互掉帧，点收藏后整屏顿一下。
- **建议方向**：`var` 改 `val`；集合改 `kotlinx.collections.immutable` 或给模型加 `@Immutable`。先开 Compose Compiler 稳定性报告拿到客观数据，再动手，避免凭感觉重构。

### P3 · 列表分页无并发控制（陈旧响应覆盖新数据）
- **证据**：`SearchViewModel.kt:163-215` 每次 `getHanimeSearchResult` 都新起一个 `viewModelScope.launch`，**没有 Job 句柄、没有 in-flight 判重**；"加载更多"回调 `SearchScreen.kt:423` 是 `{ viewModel.page++; executeSearch() }`，快速上滑可重入；旧页到达时仍在 `:197` 的 `_searchFlow.update` 里 `mergeSearchPage` 追加。`doSearch()` 只清列表，不取消在途请求。
- **影响**：跳页/重复条目/顺序错乱；下拉刷新后可能把上一轮结果再并进来。**症状**：列表出现重复卡片、翻页后跳回某处、偶发"加载更多"永不结束。
- **建议方向**：每次搜索维持单 Job，`(query, filters)` 变化时取消并重置；页码并入请求标识，过期响应直接丢弃。这是普通用户日常滑动中**必然遇到**的正确性问题。

### P4 · 播放页 God Composable（1115 行）
- **证据**：`VideoRouteHostScreen.kt` 单函数内含约 30 个 `remember { mutableStateOf }`（`:222-249` 密集一段）、十余个 `LaunchedEffect`/`DisposableEffect`（`:200,209,261,268,446,488,519,549,639,652,680,687,713,732,754`），以及 `pageHost` 匿名实现（`:396-424`）。
- **影响**：任一本地状态（音量/亮度/抽屉开关/截图在途标志）变化即整屏重组并重建约 60 个 lambda 实参；该函数无法单测，改一处要猜三处。它也是 P5、P6 的温床。
- **建议方向**：抽 ScreenStateHolder，并按手势/画质/弹幕/宿主拆四组独立编排函数。

### P5 · 在组合期执行副作用（发请求 + 写状态）
- **证据**：`VideoRouteHostScreen.kt:533-547` 用 `remember(route.videoCode, route.localUri) { ... checkedQuality = null; videoTitle = ""; ...; viewModel.getHanimeVideo(...) }`。文件注释 `:529` 自述这是为了绕开"LaunchedEffect 被主线程堵住 9 秒"而打的补丁。
- **影响**：`remember` 的 initializer 在组合期执行、可被重放或中断；在其中写 Compose 状态会引发额外的重组轮次，网络请求也不再受任何生命周期约束。**症状**：进详情页偶发双重请求，配置变化时状态被莫名重置。
- **建议方向**：副作用还原到 `LaunchedEffect`；同时根治"主线程被堵 9 秒"本身（P4 拆分 + 首帧前不做非必要初始化）。

### P6 · DisposableEffect 捕获过期快照
- **证据**：`VideoRouteHostScreen.kt:495` 的 key 是 `lifecycleOwner, playbackController, route.videoCode, isDualPane`，body `:501` 却读 `hostUiState.isInPipMode`（该值来自 `:191` 的 collect）。key 不含它，观察器闭包就永远持着建立那一刻的值。
- **影响**：先进 PiP 再退后台仍被暂停，或反之；属于"状态撒谎"类偶发问题。用户只会说"有时候退后台会停/不停"，极难复现。
- **建议方向**：用 `rememberUpdatedState` 包一层，或把真值下沉到引擎/host 对象读取。

### P7 · 非响应式状态 + 手工版本号
- **证据**：`SearchViewModel.kt:74-90` 十余个普通 `var`（含对外可变的 `tagMap`/`brandMap`），靠 `:125 filterRevision` 与 `:128 bumpFilterRevision()` 手工补偿重组。同类问题还有 8 处在组合体里直读 `SettingsRepository.current`（含 `VideoRouteHostScreen.kt:772`），而同文件 `:753` 又正确用了 `collectAsStateWithLifecycle` —— 同一文件自相矛盾，说明没有统一约定。
- **影响**：宽屏常驻筛选栏显示过期条件；改设置要等下一次无关重组才生效。**症状**："界面某些区块刷新不全"，看起来像随机 bug。
- **建议方向**：筛选态收敛成一个 `data class` 走 StateFlow；规定 `SettingsRepository.current` 只在非组合代码中读取。

### P8 · 自定义 LazyColumn 包装层：当前只见成本，不见收益
- **证据**：`ui/component/lazy/AnimatedLazy.kt` 自行重造了 `item`/`items`/`itemsIndexed` 共 8 个重载（`:144-320`），每个 item 额外多包一层 `Box`（`:337`、`:357`）；而三个入口的 `enableItemAnimation` 默认值全是 `false`（`:49`、`:81`、`:114`），全仓也没有任何一个调用点显式开启。
- **影响**：全部 40+ 个列表页被绑到私有 DSL，无法享受 Foundation 后续演进；当下这一层只增加一层布局节点与维护负担。顺带一提，`VideoGridContent.kt:48` 只传了 `key` 没传 `contentType`，角色混排的列表拿不到回收收益。
- **建议方向**：退掉 wrapper，直接切官方 `Lazy*`；确需入场动画的个别页面单独加 `animateItem`（Foundation 1.9+ 原生支持）。

### P9 · 分层护栏不完整，可供判断的事实基础（包名）是脏的
- **证据**：`ModuleLayeringTest.kt:51` 用例名写"UI 与编排层只许用 mediamp-api"，实际只拦了三个后端包（`:59`），**未拦 `ui → engine`**（该边界在 `video/ui/build.gradle.kts` 的注释里声明但无护栏）。而 "engine 不得依赖 Compose" 只能靠字串匹配 `androidx.compose`（`:25`），因为包名在模块间大量撞车：`lovehan1me.feature.player` 同时存在于 `shared`(9 处)、`video:engine`(25 处)、`video:surface`(4 处)；`lovehan1me.feature.danmaku` 同时存在于 `shared`(6)、`video:contract`(5)、`video:ui`(3)。
- **影响**：无法按 import 做依赖方向校验；用包名 grep 会得到错误的模块归属；未来新增护栏成本极高。
- **建议方向**：统一为每层独占包名前缀（`lovehan1me.video.ui.*` 已是好样例），再叠加依赖方向静态校验。

### P10 · 统一封装迁移停在半途，同类问题历史上已栽过一次
- **证据**：图片方面，commonMain 里 21 处 `HanimeAsyncImage(` 对应 **29 处裸 `AsyncImage(`**；`HanimeImageLoader.kt:14` 的注释自己写着"后续 16 个文件的迁移"待办。同步桥接方面，`jvmMain/.../NetworkSettingsRoute.kt:698` 留了 `runBlocking { Parser.homePageVer2(body) }`（parser 改 suspend 后的桥）。
- **影响**：裸调用点可能不带 ECH 网关/代理/DNS 出口配置。`CdnFetchClient.kt:13-21` 的注释明确记载这类事已经发生过一次（`CoverImageFetcher` 曾漏配，导致"开了网关网页能开、封面出不来"）。
- **建议方向**：收口剩余 `AsyncImage`，并给这两条约定各补一条 `ModuleLayeringTest` 式的护栏用例防回退。

## 最值得做的 3 件事（按 ROI 排序）

1. **图片管线单例化（P1）**。改动约 3 个文件、不涉及任何 API 语义变化；收益同时落在内存、流量、一致性和"漏配出口"四类问题上，风险最低，一轮回归即可验证。
2. **给 `HanimeVideo` / `HanimeInfo` 做稳定性改造，并先用 Compose 编译器稳定性报告量化（P2）**。这是详情页与卡片列表掉帧的根因，是通往"顶尖客户端滑动手感"的必经门槛；先量化再动手，避免凭感觉重构。
3. **把分页做成"单 Job + 请求标识"（P3）**。它是唯一一个普通用户在日常使用中必然撞到的正确性问题，修复成本远低于它的投诉成本。

## 明确不该动的 2 件事

1. **不要重构 `video/{contract,engine,ui,surface}` 的四层划分**。这是全项目唯一被代码事实（而非注释）证明为正确的部分：contract 层零平台/Compose/mediamp 依赖（实测 0 处），engine 层零 Compose（实测 0 处），`shared` 与 `video/ui` 零 mediamp 后端包引用（实测 0 处）。尤其 `PlaybackController.kt:150-154` 那条"把 position 挡在组合期外"的投影以及 `PlaybackUiState.kt:45-82` 对其取舍的完整说明，已经解决了最大的高频重组源，并有 `PlaybackUiStateTest` 兜底。为"更符合某个教科书模式"去重排它，收益接近零、风险极高。
2. **不要引入 Paging3 重写全部列表，也不要把 Ktor/OkHttp 双引擎收敛成单一方案**。`SearchViewModel.kt:45-68` 已用 500 条封顶 + `mergeSearchPage` 去重把内存与单页成本都压成常数，注释明确说这是"封顶 vs 切 Paging3"里选前者的既定取舍；`app/build.gradle.kts:172-177` 为断点续传下载保留 okhttp 同样是有据可查的取舍。这两处真正缺的是并发护栏（见 P3），不是推倒重来。
