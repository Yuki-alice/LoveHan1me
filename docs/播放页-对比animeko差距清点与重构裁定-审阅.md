# 审阅：《播放页 — 对比 animeko 的差距清点与重构裁定》

> 审阅方式：把原文每一条带 `文件:行` 的论断逐条对照真实代码复核（取证 2026-09-23）。
> 复核范围：本仓 `shared/src/{common,android,desktop,ios}Main`、`app`、`desktopApp`、`NOTICE`、`LICENSE`、
> `shared/build.gradle.kts`、`gradle/libs.versions.toml`；参考侧 `reference/animeko`。
>
> **结论先行：原文的裁定（§六）成立，可以据此决策；但支撑裁定的三条证据有误，其中一条会让
> 实施顺序做错。 §一 的"差距"叙事有措辞问题，需要按 §2 修正后再作为沟通材料。**

---

## 1. 复核通过的部分（可直接采信）

以下论断逐条核对无误，原文取证质量很高：

| 原文论断 | 复核结果 |
|---|---|
| `PlaybackEngine.kt:92-182` 19 个成员 | ✅ 计 19（93～181） |
| 其中 9 个是接口默认实现 | ✅ 109/118/126/129/140/148/155/164/177 共 9 个 |
| 桌面/iOS `PlaybackEngineFactory` 忽略 `kernel` | ✅ `desktop.kt:7-12`、`ios.kt:7-12`，注释亦自陈 |
| 桌面 `bufferedPositionMs = positionMs` | ✅ `DesktopMpvPlaybackEngine.kt:136` |
| iOS `bufferedPositionMs = positionMs` | ✅ `IosAVPlaybackEngine.kt:259`（注释说明是"如实给"而非造假） |
| mpv-android `isBuffering` 靠猜 | ✅ `MpvPlaybackEngine.kt:436`：`!paused && duration>0 && position==0` |
| 字幕/音轨代码完全不存在 | ✅ `feature/player`、`feature/video` 下 `subtitle` 只命中播放列表名/UI 文案/引导页副标题 |
| `PlaceholderPlaybackEngine.kt` 43 行、零引用 | ✅ 仅 `build/` 产物里有符号 |
| `PlaybackStallDetector.kt:65` 阈值 2500ms | ✅ `DEFAULT_THRESHOLD_MS = 2_500L` |
| `shared/build.gradle.kts:158-161` 桌面用 mediamp，版本 0.3.2 | ✅ 另有 `libs.versions.toml:66 mediamp = "0.3.2"` |
| `ExoSuperResolution.kt:100-104` 输出 == 输入 | ✅ 逐字一致，确为同分辨率锐化 |
| `MpvShaders.kt:16-42` PERFORMANCE 3 / QUALITY 5 | ✅ |
| `MpvShaders.ios.kt:4-6` 返回 null | ✅ |
| 位置发布频率 250/250/500ms | ✅ Exo `:409 PROGRESS_UPDATE_INTERVAL_MS = 250L`、System `:194`、mpv-android `:396`、iOS `:81 delay(500L)` |
| 三个大文件行数 935 / 1114 / 1000 | ✅ 精确匹配 |
| `VideoPlayerUi.kt:366-383` 6 键 `LaunchedEffect` | ✅ 6 个 key 全数命中 |
| 底栏空 `.clickable{}` 吃事件 | ✅ `VideoPlayerBottomBar.kt:178-183` |
| 滑条 120ms 节流 vs 手势路径无节流 | ✅ `VideoPlayerControls.kt:235 SLIDER_SEEK_THROTTLE_MS = 120L`；`VideoRouteHostScreen.kt:952-955` 每帧 `seekTo` |
| 预览帧注释自陈"等接上再做" | ✅ `VideoPlayerControls.kt:282-285` |
| `playerHitTarget` 只有一个调用点 | ✅ `VideoPlayerCenterControls.kt:255` |
| 底栏 48dp 装不下 | ✅ `VideoPlayerControls.kt:194-196` |
| 超分等级无设置键 / Exo level 跨 re-prepare | ✅ `VideoRouteHostScreen.kt:227`、`ExoPlaybackEngine.kt:53` |
| 桌面快捷键 Space/K、J/L、方向键、M、F、数字键 | ✅ `PlayerKeyboardShortcuts.desktop.kt:23-62` |
| 弹幕闭式几何 | ✅ `DanmakuEngine.kt:187-188`（`leftEdgeOf`） |
| 帧时钟吃原始 `withFrameNanos` | ✅ `DanmakuLayer.kt:75`；空闲 `:76 delay(IDLE_POLL_MILLIS=50L)`、`:182` 定义 50 |
| 测量缓存 512、每条 2 次 `drawText` | ✅ `:164 MEASURE_CACHE_SIZE = 512`；`:144`+`:151` |
| `PlayerBarRenderTest` 零断言 | ✅ 全文件 `assert` 出现 0 次 |
| `DanmakuLayerRenderTest:144-150` 只断言落墨 | ✅ `MIN_INKED_SAMPLES = 200`（`:261`） |
| `LogUtil.enabled` 默认 true | ✅ `LogUtil.kt:24` |
| 无正则黑名单 / 无每源偏移 / 无 REVERSE | ✅ 三处 grep 全空 |
| `clearEpisode`+`insertDanmaku` 无 `@Transaction` | ✅ `DanmakuDao.kt:72-79` 注释自陈（Dao 是接口装不了） |
| 弹幕是第 5 个 Room 库 | ✅ 共 5 个 `@Database` |
| animeko 专有符号在本仓源码中 0 命中 | ✅ 全仓仅 `docs/*.md` 与 `reference/animeko` 命中 |
| `NOTICE:78-79` 仍声明未含 animeko 源码 | ✅ |
| 不存在 `Animeko*` 移植文件 | ✅ `find shared app desktopApp -iname "*Animeko*"` 为空 |
| animeko 侧引用 | ✅ `FrameTimeSmoother.kt:66-91`（20~500Hz 钳制、相位回灌）、`StyledDanmaku.kt:77-82`（一次栅格化后 `drawImage`）、`FloatingDanmakuTrack.kt:182-190`/`:206-235`（防超车 + 二分插入）、`DanmakuHostState.kt:712-760` 且 `:839` 阈值确为 10s、`PlayerControllerState.kt:174`/`:204`、`Anime4kQualityEffects.kt:106` |
| §九 与《对齐与命中区规范》的冲突 | ✅ **成立**：那份文档 §修订（:12、:54、:57、:58）声称已逐字移植、已改 NOTICE，但仓库里既无 `AnimekoPlayerControllerBar.kt` / `AnimekoPlayerTopBar.kt`，`NOTICE:78` 也仍写着"未包含"，`LICENSE` 仍是 GPLv3 |

---

## 2. 需要修正的部分

### 2.1 【高】"预览帧需要引擎能力、不换底要自己写 5 份" —— 事实错误，且拖错了实施顺序

原文 §3.2 断言预览帧差在引擎、§六把切片 5 标为"**依赖 4（换底）**"。

实际：`PlaybackEngine.grabFrameArgb` **四个引擎全部已实现**，且 `supportsFrameCapture()` 四端全为 `true`：

| 引擎 | 实现 | 是否非破坏性 |
|---|---|---|
| Exo | `ExoPlaybackEngine.kt:219`（`PixelCopy` + `FrameReadyWaiter` 等帧） | ❌ 会 pause+seek |
| mpv-android | `MpvPlaybackEngine.kt:315` | ❌ |
| 桌面 mpv | `DesktopMpvPlaybackEngine.kt:369`，走 mediamp `features[FramePreview.Key].getPreviewFrame(pos, w, h)` | ✅ **非破坏性** |
| iOS | `IosAVPlaybackEngine.kt:185` | — |

并且这条链路**已经在用**：`VideoRouteHostScreen.kt:268`（GIF 录制）与 `:1031`（截图）都在调 `playbackController.grabFrameArgb`，UI 侧 `frameCaptureEnabled` 也已接线（`:925`）。

**后果**：
- 缺的只是滑条上的缩略图 UI（缓存 + 去抖 + 预热），**不是引擎能力**。
- §六 切片 5 应从"依赖 4 / M"改为"**无依赖 / S~M**"，可以和切片 1~3 并行，不必等换底。
- 附带：`VideoPlayerControls.kt:284-285` 那句"需要引擎提供抓帧…等接上再做"是**过期注释**，能力早已接上，应同步删掉，否则下一个人会从这里得出和本文一样的错误结论。

### 2.2 【高】"5 个引擎零覆盖" —— 事实错误

原文 §四 测试覆盖 4/10 的依据之一是"5 个引擎零覆盖"。实际：

- `iosTest/…/IosAVPlaybackEngineTest.kt`：≥6 个 `@Test`、11 处 `assertTrue`，覆盖加载起播 / fMP4 HLS / seek 落点 / 倍速收敛 / Error 携带 errorMessage / 真源播放。
- `desktopTest/…/DesktopMpvPlaybackLiveTest.kt`、`DesktopQualitySwitchLiveTest.kt`：桌面引擎实播测试。
- `commonTest/…/ComposePlaybackControllerTest.kt`（内置 fake engine）、`PlaybackStallDetectorTest.kt`、`FrameReadyWaiterTest.kt`。

**准确表述**：**Android 三个引擎（Exo / System / mpv-android）零覆盖**（需 instrumentation，确实没有）；iOS/桌面有集成级测试，但依赖模拟器/真机与网络，桌面 CI 跑不到。

评分可以维持 4/10 左右，但依据要换，否则会误导"给引擎补测试"的优先级——真正该补的是 Android 三端。

### 2.3 【中】"13.2k vs 22k" 无法复现，且是苹果比橘子

实测（同口径 `*.kt` 行数）：

| 侧 | 明细 | 合计 |
|---|---|---|
| 我方 | player+video+danmaku：common 13,090 + android 2,039 + desktop 949 + ios 856 | **16,934** |
| animeko | `video-player/src` 9,781 + `danmaku/**` 6,535 | **16,316** |

差 ~4%，不是 40%。

更关键的是**口径不对等**：animeko 的真正播放后端在独立仓库 `open-ani/mediamp`，**本工作区里根本没有**（`find reference -iname "*mediamp*"` 为空）。我方 5 个自研引擎算进了分子，animeko 的后端没进分母。

**建议**：删掉这句 LOC 对比。它不但站不住，还削弱了 §六① 的论证——而 §六① 本身是对的。

### 2.4 【中】"9 个 Anime4K 文件与 animeko 资产逐字相同" —— 部分错误

md5 逐字比对（`shared/.../shaders/*.glsl` vs `reference/animeko/utils/video-enhancement-shader-provider/.../shaders/`）：

- 7 个完全相同；
- `Anime4K_Clamp_Highlights.glsl` **仅尾随空白 + 末行缺换行**（语义相同，字节不同）；
- `Anime4K_Upscale_CNN_x2_S.glsl` **在 animeko 中不存在** —— animeko 只有 8 个 Anime4K 文件，我方 9 个。

结论（MIT bloc97 上游、可安全使用）仍然成立，但"逐字相同"应改为"同源、版本略有出入"。

顺带一个有意思的点：PERFORMANCE 档依赖的 `Upscale_CNN_x2_S` 恰恰是 animeko 没有的那个——所以这个档位配置不是从 animeko 抄来的。

### 2.5 【中】§一 的"空占位"叙事误导了真实缺陷的位置

原文："桌面和 iOS 各造一个空 `VideoSurface` 去满足 `attachSurface` 签名，两个平台的 `attachSurface/detachSurface` 都是空函数体"。

字面全对（`VideoSurface.desktop.kt:4` / `.ios.kt:4` 确为空类，`DesktopMpvPlaybackEngine.kt:537-538`、`IosAVPlaybackEngine.kt:150-151` 确为空体）。但漏掉了决定性事实：

- 桌面 `PlatformVideoSurface.desktop.kt:58` 渲染的是**真的** mediamp Skia 面（`MpvMediampPlayerSurface`）；
- iOS `PlatformVideoSurface.ios.kt:72-94` 渲染的是**真的** `AVPlayerLayer`；
- `DesktopMpvPlaybackEngine.kt:535-536` 注释写明："P5-2a Q1 裁定：**no-op 是正确设计**"。

所以这不是"没实现"，是**绕过抽象实现**。真正该写进文档的缺陷是：

> `PlatformVideoSurface` 必须把 `PlaybackEngine` **向下转型**成具体类型（desktop `:36 as? DesktopMpvPlaybackEngine`、iOS `:54 as? IosAVPlaybackEngine`），抽象在"渲染"这一层根本不存在——`VideoSurface` 只是个生命周期令牌。

这个说法比"空占位"更准确，也更有说服力（它解释了为什么加能力要在三端各写一遍）。

同理，§一"缓冲进度两端是假的"要限定：桌面 `isBuffering` 是**真的**（`DesktopMpvPlaybackEngine.kt:133` 直接取 mediamp `playerState.isBuffering`），假的只有 `bufferedPositionMs`。

### 2.6 【低】行号与遗漏

- §五 HIGH 写 `PlaybackEngine.kt:156` 起 9 个 `= {}` 成员 → 应为 **`:109` 起**（`setSuperResolution`）。`:156` 之后只剩 2 个成员。
- §四 漏了第三个零断言渲染测试：`desktopTest/…/LongPressSpeedRowRenderTest.kt`（`assert` 0 次）。
- §3.1"死代码"只点了 `PlaceholderPlaybackEngine`，但真正的污染源是那批**已与 M3 实现脱节的 KDoc**：`PlaybackEngineFactory.kt:6-8`（仍写"desktopMain / iosMain：返回占位引擎"）、`VideoSurface.kt:7`、`VideoSurface.desktop.kt:3`、`VideoSurface.ios.kt:3`。它们正是 §一"空占位"印象的来源，建议一并列入技术债。

---

## 3. 对裁定结论本身的意见

**§六①（播放内核换 mediamp）、③（弹幕不重写，只做两处外科手术）、④（UI 只优化）—— 证据充分，成立。**
②（控件状态机搬设计）也成立，与引擎无关这点判断正确。

两点补充：

1. **§五 CRITICAL（Exo 超分实为锐化）成立，且比原文说的更好修。** 9 个 MIT `.glsl` 已在仓内，mpv 路径在用；修 Android 不需要换底、不需要重新找素材，只差"按 `//!DESC` 切分 + 多 pass FBO"。这条应从"跟着换底走"里摘出来，独立优先做。

2. **§六①代价清单第一条是未验证的推断，不要当事实。** 原文："Android mpv 内核选项 mediamp 不提供（animeko 安卓只有 Exo）"。核对 `reference/animeko/app/shared/video-player/build.gradle.kts`：
   - `androidMain` → `mediamp.exoplayer`（:42）
   - `desktopMain` → `mediamp.mpv`（:53）
   - `appleMain` → `mediamp.avkit`（:56）

   mediamp **有** mpv 后端（animeko 桌面在用），animeko 安卓不选用 ≠ mediamp 在安卓不支持 mpv。这是"animeko 的选择"被当成了"mediamp 的能力边界"。应改为：**向 mediamp 上游确认是否存在 Android mpv 后端，再定这条代价是否成立**——它直接决定了"Android mpv 内核是产品刚需"这个反向条件是否触发。

---

## 4. 建议的修订动作（按优先级）

1. 改 §3.2 与 §六切片表：预览帧**不依赖换底**，切片 5 提前；同步删 `VideoPlayerControls.kt:284-285` 的过期注释。
2. 改 §四：把"5 个引擎零覆盖"换成"Android 三引擎零覆盖 + 引擎测试依赖真机/网络"。
3. 删 §一 的 LOC 对比句（16.9k vs 16.3k，且口径不对等）。
4. 改 §二：shader 资产"同源、版本略有出入"，`Upscale_CNN_x2_S` 为 animeko 所无。
5. 改 §一"空占位"为"渲染层抽象缺失（`PlatformVideoSurface` 向下转型）"，并把桌面 `isBuffering` 为真这半句补上。
6. 修 §五 `:156` → `:109`；补 `LongPressSpeedRowRenderTest` 与四份过期 KDoc。
7. §六①代价第一条标为"待上游确认"，不要作为既定代价。

---

## 5. 复核方式说明

可直接复现的关键命令：

```bash
# ① 抓帧能力是否存在（原文称不存在）
grep -rn "override suspend fun grabFrameArgb\|supportsFrameCapture(): Boolean" shared/src --include=*.kt

# ② 引擎测试是否存在（原文称 5 个引擎零覆盖）
find shared/src -path "*Test*" -name "*.kt" | grep -iE "player|engine|playback"

# ③ 规模（原文 13.2k / 22k）
find shared/src/*/kotlin/lovehan1me/feature/{player,video,danmaku} -name "*.kt" | xargs wc -l
find reference/animeko/app/shared/video-player/src reference/animeko/danmaku -name "*.kt" | xargs wc -l
find reference -maxdepth 3 -iname "*mediamp*"      # 期望：空（后端不在本仓）

# ④ shader 资产（原文称 9 个逐字相同）
AK=reference/animeko/utils/video-enhancement-shader-provider/src/commonMain/composeResources/files/shaders
for f in shared/src/commonMain/composeResources/files/shaders/*.glsl; do
  b=$(basename "$f"); [ -f "$AK/$b" ] && md5 -q "$f" | grep -q "$(md5 -q "$AK/$b")" \
    && echo "IDENTICAL $b" || echo "DIFF_OR_MISSING $b"
done
```
