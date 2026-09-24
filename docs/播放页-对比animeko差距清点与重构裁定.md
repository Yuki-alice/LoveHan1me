# 播放页 — 对比 animeko 的差距清点与重构裁定

> 起因：需要判定播放模块该"照 animeko 的成熟设计重构"还是"在现有基础上优化"。
> 本文**只以源码为准**：所有判断都能用给出的 `文件:行` 复核，参考项目路径为
> `reference/animeko`（open-ani/animeko 分支，AGPLv3）。
> 本文不引用 `docs/` 下任何既有结论 —— 见 §九，其中一条与代码事实冲突。
>
> 取证日期：2026-09-23

**路径简写**：`S/` = `shared/src/commonMain/kotlin/lovehan1me/`；
`SA/` `SD/` `SI/` = `shared/src/{android,desktop,ios}Main/kotlin/lovehan1me/`；
`AK/` = `reference/animeko/app/shared/video-player/src/`；`AKD/` = `reference/animeko/danmaku/`。

---

## 一、结论摘要

问题不在 UI 层，在 UI 层下面那条缝：`S/feature/player/PlaybackEngine.kt:92-182` 有 19 个成员，
其中 **9 个是 `= {}` / 返回 false 的接口默认实现**；桌面和 iOS 各造一个空 `VideoSurface`
去满足 `attachSurface` 签名，两个平台的 `attachSurface/detachSurface` 都是空函数体
（`SD/feature/player/PlatformVideoSurface.desktop.kt:45-49` + `DesktopMpvPlaybackEngine.kt:537-538`）。

这条缝导致下游每个能力在三端"要么真要么假"不可推测：缓冲进度两端是假的、字幕音轨根本没有、
拖动预览帧明确标注"等接上再做"、Android"超分辨率"是同分辨率锐化。animeko 的做法是把这条缝
整个交给 mediamp，自己只写薄适配器，所以它的 `video-player` 模块（含弹幕共 ~13.2k 行）
比我们的同等范围（~22k 行，含三端 source set）更小、能力更全。

**弹幕引擎是反例：我们的几何比它干净，不要重写。** 我们的位置是闭式解算
（`S/feature/danmaku/DanmakuEngine.kt:187-200`），每帧位置由 `nowMs` 直接算出，不存在累积漂移；
animeko 用增量二分插入 + 防超车 + 连续/清空两种 repopulate（`AKD/ui/…/FloatingDanmakuTrack.kt:206-235`），
复杂度是它的模型自己产生的。值得取的只有两样：它的帧时间 PLL 平滑，和"文本栅格化一次、之后每帧只
`drawImage`"。

裁定见 §六：**四层分别裁定，只有播放内核层值得重构。**

---

## 二、许可边界（这条限定了"借鉴"的方式）

| | 本项目 | animeko |
|---|---|---|
| 许可证 | GPLv3（`LICENSE`，`README.md:100`） | AGPLv3 |
| 归属声明 | `NOTICE:76-79` | — |

`NOTICE:78-79` 原文：**"No source code from animeko is included in this repository.
Any such reuse would be subject to AGPLv3 terms."** 已用符号名逐一复核，本仓 `shared/`
`app/` `desktopApp/` 中 animeko 专有符号（`PlayerControllerBar` / `SwipeSeekerState` /
`DanmakuHostState` / `StyledDanmaku` / `FrameTimeSmoother` / `ControllerVisibility`）
出现次数为 **0**；`MediaProgressSlider` / `VideoScaffold` 的命中全是注释里的引用
（`S/feature/video/VideoPlayerUi.kt:351-352`、`VideoPlayerControls.kt:308`）。

→ **可以搬设计、搬阈值、搬数据结构选型；不能搬代码。** 唯一例外：
`Anime4K_*.glsl` 是 MIT bloc97 上游，与 animeko 的 `.glsl` 资产逐字相同，本仓已经拥有
（§五.3）。

---

## 三、逐子系统对照

### 3.1 播放内核与状态

| 能力 | 本项目 | animeko | 定性 |
|---|---|---|---|
| 引擎 | 自研 5 个：Exo / mpv-android / System MediaPlayer / mpv-desktop(mediamp) / AVPlayer | mediamp 四端统一 + libass 薄包装 | 架构选择 |
| 内核选择 | 桌面/iOS **忽略** `kernel` 参数（`SD/…/PlaybackEngineFactory.desktop.kt:7-12`、`SI/…/PlaybackEngineFactory.ios.kt:7-12`） | DI 注册，每端一个实现 | — |
| 位置发布频率 | Exo/mpv-android 250ms、iOS **500ms 轮询**、桌面事件驱动无定时器 | mediamp 统一 | — |
| 缓冲进度 | Android 真实；**桌面/iOS `bufferedPositionMs = positionMs`**（`SD/DesktopMpvPlaybackEngine.kt:136`、`SI/IosAVPlaybackEngine.kt:251-259`） | `Buffering` feature + 连续缓存块分段绘制 | **假的** |
| `isBuffering` | mpv-android 用 `!paused && duration>0 && position==0` 猜（`SA/MpvPlaybackEngine.kt:436`，且 `cache-pause=no`）→ 中途重新缓冲永不上报 | 同上 | **假的** |
| 字幕 / 音轨 | **代码完全不存在**（grep `subtitle\|trackselection\|gettracks\|slang\|libass` 在 `feature/player`、`feature/video` 下只命中 UI 文案） | `TrackGroup.select` + `AK/…/ui/progress/{SubtitleSwitcher,AudioSwitcher}.kt`，Android 硬字幕走 libass | **缺失** |
| 画面比例 | 四套并行实现：mpv `panscan`/`video-aspect-override`、Exo `setVideoScalingMode`、AVPlayer `videoGravity`（在 `SI/…/PlatformVideoSurface.ios.kt:68-70` 用协程反向订阅 state）、桌面 `dwidth/dheight` | `AK/…/ui/VideoAspectControllerState.kt` 包 mediamp `VideoAspectRatio`，只有 FIT/STRETCH/CROP | 重复劳动 |
| 统计面板 | 无 | `AK/…/ui/PlayerStatsOverlay.kt` 21 字段快照，桌面 1s 轮询 mpv handle | 缺失 |
| 死代码 | `S/feature/player/PlaceholderPlaybackEngine.kt`（43 行，全仓零引用） | — | 可删 |

我们自己的代码已经写下诊断：`ComposePlaybackController.kt` 注释"引擎的 `isBuffering`
三端语义不一致"，并因此引入跨平台兜底 `PlaybackStallDetector`（阈值 2500ms，`S/feature/player/PlaybackStallDetector.kt:65`）。
**一个用于纠正引擎语义的组件，本身就是引擎层不对齐的证据。**

### 3.2 控件、手势、进度条

| 项 | 本项目 | animeko |
|---|---|---|
| 可见性模型 | 局部布尔 + **6 键 `LaunchedEffect`**：`S/feature/video/VideoPlayerUi.kt:366-383` | 请求方集合 → `derivedStateOf` 优先级；`rememberAlwaysOnRequester` 在 `DisposableEffect` 里自撤销（`AK/…/ui/PlayerControllerState.kt:204, :312`） |
| 拖动时换控件 | 靠底栏一个空 `.clickable{}` 吃事件（`VideoPlayerBottomBar.kt:178-183`） | `InlineSliderOnly` 预设，注释："直接拖动进度条必须使用此状态，避免替换正在接收触摸事件的组件"（`PlayerControllerState.kt:174`） |
| 手势/点击仲裁 | **无边缘排除**，拖拽层是全屏 Box；触屏鼠标共用一套行为 | 挂载时用 `enabled=` 决定（组合期就定死命中路径），触屏/鼠标两套行为表 `GestureFamily`（`AK/…/ui/gesture/PlayerGestureHost.kt:470-484, :602, :747`） |
| 命中区 | `playerHitTarget` 定义了 48dp 机制，**实际只在一个调用点使用**（`VideoPlayerCenterControls.kt:255`）；底栏图标 24/25dp，代码注释说明 48dp 盒子放不下（`VideoPlayerControls.kt:194-196`） | M3 `IconButton` 默认 enforcement，全仓从不关闭 |
| seek 节流 | 进度条路径 120ms 节流；**横向手势路径每帧无节流 `seekTo`**（`VideoRouteHostScreen.kt:952-955`） | 预览与提交分离：拖动期间只写 `previewPositionRatio`，**引擎仅在 `onPreviewFinished` 被 seek 一次** |
| 拖动预览帧 | **时间气泡 only，无帧**。`VideoPlayerControls.kt:281-286` 注释自陈：见 `frameCaptureEnabled` —— 等接上再做 | `AK/…/ui/progress/MediaProgressFramePreview.kt`：LruCache(8) + 2s 栅格键去重 + 50ms 去抖 + 切集预热 + **BT 源未下载区域不请求**（避免抢播放带宽，`MediaProgressSlider.kt:539-549`） |
| 上滑取消 seek | ✅ 48dp 阈值，回退到拖起点的值 | ✅ 144dp，且按 density 有测试钉住 |
| 键盘 | 桌面专属 actual（`SD/feature/video/PlayerKeyboardShortcuts.desktop.kt:23-62`）：Space/K、J/L、↑↓、M、F、数字键 | 多一套：长按方向键 >200ms 转倍速快进、A/D 循环倍速、B 弹幕、I 统计、Enter 在 `onPreviewKeyEvent` 吞掉（同一节点带 `combinedClickable`，获焦后 Enter 会被当点击） |
| IME / 焦点 | 无焦点状态机 | `PlayerFocusState` 带 owner token，弹幕输入框获焦即暂停 + 请求常显 |

预览帧差距**不是 UI 差距，是引擎差距**：它需要引擎提供 `FramePreview` 解码能力。
不换底就要自己写 5 份。

### 3.3 弹幕

| 项 | 本项目 | animeko | 判定 |
|---|---|---|---|
| 位置解算 | **闭式**：`viewport.widthPx * (1 - (nowMs-startMs)/traverseMs)`（`DanmakuEngine.kt:187-200`），每帧零状态突变 | 增量插入 + `distanceX` 纯函数 + 重锚 | **我们更优** |
| 碰撞 | 三个 `LongArray` 占用表（滚动/顶/底独立），`pickFreeLane` 取 least-busy；满载 `return false` **静默丢弃并计数** | 位置有序列表 + 二分插入 + **防超车**（比较双方离轨时间，`FloatingDanmakuTrack.kt:182-190`） | 各有所长 |
| 帧时钟 | 直接吃 `withFrameNanos` 原始值；空闲时 `delay(50)` 降到 ~20Hz | `withFrameNanos` → **PLL 平滑**再推进动画时钟 | **缺**（见下） |
| 文本渲染 | 每帧 `textMeasurer.measure`（512 条缓存）+ 每条 **2 次 `drawText`**（1.5dp 描边 + 填充，`DanmakuLayer.kt:91-108, :133-157`） | 首次绘制栅格化成 `ImageBitmap`，之后每帧只 `drawImage`（`AKD/ui/…/StyledDanmaku.kt:77-82`） | **可取** |
| 时间基准 | `DanmakuPositionTracker` 锚点外推 + `seekGeneration` 单调计数，`SEEK_THRESHOLD_MS=1500`、`MAX_LOOKAHEAD_MS=1000` | `TimeBasedDanmakuSession`：`lastIndex` + `binarySearchBy`，阈 3s / 回看 20s / 上限 40 条 | 思路同源，我们的更简 |
| Repopulate | 快照 reset + `remerge()` | 额外有"连续 vs 清空"判定：屏幕上最新条目与目标时间差 ≤10s 时按 key 做 diff，保留对象身份（`AKD/ui/…/DanmakuHostState.kt:712-760`），有 `assertSame` 测试钉住 | 值得借鉴（避免 seek 微调时弹幕整屏闪） |
| 位置类型 | SCROLL / TOP / BOTTOM。**REVERSE 全仓不存在** | TOP / BOTTOM / NORMAL，同样无反向 | 双方都无 |
| 过滤 | **无正则黑名单、无每源时间偏移** | `danmakuRegexFilterList` + `DanmakuOriginConfig.shiftMillis`（每来源独立开关与偏移） | 缺失 |
| 来源 | dandanplay + 站内评论映射；凭据 XOR 代理，未配置即休眠 | ≥3 provider 合并（Ani + dandanplay + Local 缓存 provider） | 缺失（架构位已留） |
| 数据层 | 独立第 5 个 Room 库；TTL 12h、失败冷却 10min、**失败时宁取陈旧缓存**、`TOMBSTONE_CID` 记空集。⚠️ `clearEpisode` + `insertDanmaku` **无 `@Transaction`**（代码注释自陈），中间崩溃丢一页 | `DanmakuCacheStrategy` 控制是否回写 | 我们更细致 |

`FrameTimeSmoother` 解决的问题对我们**完全成立**：Skiko 上 `withFrameNanos` 给的是 EDT
回调开始时刻而非 vsync，原始 delta 会在 12/21/14ms 之间抖，肉眼是弹幕左右抽动。
算法是简化 PLL（`AKD/ui/…/FrameTimeSmoother.kt:66-91`）：EMA 估计周期（钳制 20~500Hz）、
相位误差以 0.1 增益回灌、校正量上限 0.2×周期、≥250ms 空档直接透传。
**掉帧被摊平而非一次跳完，且长期漂移有界。**

### 3.4 超分辨率

**本项目的 Android 路径不是超分。** `SA/feature/player/ExoSuperResolution.kt:100-104`：

```kotlin
override fun configure(inputWidth: Int, inputHeight: Int): Size {
    texelWidth = 1f / inputWidth.coerceAtLeast(1)
    texelHeight = 1f / inputHeight.coerceAtLeast(1)
    return Size(inputWidth, inputHeight)      // 输出 == 输入，没有任何放大
}
```

PERFORMANCE 是 5 抽头 luma 反锐化，QUALITY 是 13 抽头 + 噪声门。文件头注释自陈"9 个 mpv
`.glsl` 无法复用、目标是先调通链路"。异常路径双重 `try/catch` 静默降到 OFF。

| 平台 | 本项目 | animeko |
|---|---|---|
| Android/Exo | ❌ 同分辨率锐化 | ✅ 真：按 `//!DESC` 切官方 `.glsl` → 注入模板 `.frag` → 多 pass FP16 ping-pong；QUALITY `configure` **返回 `Size(input*2, input*2)`**（`AK/androidMain/…/Anime4kQualityEffects.kt:106`），再接一个 ewa_lanczossharp 缩到视口 |
| Android/mpv | ✅ `glsl-shaders`，等级列表见 `S/core/util/MpvShaders.kt:16-42`（PERFORMANCE 3 / QUALITY 5 个文件），⚠️ **不记住等级、`load()` 后不重挂** | — |
| 桌面 | ✅ 最完整：`glsl-shaders` + `scale/dscale/cscale` = ewa_lanczossharp / sigmoid，QUALITY→PERFORMANCE→OFF 回退链 | ✅ Win+mac；**Linux 返回 null** |
| iOS | ❌ 无（`SI/core/util/MpvShaders.ios.kt:4-6` 直接 `= null`，注释说明 AVPlayer 原生管线不支持挂自定义 shader；AVPlayer 引擎不覆盖 `supportsSuperResolution()`，菜单自动隐藏） | ❌ Apple 整体返回 null |

**关键事实：本仓 `shared/src/commonMain/composeResources/files/shaders/` 里已经躺着 9 个
Anime4K CNN 文件，和 animeko 的资产逐字相同（MIT）。** mpv 路径已经在用；只有 Exo 路径
绕开它们手写了一个近似。→ 修 Android 超分的成本主要是"按 `//!DESC` 切分 + 多 pass FBO"，
不是找素材。

其他两处：
- 等级是 `var superResolutionIndex by remember { mutableStateOf(0) }`（`VideoRouteHostScreen.kt:227`），
  **无设置键**；而 Exo 引擎的 level 跨 re-prepare 存活（`ExoPlaybackEngine.kt:53`）→ 菜单可能显示
  "关闭"而特效仍挂着。加键时注意本仓首启即全量落盘，改默认值对老存档无效，需读侧回落。
- **两侧都没有任何 FPS / GPU 时间 / 输出像素的实测**。"超分"的性能代价目前无人知道。

---

## 四、健康度评分

| 维度 | 分数 | 依据 |
|---|---|---|
| 正确性风险 | 6/10 | 未发现崩溃级缺陷，但三处语义造假（缓冲进度、`isBuffering`、Android 超分） |
| 类型安全 | 5/10 | 9 个默认空实现使"不支持"与"忘了实现"不可区分；`VideoSurface` 三端语义不同 |
| 可观测性 | 7/10 | `PlayerTrace` 设计好，但桌面/iOS 常开（`LogUtil.enabled` 默认 true，仅 Android 按 DEBUG 关），桌面错误文本是常量 `"mpv playback error"` |
| 测试覆盖 | 4/10 | 纯逻辑测试质量高；5 个引擎与 1000 行控件文件零覆盖；渲染测试只断言"有墨" |
| 可维护性 | 5/10 | 每个能力要写 3~5 份；控件可见性是布尔汤；画面比例四套并行实现 |

---

## 五、按优先级的整改项

### CRITICAL
- **[正确性]** `ExoSuperResolution.kt:100-104` — "超分辨率"实为同分辨率锐化。
  取真正 Anime4K CNN 链（资产已在本仓，MIT），或把菜单文案改成"锐化"。二选一，不要维持现状。

### HIGH
- **[架构]** `PlaybackEngine.kt:156` 起 9 个 `= {}` 成员 + `VideoSurface.{desktop,ios}.kt` 空占位类
  —— 换底到 mediamp，见 §六①。
- **[类型安全]** `DesktopMpvPlaybackEngine.kt:136`、`IosAVPlaybackEngine.kt:251-259`
  假 `bufferedPositionMs`；`MpvPlaybackEngine.kt:436` 猜出来的 `isBuffering`。
- **[测试]** `PlayerBarRenderTest.kt` 生成 PNG **零断言**（全文件 `assert` 出现 0 次）；
  `DanmakuLayerRenderTest.kt:144-150` 仅断言 `inked > MIN_INKED_SAMPLES(200)`。
  这两个文件制造了"控件与弹幕有测试"的错觉。

### MEDIUM
- **[可维护性]** 可见性 → 请求方集合；手势 → 挂载期 `enabled=` + 触屏/鼠标两套表。
- **[正确性]** `VideoRouteHostScreen.kt:952-955` 无节流 seek，与进度条 120ms 节流并存。
- **[一致性]** 超分等级落设置 + 引擎重挂等级（Android mpv 的 `load()` 后不重挂同源问题）。
- **[可观测性]** 桌面错误文本常量；`SystemPlaybackEngine.seekTo` 位置截断为 Int（`:83`）。
- **[缺失]** 每源时间偏移、正则黑名单（这两项与引擎无关，可独立做）。

### LOW
- 底栏图标铺 `playerHitTarget`；统计面板；反向弹幕。

---

## 六、重构 vs 优化：分层裁定

### ① 播放内核层 —— **换底（唯一值得重构的地方）**

理由链：§三对照表里所有"缺失"和"假的"，追到底都是同一条缝。
`shared/build.gradle.kts:158-161` 已经在桌面用 `mediamp-mpv-desktop` 0.3.2；
animeko 证明 `mediamp-exoplayer` / `mediamp-avkit` 后端在生产可用。换底后缓冲、轨道、
`FramePreview`、`VideoAspectRatio`、`PlaybackSpeed`、截图全从同一个 `player.features[...]`
取，5 个引擎里 4 个可删。

**这不是"重写播放器"，是替换一条已存在、已被三端实现的接口的实现方式；UI 层可以一行不改。**

代价（决策前必须接受）：
- **Android mpv 内核选项 mediamp 不提供**（animeko 安卓只有 Exo）。换底 = 放弃它，
  或让它成为抽象的唯一例外。
- 开始跟上游 0.3.2 的版本节奏走；`settings.gradle.kts` 里那批 `exclusiveContent`
  说明依赖面在国内镜像下已经很脆，多两个 mediamp artifact 就多两个坑位。
- Android 的 `EchGateDataSource` 管道要重接。可行性有旁证：animeko 就是用
  `mediaSourceInterceptor = pipeline::intercept` 挂进去的。

**如果 Android mpv 内核是产品刚需，本条裁定要反过来。**

### ② 控件状态机层 —— **重写小范围，搬设计**
请求方集合 + 挂载期仲裁是纯模式，几十行，与引擎无关，**不必等 ①**。

### ③ 弹幕引擎层 —— **不重写，只做两处外科手术**
闭式几何是资产，别丢。只取：(a) `FrameTimeSmoother` 的 PLL；(b) 文本→`ImageBitmap`
一次栅格化 + 每帧 `drawImage`。合计 <200 行，且可套它的纯状态测试范式。

### ④ UI 布局与皮肤层 —— **只优化，不重构**
`VideoPlayerUi.kt`(935) + `VideoRouteHostScreen.kt`(1114) + `VideoPlayerControls.kt`(1000)
是三端跑通且有对齐规范的东西。animeko 这层反而很薄，因为重活在 mediamp 里 ——
向它"重构"等于把工作搬到自己身上。

### 建议切片顺序

| 序 | 切片 | 依赖 | 规模 |
|---|---|---|---|
| 1 | 弹幕 PLL 帧平滑 + 位图缓存（含纯状态测试） | 无 | S |
| 2 | 可见性请求方集合 + 手势挂载期仲裁 | 无 | M |
| 3 | 超分诚实化（真 CNN 链 或 改文案） | 无 | M |
| 4 | `PlaybackEngine` → mediamp（Android + iOS），删 3 引擎 | 决策 | L~XL |
| 5 | 预览帧管线 + 拖动只提交时 seek | **依赖 4** | M |
| 6 | 字幕/音轨选择 | **依赖 4** | M（不换底则 XL） |
| 7 | 每源时间偏移 + 正则黑名单 | 无 | S~M |

1~3 与 4 完全解耦、风险最低，先做可以先验证"借设计"的路线走得通。

---

## 七、技术债清单

| 项 | 规模 |
|---|---|
| Android Exo 真 Anime4K 链 | M |
| `PlaybackEngine` → mediamp 换底 | L~XL |
| 可见性/手势模型改造 | S~M |
| 弹幕 PLL + 位图缓存 | S |
| 轨道选择 UI + 引擎能力 | M（换底后）/ XL（不换底） |
| 黑名单 + 每源偏移 + 反向弹幕 | S~M |
| 弹幕/控件纯状态测试改造 | M |
| `DanmakuDao.clearEpisode`+`insertDanmaku` 加 `@Transaction` | S |
| 删 `PlaceholderPlaybackEngine` | S |

---

## 八、不要动的部分（本仓优于或等于参考项目）

- `DanmakuPositionTracker` 锚点外推 + `seekGeneration`，引擎对时间呈纯函数，**结构上不可能漂移**。
- `PlaybackStallDetector` 显式阈值 + 6 个边界测试；`FrameReadyWaiter` 的 tolerance+settle 双门。
- `PlayerTrace` 的会话归因 span。
- `DanmakuRepository` 的 TTL / 失败冷却 / **宁取陈旧缓存** 三段策略与 `TOMBSTONE_CID`。
- 弹幕闭式几何与满载丢弃计数。
- 注释里大量"为什么"级别的诚实说明，包括主动标注"未在真机验证" —— 保持这个习惯。

---

## 九、与本仓既有文档的冲突

`docs/播放页-对齐与命中区规范.md` 顶部有一条 2026-09-16 修订，称已改为"**直接移植
animeko 源码**"。按 §二 的符号清点与 `NOTICE:78-79` 的声明，**仓库中不存在 animeko 源码**，
该修订与代码事实不符（若真要执行，需先把 `LICENSE` 升到 AGPLv3，否则违反 NOTICE 声明）。
本文按"搬设计不搬代码"给结论。

`docs/播放页-三端布局对比与控件优化方案.md` 与本文范围不重叠（那份是像素测量与间距规范），
可继续作为布局依据；本文只管引擎缝、状态模型、弹幕与增强。
