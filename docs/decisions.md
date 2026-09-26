# 决策与边界

只有用户拍过板的东西进这份文件。每条格式：**决定** —— 失效条件 —— 重认日期。
重认日期指用户对"这条还是不是你的意思"的逐条确认；2026-09-25 是文档体系重建那次的重认。
新规则：agent 若发现某条与代码冲突，**当场来问用户**，不要自行推测它是否还生效。

## A. 范围外（结构性不做，不是"还没做"）

- **BT 下载 / 数据源 SPI 插件平台 / 60 模块拆分 / Bangumi 同步 / 放送时间表与评分体系** 不做 ——
  失效条件：用户明确改判 —— 重认 2026-09-25
- **UI 不做六件**：底栏整体抬到 60dp、播放页布局重构、每源独立时间偏移、正则屏蔽词、
  OP/ED 跳过、锁屏手势 —— 失效条件：用户点名要做其中某件（`OP/ED 跳过` 特别注意：
  animeko 有整套实现，不要把它当我们的缺口补上）—— 重认 2026-09-25
- **字幕轨 / 外挂字幕 / 音轨选择 / 音频偏移 UI 永久不做**（源为单音轨 + 内嵌硬字幕）——
  失效条件：源结构变化出现多音轨/软字幕 —— 重认 2026-09-25
  现核：`player/` 与 `shared/` 内 `TrackSelection|subtitleTracks|audioTracks` 零实现
- **桌面不内嵌浏览器（JCEF/KCEF）**，永久不考虑 —— 失效条件：无 —— 重认 2026-09-25

## B. 播放器与画面

- **向 animeko 靠拢的正确落点是播放底座 mediamp，不是抄它的 UI** ——
  失效条件：mediamp 停止维护 —— 重认 2026-09-25
- **改判补充（用户 2026-09-26 拍板）：分层与状态契约可以搬，页面结构与外观不搬** ——
  可搬：api/ui 分离、领域层不依赖 UI 层、请求值与真值同快照分列、
  能力声明取代接口默认实现、控件可见性的请求者令牌仲裁。
  不可搬：播放页布局与视觉外观（仍受上面"不得回退四处"约束）——
  失效条件：用户点名改 —— 重认 2026-09-26
- **mediamp 内核与超分实现本次不重做，只重做组织形态** ——
  三端后端已落地（`video/engine/build.gradle.kts:38` 起），超分双端已实现
  （桌面 `video/engine/src/desktopMain/kotlin/lovehan1me/feature/player/DesktopMpvPlaybackEngine.kt:353`）——
  失效条件：用户改判 —— 重认 2026-09-26
- **视频模块四分 `:video:contract` / `:video:engine` / `:video:ui` / `:video:surface`，硬边界是 contract 与 ui 都不得碰 mediamp 类型** ——
  依据：引擎对 mediamp 是 `implementation`（`video/engine/build.gradle.kts:38`）而非 `api`，
  抬成 `api` 会漏进 iOS 导出框架；现核 `shared/src/` 搜 `org.openani.mediamp` 零命中。
  这条边界已由源码扫描测试守死（不许回潮）：
  `video/contract/src/desktopTest/kotlin/lovehan1me/video/contract/ModuleLayeringTest.kt:19` ——
  失效条件：用户改判，或 iOS 不再需要 framework export —— 重认 2026-09-26
- **播放器控件已整体迁进 `:video:ui`**（原"仍留 `:shared`、本轮只做前置解耦"那条按自身失效条件作废）——
  迁移集合：`VideoPlayerUi` / `VideoPlayerControls` / `VideoPlayerStateCards` / `VideoPlayerCenterControls` /
  `VideoPlayerBackdrop` / `VideoPlayerBottomBar` / `VideoPlayerTopBar` / `PlayerBatteryIndicator` 八个
  （`video/ui/src/commonMain/kotlin/lovehan1me/feature/video/`）+ 三端电量 actual，
  外加**只在控件内被调用**的 `PlayerWheelZoom` / `PlayerKeyboardShortcuts` 及其三端 actual
  （`video/ui/src/commonMain/kotlin/lovehan1me/feature/video/PlayerWheelZoom.kt:17`、
  `video/ui/src/commonMain/kotlin/lovehan1me/feature/video/PlayerKeyboardShortcuts.kt:41`）。
  后两者原判"控件对其零引用"是**误判**：`VideoPlayerUi` 直接调用 `playerKeyboardShortcuts` / `playerWheelZoom`
  / `rememberPlayerKeyActions`（`video/ui/src/commonMain/kotlin/lovehan1me/feature/video/VideoPlayerUi.kt:465`）——
  搬控件必须连带搬它们，否则 `:video:ui` 要反向依赖 `:shared`。
  设施去处：常量与尺寸 `PlayerTokens`（`video/ui/src/commonMain/kotlin/lovehan1me/video/ui/PlayerTokens.kt:19`）、
  按钮族（`video/ui/src/commonMain/kotlin/lovehan1me/video/ui/PlayerButtons.kt:28`）、
  封面模糊 expect/actual（`video/ui/src/commonMain/kotlin/lovehan1me/video/ui/PlayerVisualEffect.kt:10`）、
  `formatPlaybackTime` 下沉契约层（`video/contract/src/commonMain/kotlin/lovehan1me/video/contract/PlaybackTime.kt:9`）。
  受业务牵连的三项由 `:shared` 注入/传参：触感 `LocalPlayerHaptic`
  （`video/ui/src/commonMain/kotlin/lovehan1me/video/ui/PlayerHaptic.kt:12`）、
  诊断出口 `LocalPlayerDiagnostics`（`video/ui/src/commonMain/kotlin/lovehan1me/video/ui/PlayerDiagnostics.kt:23`）、
  封面 `cover` 槽位（`shared/src/commonMain/kotlin/lovehan1me/feature/video/VideoShellContent.kt:214`）。
  **GIF 捕获链路已迁进 `:video:ui`**（原「`GifCaptureDialog` 有意留在 `:shared`」按自身失效条件作废，
  用户 2026-09-26 拍板）：弹窗 `video/ui/src/commonMain/kotlin/lovehan1me/feature/video/GifCaptureDialog.kt:70`
  + `core/util/gif/` 六个纯算法文件（包名 `lovehan1me.core.util.gif` 有意不变，两侧 import 零改动）
  + 测试（`commonTest` 四类加辅助 `MinimalGifDecoder`，`desktopTest` 的 `Gif89aEncoderTest` 用了 `java.io.File` 故必须留在 desktop）。
  平台「保存 / 分享」边**改参数注入**：`:video:ui` 自持 `GifExportOutcome`，`:shared` 在调用点把
  `MediaExportOutcome` 映射进去（`shared/src/commonMain/kotlin/lovehan1me/feature/video/VideoRouteHostScreen.kt:1084`）；
  `exportMediaAndShare` / `MediaExportOutcome` 仍留 `:shared`
  （`shared/src/commonMain/kotlin/lovehan1me/core/platform/MediaExport.kt:36`），因为截图路径共用同一套。
  文件名时间戳由 `kotlin.time.Clock` 提供（不得见 `:video:engine` 的 `currentEpochMillis`），
  `image/gif` 这个 MIME 随平台导出边落到 `:shared` 调用点。
  迁走后 `:shared` 对 gif 包只剩一条代码依赖：`ScreenshotCapturer` 用 `FrameScaler`
  （`shared/src/commonMain/kotlin/lovehan1me/core/util/image/ScreenshotCapturer.kt:6`），
  靠 `:shared` 的 `api(project(":video:ui"))` 解析，该 import 未改。
  **`PlayerTrace` 有意留在 `:shared`**（用户 2026-09-26 重认）：`:video:ui` 对它零代码引用，
  它的出口本就被 `LocalPlayerDiagnostics` 槽位隔开；搬它要连带解决 `LogUtil`（在 engine）、
  `PlatformLock`（在 `:shared`，expect + 三端 actual）、`currentEpochMillis`（在 engine）三个跨模块依赖，
  且 `internal fun resetForTest()` 的跨模块可见性会打断 `:shared` 的 `WatchFetchPerfLiveTest`。
  它的渲染测试 `PlayerBarRenderTest` 已在 `:video:ui` 并自建极小主题（本模块不得反向依赖 `:shared`）。
  同轮更正引擎侧一段不实注释（只改注释、未改行为）：`IosFrameCapture` 的 KDoc 曾声称就地复用
  `FrameScaler`，实际代码是尺寸不等时直返源尺寸、缩放由 `GifRecorder.scaleToPlan` 补做
  （`video/engine/src/iosMain/kotlin/lovehan1me/feature/player/IosFrameCapture.kt:161`）。
  同轮清掉 `:shared` 里随之成死代码的播放器 token（用户 2026-09-26 拍板）：`OverlayAlpha` 整块、
  `PlayerSizes` 整块、`Sizes.controlXS` / `Sizes.controlS`。删的依据是**全仓 grep 而非"看起来像播放器的"**：
  只剩 `Sizes.controlM` 与 `Overlay.onScrim` 还被 `:shared` 引用
  （`shared/src/commonMain/kotlin/lovehan1me/ui/theme/Defaults.kt:63`、
  `shared/src/commonMain/kotlin/lovehan1me/ui/theme/Defaults.kt:74`），其余成员在 Defaults.kt 之外零命中。
  失效条件：控件或 GIF 链路再长出需要 `:shared` UI / 平台设施的新边，或用户改判 `PlayerTrace` 的归属 —— 重认 2026-09-26
- **共用资源在 `:video:ui` 存副本，不新建公共资源模块**（用户 2026-09-26 拍板）——
  依据：播放器控件资源共 30 个 drawable + 26 条字符串，其中 22 drawable + 24 字符串除控件外零引用，
  已从 `:shared` 删除；余下 8 个 drawable（`ic_arrow_back` / `ic_fullscreen` / `ic_light_mode` /
  `ic_lock` / `ic_pause` / `ic_play_arrow` / `ic_refresh` / `ic_skip`）与 `cancel` / `retry` 两条字符串
  另有 `:shared` 页面在用，故两侧各一份：`:video:ui` 复制、`:shared` 原样保留。
  代价：同名资源两份，改视觉要改两处。
  失效条件：共用资源多到维护不动，或需要单一真相 —— 重认 2026-09-26
- **桌面继续用 mpv 是长期决定**（需要 Anime4K 真链 + 画面调节），已知代价写在
  `docs/evidence/` 之前不写、也不许被当作 bug 重提换底 —— 失效条件：用户改判 —— 重认 2026-09-25
- **截图 / 录 GIF：代码保留，入口先藏；预览帧滑条不做** ——
  现核 `video/engine/src/commonMain/kotlin/lovehan1me/feature/player/PlaybackEngine.kt:120` 默认 false，
  仅 `video/engine/src/desktopMain/kotlin/lovehan1me/feature/player/DesktopMpvPlaybackEngine.kt:312` override true
  —— 失效条件：接上真实用途时 —— 重认 2026-09-25
- **超分致错时吞异常降级为 OFF，绝不断播** —— 失效条件：用户改判 —— 重认 2026-09-25
- **分辨率门控：渲染面尺寸未知时不门控**（宁花算力，不静默关掉功能）——
  现核 `video/engine/src/androidMain/kotlin/lovehan1me/feature/player/ExoSuperResolution.kt:61` ——
  失效条件：用户改判为"未知就保守关掉" —— 重认 2026-09-25
- **四处不得因"对齐 animeko"而回退**：双皮肤结构、右栏配色跟随主题、
  时间文字描边 + `tnum`、亮度能力探测 —— 失效条件：用户点名改 —— 重认 2026-09-25

## C. 弹幕

- **主路径 = 人工选集 + 持久化记住关联**；自动匹配只在唯一高置信时启用，有歧义一律不猜 ——
  失效条件：命中率前提被重新实测推翻 —— 重认 2026-09-25
- **v1 不做六件**：发送弹幕（需 JWT）、文件哈希匹配、本地自发弹幕池、正则屏蔽词、
  多源叠加、弹幕统计面板 —— 失效条件：用户逐条例外放行 —— 重认 2026-09-25
- **凭据构建期注入、打开即用**：真实值只在全机不入库的 `local.properties`，
  由 `shared/build.gradle.kts:278` 的 `generateDanmakuCredentials` 生成到 `build/generated/`；
  设置页那两个输入框是**覆盖**入口，不是必填项 —— 失效条件：用户改判 —— 重认 2026-09-19
- **弹幕引擎不重写**：闭式几何解算是资产，只从 animeko 取两处外科手术
  （帧平滑 + 文本一次栅格化）。两处均已落地且解算件未改：帧平滑加在绘制层外侧
  `video/ui/src/commonMain/kotlin/lovehan1me/feature/danmaku/FrameTimeSmoother.kt:44`
  （输出与真实帧时刻的偏差有"一帧"硬上界），文本一次栅格化把描边与填色烤进位图
  `video/ui/src/commonMain/kotlin/lovehan1me/feature/danmaku/DanmakuRasterCache.kt:82`；
  `DanmakuPositionTracker` 一行未动 —— 失效条件：实测证明确实解不动 —— 重认 2026-09-25
- **不要动四件**（改动前必须先取证，不许当缺陷顺手修）：
  `DanmakuPositionTracker` + `seekGeneration`、`PlaybackStallDetector`、
  `PlayerTrace` 会话归因、`DanmakuRepository` 的 TTL / 失败冷却 / 宁取陈旧缓存 / `TOMBSTONE_CID`
  —— 失效条件：用户点名 —— 重认 2026-09-25

## D. 出口与站点

- **不自建托管反向代理**（流量经第三方服务器）。
  例外且必须写清：**本地 ECH 网关 `echgate/` 不属于这条禁区**——流量仍从用户本机出、
  没有中转，它解决的是 SNI 层问题，与反代不是一件事 —— 失效条件：用户改判 —— 重认 2026-09-25
- **不得对用户承诺 SOCKS 也能播**：播放器那一段只吃 HTTP 代理，SOCKS 档必须明确告知
  "这一段是直连" —— 失效条件：上游支持逐请求代理 —— 重认 2026-09-25
- **站点范围五条**：手动 cookie 流程永久保留为降级路径 / 不扩第三站 / 不做站点插件化 /
  iOS 不追付费分发 / comic 子站延后单列不阻塞主线 —— 失效条件：用户逐条改判 —— 重认 2026-09-25

## E. 代码来源

- **禁止文件级复制**：允许照着参考实现的思路重写同名逻辑，但包名、变量名、结构必须自己重做。
  `NOTICE` 里"未包含 animeko 源码"那句必须与实际一致——若发生函数级移植，就把它改写为
  "参考实现"，不许留着过期声明 —— 失效条件：用户改判 —— 重认 2026-09-25
- **clean-room 闸门**：`tools/license_similarity_scan.py` 存在就是给你跑的，
  涉及参考项目改动后跑一遍。不预设相似度阈值（旧文档那个数字无法核实出处，已作废）——
  失效条件：脚本被删除或不再覆盖源码目录 —— 重认 2026-09-25

## F. 会被反复重提的实现纪律

- **改任何字段的默认值语义，必须同时回答"老存档里已落盘的旧值怎么办"**：
  读侧针对性回落，或显式迁移。首启会把全部键（含空串、含 false）逐个落盘，
  此后默认值对老装机无效 —— 现核 `shared/src/commonMain/kotlin/lovehan1me/data/datastore/DataStoreManager.kt:75`，
  读侧回落样例 `shared/src/commonMain/kotlin/lovehan1me/data/datastore/DataStoreManager.kt:297`
  —— 失效条件：存档改为按需写键（不再首启全量落盘）之后 —— 重认 2026-09-25
- **超分档位已落盘**（2026-09-26 立项，当轮实施完成；原"未做"条目按自身失效条件删除）——
  读侧对"超出当前引擎可选档位"的存档值一律回落到 OFF，回答上面那条的"老存档旧值怎么办"：
  `video/contract/src/commonMain/kotlin/lovehan1me/video/contract/VideoEnhancementController.kt:58`
  —— 失效条件：用户改判档位回落规则 —— 重认 2026-09-26
