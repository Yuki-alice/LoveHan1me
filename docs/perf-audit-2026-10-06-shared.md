# Compose 性能审计报告 — `:shared` / `:video:*`

> 工具链：Kotlin 2.4.10 · Compose Multiplatform 1.12.0 · AGP 9.2.1 · Gradle 9.4.1
> 审计日期：2026-10-06
> 方法：**静态审计**（见下方"方法偏差"）

---

## 0. 方法偏差（必须披露）

`auditing-compose-performance` 要求 Phase 2 读 Compose 编译器报告
（`composables.txt` / `classes.txt`）来定位 `restartable but not skippable`。
本次**未能产出该报告**：

- 在 `shared/build.gradle.kts` 里加 `composeCompiler { metricsDestination / reportsDestination }`
  并强制 `--rerun` 后，`:shared:compileKotlinDesktop` 在 5m11s 报
  `Internal compiler error.` —— CMP 1.12 的编译器在开启 metrics/reports 时崩溃。
- 该 `composeCompiler { }` 块已**完整回退**（全仓检索 `composeCompiler` 为 0 命中），
  仓库回到可构建状态。

替代手段：按技能里的反模式清单做定向检索（热状态 Modifier、`collectAsState`、
`Modifier.composed`、缺 `key`/`contentType`、组合期写 State、`while(true)` 轮询、
主题层每帧失效），再逐个打开高频组合项人工判读。

**因此本报告没有帧时间数字。** 下面每一条都写明"预期影响"与"为什么"，
但**没有实测前后对比**——不能当作已验证的收益，只能当作已论证的修复。
要做真实验证需要：release + R8 + 真机 + Macrobenchmark，本次环境不具备。

---

## 1. 范围

- **在范围内**：`shared/`、`video/`（自有代码，829 个 Kotlin 文件 / 94,342 行）
- **排除**：`reference/`（`Han1meViewer-main`、`mediamp-0.5.0`、`animeko-main`）
  —— 上游 vendored 代码，改了会被下次同步冲掉，且不是我们的组合树。

---

## 2. 已修复项

### P0-1　`VideoCardItem` 每卡片现造封面渐变　→ 提到文件级常量

`feature/.../ui/component/VideoCardItem.kt`

全应用最高频的组合项（首页 / 搜索 / 播放列表 / 历史 / 相关，一屏几十个）。
原先在组合体里 `Brush.verticalGradient(listOf(...))`，滚动时每新建一张卡片就
分配一个 `List<Color>` + 一个 `Brush`。它是纯常量，提到文件级 `CoverScrimBrush`
只造一次。

### P0-2　组合期反向写 ViewModel　→ 移入 `LaunchedEffect`

`feature/home/myplaylist/MyplayListBottomSheet.kt`

原先在组合体里直接
`if (listCode.isNotEmpty()) vm.setListInfo(listCode, playListTitle)`。
组合阶段写 State 属"反向写"：每次重组都触发一次，且写进快照会让**本次重组作废重来**。
改为 `LaunchedEffect(listCode, playListTitle)`，只在入参真变时执行一次。

### P0-3　滚动位置每像素回写 ViewModel　→ 加 `distinctUntilChanged`

- `feature/home/myplaylist/MyplayListBottomSheet.kt`（播放列表弹层）
- `feature/video/CommentScreen.kt`（评论页）
- `feature/home/myplaylist/PlaylistContent.kt`

这三处都是 `snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }`
直接 `collect` 后写回 ViewModel。**没有去重 = 每滚动一像素写一次**，写回去又触发
本页重组 —— 这是评论页 / 播放列表页滚动掉帧的主因。补 `.distinctUntilChanged()`
后只在位置真变时写。

### P0-4　分页判定连发重复请求　→ 先映射成布尔再去重

`MyplayListBottomSheet.kt` / `PlaylistContent.kt`

`snapshotFlow { gridState.layoutInfo }` 的问题更重：`layoutInfo` **每滚动一像素都是
新对象**，直接 collect 等于每帧跑一遍分页判断；触底那一小段滚动里会连续发几十个
`true`，反复打 `OnLoadMore`。改为先映射成 `是否接近末尾` 的布尔、
`.distinctUntilChanged()` 后再判断，只在翻转时往下走。

### P1-1　`collectAsState` → `collectAsStateWithLifecycle`

| 文件 | 流数量 |
| --- | --- |
| `feature/home/WatchHistoryScreen.kt` | 5 |
| `feature/home/myplaylist/MyPlayListScreen.kt` | 2 |
| `feature/home/myplaylist/MyplayListBottomSheet.kt` | 4 |
| `iosMain/.../PlatformSettingsRoutes.ios.kt` | 1 |

这些流全部来自 ViewModel（组合体之外）。用 `collectAsState` 时页面退到后台
**仍在收集**，上游的取历史 / 分页查询跟着空转。
`collectAsStateWithLifecycle` 把收集绑到 `STARTED`，后台即停。
改完后全仓检索 `.collectAsState()` 在自有代码里**零命中**。

### P1-2　主题过渡期间每帧新造 ColorScheme 实例　→ `remember`

`ui/theme/Theme.kt`、`ui/theme/SubjectTheme.kt`

`animateColorScheme` 用 48 个 `animateColorAsState` 做 300ms 过渡，这 48 个
`.value` 都在 `HanimeTheme` 的作用域里读，所以过渡期间每帧本函数都会重组。
原本每帧还会顺带做一次 `colorScheme.amoled()`（一次 `copy()`）→ 新实例。
`ColorScheme` 没重写 `equals`（按引用比），于是 `ConfigureSystemBars`
**每帧都认为入参变了而重组**。
把 AMOLED 叠加 `remember` 起来后，过渡期间传下去的是同一个实例，子项才能真的跳过。

> 注意：`boardColorScheme` 内部已经 `remember` 了 materialkolor 的全量色算，
> 所以并没有"每帧重算调色板"，这点已核实。色算不需要动。

### P2-1　Lazy 列表补 `key` / `contentType`

| 文件 | 补的内容 |
| --- | --- |
| `MyplayListBottomSheet.kt` | `key = { _, item -> item.videoCode }` |
| `PlaylistContent.kt` | `key = { it.listCode }` |
| `feature/home/subscription/SubscriptionContent.kt` | `contentType = { "subscription_video" }` |
| `feature/home/artist/ArtistDetailScreen.kt` ×3 | `contentType`（`"video"` / `"playlist"`） |
| `feature/settings/OpenSourceLicensesScreen.kt` | `contentType = { "license" }` |
| `feature/settings/dialog/HomeCategoryLayoutDialog.kt` | `contentType = { "home_category" }` |

`ArtistDetailScreen` 这一处最值得说：**三个 tab 共用同一个 `LazyVerticalGrid`**
（作品视频 / 播放列表 / chips 通栏 + 列表）。不标 `contentType` 的话切 tab 时
槽位会跨类型复用，已缓存的组合全丢、逐个重建。

---

## 3. 已核查、确认无需改动

这几处打开前都怀疑有问题，读下来是**已经写对的**，不改：

- `ui/component/lazy/AnimatedLazy.kt` — 入场动画用
  `Modifier.graphicsLayer { alpha = ...; scaleX/Y = ... }`，是正确的 lambda 形式
  （在 Draw 阶段读状态，不触发重组）。scope 包装层已正确转发 `key`/`contentType`/`span`。
- `video/ui/.../feature/danmaku/DanmakuLayer.kt` — 已经做过重度优化：
  每条弹幕栅格化一次进 `DanmakuRasterCache`、`fillStyle`/`borderStyle`/`stub` 都
  `remember`、`clipToBounds`、空闲时降到 20Hz、帧时间用非 State 的
  `FrameTimeSmoother`、空集合时零 draw call。
- `video/ui/.../progress/MediaProgressSlider.kt` — `PlayerProgressSliderState` 用
  `() -> Long` lambda 传值 + `derivedStateOf` + `collectAsStateWithLifecycle`，
  是"延迟读取"的标准写法。
- `feature/video/VideoPlayerShell.kt` 的 `DanmakuVisibility` —
  `Modifier.graphicsLayer { alpha = danmakuAlpha }`，正确。
- `core/domain/model/VideoItemType.kt` — 已标 `@Immutable`，`VideoCardItem` 可跳过。
- `Modifier.composed` —— 全仓零命中，无需做 Modifier.Node 迁移。
- `Modifier.alpha/scale/rotate/offset(...)` —— 只出现常量值（`0f`/`0.8f`/`0.7f`），
  没有"把热状态读进 Composition"的违规。
- `derivedStateOf` —— 6 处用法都包在 `remember` 里，判定逻辑正确。

---

## 4. 已知但未修（记录，带理由）

### 设置页 2 秒轮询

`jvmMain/.../NetworkSettingsRoute.kt:155`、`iosMain/.../PlatformSettingsRoutes.ios.kt:107`

```kotlin
var tick by remember { mutableIntStateOf(0) }
LaunchedEffect(Unit) { while (true) { delay(2_000L); tick++ } }
```

`tick` 在外层组合体里读 → **整个设置页每 2 秒重组一次**，并且
`recentEgressRows(50)` / `buildEgressExport(...)` 每 2 秒各建一次列表，
即使网关状态根本没变。

**未修的理由**：这是网络诊断页，用户是**主动盯着**这一页看实时三态和事件流的，
2s 刷新是产品需求；且该页内容基本静态，实测前无法证明它是瓶颈。
**要修的话**：把轮询挪到一个只包住状态卡的小组合项里（让整页不再跟着重组），
或改成 `flow { while(true){ emit(...) } }.collectAsStateWithLifecycle()` 以便
后台自动停。等有真机帧数据再说。

### 未启用 Baseline Profile

`app/build.gradle.kts` release 已开 `isMinifyEnabled` + `shrinkResources` + R8，
**但没有 baseline profile**。这是启动与首屏渲染最典型的收益点，但要新增一个
`:benchmark` 模块 + Macrobenchmark，并且必须用**真机 / 模拟器 + release + R8**
跑一遍生成规则——本次环境（Windows、无 Android 构建验证）做不到，
硬加一个编不出、跑不了的模块只会污染仓库。列为后续项。

---

## 5. 验证状态

- `:shared:compileKotlinDesktop` → **BUILD SUCCESSFUL**（仅剩既有的
  `monthNumber` / `dayOfMonth` 弃用告警和多余安全调用告警，与本次改动无关）
- `iosMain` 的改动（`PlatformSettingsRoutes.ios.kt`）**未编译验证** ——
  该目标需要 macOS + Xcode，Windows 上编不了。改动是单点替换
  （`collectAsState` → `collectAsStateWithLifecycle` + import 互换），
  已在 `jvmMain` 同款代码上验证通过。
- **没有运行时 / 帧时间验证**。见第 0 节。

---

## 6. 下一步（按性价比排序）

1. 真机 + release + R8 跑一遍 Macrobenchmark，拿到首页滚动 / 播放页 / 主题切换
   三条基线，再回头核对本报告第 2 节的"预期影响"是否成立。
2. 补 Baseline Profile。
3. 解决 CMP 1.12 编译器 metrics/reports 崩溃（升级版本或换 JVM 参数），
   拿到 `composables.txt` 后把"跳过率"做成可回归的指标。
4. 设置页 2 秒轮询收窄作用域。
