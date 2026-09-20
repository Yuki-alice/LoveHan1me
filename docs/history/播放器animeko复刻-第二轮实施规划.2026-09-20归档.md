# 播放器 UI 第二轮重构：animeko 全盘 1:1 复刻实施规划

> 制定：2026-09-17（通读 animeko `video-player` 模块 9.7k 行 + 本项目 video 包 10.7k 行后定稿）
> 决策来源：用户 2026-09-17 拍板（4 项）
> 1. **视觉 = 全盘 1:1**：M3 原生控件形制、无毛玻璃、纯色 Surface 指示器、右侧竖排浮钮、右侧滑出无圆角无动画面板。毛玻璃卡 / B 站风皮肤 / chip 药丸全部移除。
> 2. **独有功能全留，改 animeko 形态承载**：清晰度 / 超分 / 截图 / GIF 保留，砍主页键与电量。
> 3. **范围 = 只重写播放器覆层**：新建 `feature/player/ui` 包，删除重写 7 个文件；`VideoShellContent` / `VideoRouteHostScreen` 只改接线，不动结构。
> 4. **手势 = 按输入设备分叉 + 保留三分区双击**。
> 5. **参考方式 = 直接借鉴 AGPLv3 实现**，项目许可转 AGPLv3。

**与既有文档的关系**
- 本文**取代** `docs/播放页-animeko控件对照与复刻方案.md` 的「§九 复刻路线」（那一份的对照结论仍然有效，保留作参考）。
- 本文**不动** `docs/播放器重写定稿.md` 的 S1–S5（引擎所有权 / 渲染面单路径）——那些是「崩不崩」的问题，本文只解决「像不像」。**本次红线：不许碰引擎层。**

---

## 一、现状盘点（第一轮已落地了什么，还剩什么）

### 1.1 第一轮（2026-09-15）已经落地的 9 项

| # | 已落地 | 文件 | 本文处置 |
|---|---|---|---|
| A | 底栏两态行结构 | `VideoPlayerBottomBar.kt` | **保形状，换皮肤**（毛玻璃 → 裸 M3） |
| B | `PlayerBarSkin` 皮肤与行结构解耦 | 同上 | **删除**（不再有双皮肤） |
| C | 进度条时间预览气泡 | `VideoPlayerControls.kt` | **重写**（接入 animeko 浮层规格） |
| D | 上滑取消 seek（48dp） | 同上 | **重写为 `TouchSeekState` 状态机**（144dp） |
| E | 图标/轨道数值对齐（32/26/24/5/22/12） | `ui/theme/Defaults.kt` | **再对齐一轮**（见 §五数值表） |
| F | 时间文字等宽 + 描边 | `VideoPlayerBottomBar.kt` | **重写为 `MediaProgressIndicatorText`**（占位防抖 + 剩余时间） |
| G | hover 挂点移到顶/底栏容器 | `VideoPlayerUi.kt` | **重写为 `hoverToRequestAlwaysOn` 请求者** |
| H | 锁钮抽成具名槽位 | `VideoPlayerCenterControls.kt` | **重写为 `gestureLock` 槽位** |
| I | `expanded` 与 `bilibiliStyle` 分成两个参数 | 同上 | **`bilibiliStyle` 删除**，只留 `expanded` |

### 1.2 剩下没做的（本次全部要做）

| 缺口 | 来源 | 阶段 |
|---|---|---|
| 无 `VideoScaffold` 抽象，9 个覆盖层平铺在 857 行主函数 | §1.1 | P1 |
| 无 `PlayerControllerState`（现在是三布尔 + 6 个 `LaunchedEffect` + 5 个特例） | §2 | P2 |
| 无 `keepLayoutWhenHidden`（`alpha(0f)` + 吞指针） | §4.5 | P1 |
| 无 detached slider（控件隐藏时横滑的独立进度条） | §2.4 | P5 |
| 进度条无预览帧、无缓存分块语义、无 thumb 跟随时间 | §4 | P4 |
| 手势 HUD 显示**百分比**而非秒数/音量值/倍速值 | §六 | P5 |
| 无 `GestureFamily` 输入设备分叉 | §A.6 | P6 |
| 无上/下滑切全屏 | §A.7 | P6 |
| 侧栏是 `ModalBottomSheet`，不是右侧滑出面板 | §D | P7 |
| 倍速是底部选项列表，不是就地 Popup + Slider | §E | P7 |
| 顶栏是自绘玻璃卡，不是 M3 `TopAppBar(Transparent)` | §C | P5 |
| 无 `PlayerFullscreenState`（`isFullscreen` + `request` 两处分离） | §H.5 | P0 |
| 顶栏 hover 是死代码（创建了 source 但没传，真 Bug） | 现状报告 F-1 | P1（天然消失） |

---

## 二、目标文件布局

新建包 **`shared/src/commonMain/kotlin/lovehan1me/feature/player/ui/`**（与引擎 `feature/player/` 同模块，UI 与引擎归位到一处）。

```
feature/player/ui/
├── PlayerControllerState.kt          ~330   ControllerVisibility / 三请求者列表 / keepLayoutWhenHidden
├── PlayerFullscreenState.kt          ~90    isFullscreen + request(target) 绑成一个对象
├── PlaybackSpeedControllerState.kt   ~100   previewSpeed / commitSpeed / 量化 0.25
├── VideoAspectRatioControllerState.kt ~70   画面比例（Fit/Crop/Fill）
├── VideoPlayerScaffold.kt            ~420   14 具名槽位 + 统一 insets + scrim
├── VideoLoadingIndicator.kt          ~60    24dp 圈 + 描边文案
├── VideoSideSheets.kt                ~240   右侧滑出 + 内部导航栈（None 哨兵）
├── PlayerScreenshotButton.kt         ~70    rhsButtons：截图浮钮
│
├── top/
│   └── PlayerTopBar.kt               ~120   M3 TopAppBar(Transparent) + 视频增强/数据源/more
│
├── progress/
│   ├── PlayerControllerBar.kt        ~700   两态底栏 + 内置控件（Playback/Next/Audio/Speed/Options/Fullscreen）
│   ├── PlayerProgressSliderState.kt  ~180   拖动预览 / 乐观值 / 节流（从 UI 主函数下沉）
│   ├── MediaProgressSlider.kt        ~700   4 层自绘轨道 + thumb + 预览浮层
│   ├── TouchSeekState.kt             ~120   上滑取消状态机
│   ├── MediaProgressIndicatorText.kt ~160   描边 + 占位防抖 + 倍速剩余时间
│   ├── MediaProgressFramePreview.kt  ~160   LRU 8 帧 / 2s 网格 / 接 supportsFrameCapture
│   └── VerticalSlider.kt             ~75    音量竖条（旋转 Slider）
│
└── gesture/
    ├── PlayerGestureHost.kt          ~700   手势组合 + GestureIndicator
    ├── GestureFamily.kt              ~60    TOUCH / MOUSE 语义表
    ├── SwipeSeekerState.kt           ~270   横滑 seek（像素→秒）+ 上滑取消
    ├── FastSkipState.kt              ~200   长按 3x + 三分区双击跳片
    ├── SwipeLevelControl.kt          ~150   音量 / 亮度 LevelController
    ├── SwipeToFullscreen.kt          ~75    上滑 64dp 切全屏
    ├── GestureLock.kt                ~195   锁钮 + LockedScreenGestureHost
    └── PlayerKeyboardShortcuts.kt    ~110   合并现有 79 行 + 平台 actual
```

**删除（7 个文件，整份重写）**

| 文件 | 行数 | 去向 |
|---|---|---|
| `feature/video/VideoPlayerUi.kt` | 857 | → `ui/VideoPlayerScaffold.kt` + 装配宿主（留在 `video/` 下的薄 `PlayerOverlayHost`） |
| `feature/video/VideoPlayerControls.kt` | 860 | → `ui/progress/MediaProgressSlider.kt` + `ui/gesture/*` |
| `feature/video/VideoPlayerBottomBar.kt` | 737 | → `ui/progress/PlayerControllerBar.kt` |
| `feature/video/VideoPlayerTopBar.kt` | 513 | → `ui/top/PlayerTopBar.kt` |
| `feature/video/VideoPlayerCenterControls.kt` | 324 | → 中央播放键并入 `PlayerControllerBar` 之上；锁钮并入 `ui/gesture/GestureLock.kt` |
| `feature/video/VideoPlayerStateCards.kt` | 352 | Resume/结束/重试卡 → 保留为 `ui/PlayerStateCards.kt`（移包，内容精简） |
| `feature/video/VideoPlayerBackdrop.kt` | 260 | → scrim 移到 Scaffold；封面 + 缓冲圈保留为 `ui/VideoPlayerBackdrop.kt`（移包） |

**保留但改接线**
`VideoShellContent.kt`（288）、`VideoRouteHostScreen.kt`（1010）、`GifCaptureDialog.kt`（192）、`PlayerTrace.kt`（125）、`VideoViewModel.kt`（524）。

**删除的功能点**：主页键、电量指示器、`bilibiliStyle` 全部分支、B 站风弹幕占位输入框。

---

## 三、目标骨架（`VideoPlayerScaffold`）

严格照搬 animeko `VideoScaffold.kt:81-369`，14 个具名槽位，一层 Box + 全部覆盖层 `matchParentSize`：

```
BoxWithConstraints(expanded ? fillMaxHeight : fillMaxWidth)
└─ Box(!maintainAspectRatio ? fillMaxSize : fillMaxWidth().height(maxWidth*9/16))
   ├─ Box.matchParentSize                    → video()              （唯一调用点，不套 insets）
   ├─ Box.matchParentSize                    → danmakuHost()        （我们无弹幕 → 空槽，保留）
   ├─ BoxWithConstraints.matchParentSize     → gestureHost()        （拿 maxWidth 算 swipe 比例）
   ├─ Box.matchParentSize.padding(12)        → playerStatsOverlay() （我们无 stats → 空槽）
   ├─ Box { Column {
   │     AniFade(topBar || inlineSliderOnly)  → 顶 scrim 0.72→0.45→0
   │     Box.weight(1f)
   │     AniFade(bottomBar)                   → 底 scrim 0→0.45→0.72
   │     AniFade(detachedSlider)
   │   } + AniFade(floatingBottomEnd).align(BottomEnd) }
   ├─ Column(End insets) { rhsButtons() ; gestureLock() }   spacedBy(8), padding(end=16)
   ├─ Box { leftBottomTips（下方 50% 垂直居中）}
   ├─ Box.matchParentSize(Center)            → floatingMessage()
   ├─ Box.matchParentSize(Center)            → framePreviewOverlay()
   └─ Box.matchParentSize                    → rhsSheet()
```

关键点（照抄）：
- **scrim 由 Scaffold 画，不由 bar 画**（`VideoScaffold.kt:176-182` / `:254-260`）。
- **insets 单一真相源**：Scaffold 收一个 `contentWindowInsets`，按层分发；视频层不套、弹幕层只 Vertical、顶栏 Horizontal+Top、底栏 Horizontal+Bottom、右栏 End。
- **`maintainAspectRatio = !expanded`**：全屏只改尺寸不改结构。
- **`keepLayoutWhenHidden(hidden)`** = `alpha(0f)` + `clearAndSetSemantics{}` + `awaitPointerEvent(Initial).consume()`，不是 `if (!visible)` 摘子树。

---

## 四、控制器状态机（`PlayerControllerState`）

六个布尔位 + 三个 `SnapshotStateList<Any>` 请求者列表 + `derivedStateOf` 优先级（照搬 `PlayerControllerState.kt:126-304`）：

```kotlin
visibility = when {
    inlineProgressSliderRequesters 非空 → InlineSliderOnly   // 拖底栏进度条中
    alwaysOnRequests 非空               → Visible            // hover / 下拉 / 侧栏
    fullVisible                         → Visible            // 用户单击切出来的
    progressBarRequesters 非空          → DetachedSliderOnly // 隐藏时横滑 seek
    else                                → Invisible
}
```

**必须迁移的 5 个现有特例 → 请求/取消请求**：

| 现有特例 | 迁移为 |
|---|---|
| `activeSidePanel != null` | `rememberAlwaysOnRequester(state, "sideSheet")` |
| `gestureType != null` | 手势层 `request()` / `cancelRequest()` |
| `isProgressGestureActive` | `setRequestInlineProgressSlider(this)` |
| `isControlsHovered` | `Modifier.hoverToRequestAlwaysOn(requester)` 挂**顶/底栏容器** |
| `isLocked` | `ControllerVisibility.withGestureLocked(locked)` 纯函数派生 |

一次性修掉现状报告 F-1：**顶栏 hover 死代码**（创建了 source 却没传参）在搬家后天然消失。

---

## 五、数值总表（animeko 原值 → 我们的目标值）

| 项 | animeko | 我们现状 | 目标 |
|---|---|---|---|
| 自动隐藏超时 | 3 s（触摸）/ 鼠标移停 3 s | 5 s | **3 s** |
| 锁定后收起 | 2 s | 3 s | **2 s** |
| 顶栏载体 | M3 `TopAppBar`，`containerColor=Transparent` | 自绘玻璃卡 | **M3 TopAppBar** |
| 顶栏图标 | 24 dp | 24 dp | 24 dp ✅ |
| 底栏播放/暂停图标 | **36 dp** | 32 dp | **36 dp** |
| 底栏下一集图标 | 36 dp | 32 dp | **36 dp** |
| 底栏全屏图标 | **32 dp** | 26 dp | **32 dp** |
| 底栏内边距 | expanded 8/4；非 4/2 | 18/4 | **4/2 与 8/4** |
| 底栏行间距 | expanded 8 / 非 4 | 12 | **8 / 4** |
| 顶 scrim | `0→黑.72` / `.32→.45` / `1→0` | 120 dp 两段 | **三段停靠点** |
| 底 scrim | 镜像（`0→0` / `.68→.45` / `1→.72`） | 180 dp 两段 | **镜像三段** |
| 底栏内容前置 Spacer | expanded 12 / 非 6 | — | **12 / 6** |
| 进度条容器 | 22–24 dp | 22 dp | **22 dp** |
| 进度轨道 | 6 dp | 5 dp | **6 dp** |
| thumb | 自绘 `12×24`，`r=8` | glow + 白圆 12 | **自绘 12×24 r=8** |
| 轨道圆角 | 胶囊（pill） | pill | pill ✅ |
| 时间文字 | `labelMedium`（Scaffold 外层 `labelSmall`） | `labelSmall` | **labelMedium** |
| 时间描边 | `Stroke(miter=3f, width=2f, join=Round)` 深灰 | 同字两遍 `字号/15` | **`Stroke(width=2f)`** |
| 时间占位 | `"88:88 / 88:88"` alpha 0 | 无 | **加占位防抖** |
| 剩余时间 | `(总-当前)/倍速` | 无 | **加** |
| 指示器容器 | `Surface` alpha `.8` 圆角 4dp 阴影 1dp padding 12/8 高 36 | 玻璃卡 170×190 | **Surface 36dp 行** |
| 指示器图标 | 36 dp | 36 dp | 36 dp ✅ |
| 指示器文字 | `bodyLarge + SemiBold` | `headlineSmall` | **`bodyLarge SemiBold`** |
| 指示器进度条 | 宽 80 dp，track `onSurface.5` | 8dp 高 Linear | **80 dp 宽** |
| 指示器入场/出场 | `fadeIn(spring StiffnessMedium)` / `fadeOut(tween 500)` + 保留末帧 | 默认 fade | **照抄** |
| 指示器时长 | 播放暂停 700 / 其余 500 | 700 | **700 / 500** |
| 长按触发延迟 | 500 ms | `longPressTimeoutMillis` | **500 ms** |
| 长按倍率 | 3.0x（显示 `3.00x`） | `longPressSpeedTime` | **3.0x + 数值** |
| 横滑满宽秒数 | 97 s（线性无阻尼） | 百分比 + sensitivity 4.0..0.5 | **97 s 线性**（sensitivity 保留为倍率） |
| 上滑取消阈值 | 144 dp | 48 dp | **144 dp** |
| 竖拖分档 | `((maxH-100dp)/40).coerceAtLeast(2dp)` | `height*0.8f` | **40 档** |
| 音量步进 | 滑动 .05 / 键盘 .10 / Shift .01 | .05 / .01 | **照抄** |
| 亮度步进 | .01 | `height*0.8f` | **.01** |
| 上下滑切全屏 | 64 dp | 无 | **64 dp** |
| 键盘左右 | ±5 s（长按 200ms 后加速） | ±10 s | **±5 s + 长按加速** |
| 侧栏宽度 | `widthIn(300,400)`，实际 `maxW*0.28` | 底部 sheet | **右侧滑出 `maxW*0.28`** |
| 侧栏背景 | `surfaceContainerHigh` | 白卡 | **`surfaceContainerHigh`** |
| 侧栏圆角/阴影/遮罩/动画 | **全无**（`fadeIn(snap())`） | 圆角 + 动画 | **全无** |
| 倍速弹层 | Popup 280dp 圆角 16 阴影 8，强制暗色，间距 8 | 底部选项列表 | **照抄** |
| 倍速档位 | 0.5–2.5，步进 0.25（9 档） | 0.5–3.0（11 档） | **0.5–3.0 步长 0.25（11 档）**（保留我们范围） |
| 倍速按钮文案 | 1.0 → "倍速"，否则 `1.25x` | chip `1.0x` | **照抄** |
| 锁钮 | `Surface` 圆角 16，底 `background.05`，边 `outline.618`，`Lock`(primary)/`LockOpen`(白) | `FilledIconButton` 48 黑 .45 | **照抄** |
| 加载圈 | `24 dp`，`strokeWidth 3` | 自有 | **24 / 3** |
| 加载文案外层 | `labelSmall` + `onBackground.618` | — | **照抄** |
| 预览浮层 | 有帧 `160×90` 圆角 12；无帧 `CircleShape`；padding 16/12 | 小胶囊 | **照抄** |
| 帧区 | `160×90` 圆角 8，底 `Black.3` | 无 | **加** |
| 帧预览 | LRU 8 帧 / 2 s 网格 / debounce 50 ms | 无 | **加** |

---

## 六、手势语义（`GestureFamily` 分叉 + 三分区双击）

照搬 animeko 的分叉表，但**双击保留我们已有的三分区**：

```kotlin
enum class GestureFamily(
    val clickToPauseResume: Boolean,      // 单击 = 播放/暂停（否则 = 切控件）
    val clickToToggleController: Boolean, // 单击 = 切控件
    val autoHideController: Boolean,      // 单击后是否启动自动隐藏
) {
    TOUCH(false, true,  true),            // 单击切控件，3s 后自动隐藏
    MOUSE(true,  false, false),           // 单击播放/暂停，不自动隐藏（靠鼠标移动 + 3s）
}
```

**双击（三分区，两端共用）**

| 区域 | 行为 |
|---|---|
| 左 1/3 | 快退 `DOUBLE_TAP_SEEK_STEP_MS`（10 s） |
| 右 1/3 | 快进 10 s |
| 中间 1/3 | **TOUCH → 播放/暂停；MOUSE → 切换全屏** |

> 缩放 ≠ 1 时的「双击归零」优先级**保留**（现有行为，比 animeko 好用）。

**其余手势（完全照搬 animeko 数值）**：长按 500 ms 触发 3.0x 并显示 `3.00x`、横滑 97 s/满宽 + 上滑 144 dp 取消、左右 1/3 竖拖音量/亮度、中间 1/3 上滑 64 dp 切全屏、锁定后点屏唤锁钮 2 s 收起。

**保留我们更严谨的两点**（见 §八）：亮度能力探测（无能力不接管手势）、失败重试卡。

---

## 七、独有功能的 animeko 形态归位

| 能力 | animeko 对应位 | 落地形态 |
|---|---|---|
| **清晰度/画质** | 「数据源」`Icons.Rounded.DisplaySettings` | 底栏 `endActions` 文本按钮 + `DropdownMenu`（复用 `OptionsSwitcher`） |
| **超分** | 「视频增强」`Icons.Rounded.AutoAwesome` + `VideoEnhancementDropdown` | `topBar` actions，仅 `expanded` 显示；桌面按 `supportsSuperResolution()` 隐藏（记忆约定：能力判断走引擎 API，不走名字匹配） |
| **截图** | `rhsButtons` → `ScreenshotButton` | 右侧竖排浮钮，仅 `expanded` 且平台支持时挂载 |
| **GIF 录制** | TopBar「更多」`DropdownMenu` | `more` 菜单项，弹 `GifCaptureDialog`（保留现有 192 行实现） |
| Resume / 结束卡 / 重试卡 | animeko 无 | 保留为 `ui/PlayerStateCards.kt` 独立覆盖层（我们更好） |
| 主页键 / 电量 | animeko 无 | **删除** |
| 弹幕 | `danmakuHost` + 底栏输入框 | **槽位保留，内容空**（无数据层）；底栏 `expanded` 中间位改放「清晰度」文本按钮 |

---

## 八、刻意偏离清单（**需你确认**，这 5 处我不照搬）

| # | 偏离 | 理由 |
|---|---|---|
| 1 | **视频比例**：保留按 `videoWidth/videoHeight` 自适应，不硬编码 16:9 | animeko 非全屏强制 `maxWidth*9/16`，我们的竖屏/方屏视频会被压黑框。我们现状更好。若你要严格 1:1，我改成 16:9。 |
| 2 | **重试卡**：保留「失败即停 + 手动重试」 | animeko 播放器层无点击重试，靠数据层自动切源。我们无多源，砍掉等于无法恢复。 |
| 3 | **亮度能力探测**：`supportsBrightness()` 为 false 时不接管竖拖 | animeko 用 `NoOpLevelController` 挂载但不响应，桌面会吃掉手势。我们更严谨。 |
| 4 | **倍速范围**：保留 0.5–3.0（11 档），不用 animeko 的 0.5–2.5 | 现有 `PlayerDefaults.speeds` 已含 2.25–3.0，砍掉是功能倒退。步长统一 0.25。 |
| 5 | **双击三分区**：保留 | 你已选。 |

---

## 九、实施阶段（每步可独立回退，按步提交）

> 提交纪律（项目约定）：`git commit -F msg.txt -- <显式路径>`，**禁止 `git add -A`**。

### P0 — 地基（不接线，零视觉变化）
1. 新建 `ui/` 包，落地 `PlayerControllerState.kt` + `PlayerFullscreenState.kt` + `keepLayoutWhenHidden`。
2. 落地 `PlaybackSpeedControllerState.kt` + `VideoAspectRatioControllerState.kt`。
- **验收**：`:shared:compileKotlinDesktop` + `:shared:compileAndroidMain` 绿；**视觉零变化**（未接线）。

### P1 — 脚手架搬家（纯搬家，零视觉变化）
3. 落地 `VideoPlayerScaffold.kt`（14 槽位 + insets 单一真相源 + scrim 收到 Scaffold）。
4. `VideoPlayerUi.kt` 重写为「装配宿主」：9 个平铺层 → 具名槽位；现有 40+ 参数先原样搬运，不改形状。
- **验收**：编译绿 + **视觉零变化** + 顶栏 hover 死代码消失 + 窄/宽屏/全屏/PiP 回归。

### P2 — 状态机替换
5. 删 `showControlsState` / `effectiveShowControls` / `playerUiVisible` 三布尔与 6 个 `LaunchedEffect`；5 个特例迁移为请求者。
6. hover 挂点从播放器 Box 移到顶/底栏容器（第一轮已改一半，此处收口为 `hoverToRequestAlwaysOn`）。
- **验收**：显隐行为与现状等价；拖动进度条时底栏不闪。

### P3 — 底栏重做（第一次可见变化）
7. `PlayerControllerBar` 两态：非 expanded = `[时间行] / [start | 进度条 weight(1f) | end]`；expanded = `[时间行] / [全宽进度条] / [start | 清晰度 | end]`。
8. 内置控件全部换 M3 `IconButton` 形制；图标 36/36/32；删除 `PlayerBarSkin` 与所有 B 站风分支。
- **验收**：截图对齐 animeko 底栏；非全屏进度条内联在图标排中间。

### P4 — 进度条重做
9. `PlayerProgressSliderState`（乐观值/节流下沉）+ `TouchSeekState`（144 dp 上滑取消）。
10. 4 层自绘轨道（底轨 / 缓冲 / 进度）+ 自绘 thumb 12×24 r=8 + 缓存独立色。
11. `MediaProgressIndicatorText`（描边 + 占位防抖 + 倍速剩余时间）。
12. 预览浮层：先做「只有时间」的 `CircleShape` 胶囊；P4b 接 `MediaProgressFramePreview`（LRU 8 / 2 s 网格）到 `supportsFrameCapture()`。
- **验收**：桌面 hover 出气泡；拖动不闪不跳；上滑可取消。

### P5 — 顶栏 / 指示器 / 加载态 / detached slider
13. `top/PlayerTopBar.kt`：M3 `TopAppBar(Transparent)` + 视频增强 / 数据源 / more 菜单；桌面加 focus workaround（issue #288）。
14. `GestureIndicator`（Surface 36 dp 行 + spring 入场 / 500 ms 出场 + 保留末帧）。
15. `VideoLoadingIndicator`（24/3 圈 + 描边文案）。
16. `detachedProgressSlider` + `floatingBottomEnd` + `rhsButtons`（截图）。
- **验收**：指示器显示秒数 / 音量值 / `3.00x`；控件隐藏时横滑出独立进度条。

### P6 — 手势层重做
17. `PlayerGestureHost` + `GestureFamily` 分叉 + `SwipeSeekerState`（秒数）+ 三分区双击 + `FastSkipState` + `SwipeLevelControl` + `SwipeToFullscreen` + `GestureLock`。
18. 键盘合并进 `gesture/PlayerKeyboardShortcuts.kt`（±5 s + 长按加速 + `F` + `0-9` + `M`）。
- **验收**：桌面鼠标语义（单击播放、不自动隐藏）与触摸语义（单击切控件、3 s 隐藏）都成立；三分区双击两端一致。

### P7 — 侧栏与面板
19. `VideoSideSheets`：右侧滑出（无圆角 / 无阴影 / 无遮罩 / 瞬时动画）+ `None` 栈底哨兵 + `listSaver`。
20. 倍速改就地 `Popup` + `SteppedSlider`（280 dp / 圆角 16 / 强制暗色 / 间距 8）。
21. 清晰度 / 超分 / GIF 归位（见 §七）。
- **验收**：面板从右侧滑出；倍速拖动实时预览、松手提交。

### P8 — 清理与许可
22. 删 7 个旧文件；删死 token（`panelWidth` / `trackTouch` / `bottomRow`）；清理 7 个文件顶部各 ~145 行重复 import。
23. **许可切换**：`LICENSE` / `NOTICE` / `README.md` 转 AGPLv3，animeko (OpenAni) 归属写入 NOTICE。
- **验收**：全量编译绿 + 回归清单全过。

---

## 十、红线

1. **不许碰引擎层**：`PlaybackEngine` / `ComposePlaybackController` / `PlaybackEngineFactory` 全程只读；`PlaybackEngineState` 字段不改。
2. **不许在本次引入 `movableContentOf`**（`播放器重写定稿.md` 红线，等 S4 之后）。
3. **播放器不动**：渲染面挂载点不因布局分支 / 异步就绪切换。
4. **每一步可独立回退**：按阶段提交，提交信息写清做了什么。
5. **P1 与 P2 的验收是「视觉零变化」**——这两步是纯搬家，任何视觉变化都说明碰坏了。

---

## 十一、验收口径

**编译**（iOS 当前本就编不过，见 `播放页-animeko控件对照与复刻方案.md` §十一，本次不以其为门禁）：
```
./gradlew :shared:compileKotlinDesktop :shared:compileAndroidMain
./gradlew :app:compileDebugKotlin          # 改了 :app 或平台壳槽位时必须加跑
```

**回归清单（P1 起每次必跑）**：窗口跨 840 dp 断点、全屏进出、切集、切片（清晰度切换）、错误重试、续播、本地片、PiP 进出。

**最终硬验收**：连续播 3 部片子不崩不闪；桌面端鼠标 hover / 键盘 / 滚轮三项全通。

---

## 十二、风险

| 风险 | 说明 | 缓解 |
|---|---|---|
| AGPLv3 传染 | 直接借鉴 animeko 实现 → 项目整体须转 AGPLv3 | 用户已拍板；P8 统一改 `LICENSE` / `NOTICE` / `README`，不要中途改一半 |
| P1/P2 搬家引入回归 | 857 行主函数重排，容易碰坏渲染面 | 严守「视觉零变化」验收；与 `播放器重写定稿.md` 的 S2/S3 合并做，不重复动 |
| 帧预览碰引擎 | `MediaProgressFramePreview` 需要抓帧 | 只读调用 `supportsFrameCapture()` / `grabFrameArgb()`，不改接口；P4b 独立一步，失败可整体回退 |
| 删 7 文件后 iOS 更坏 | iOS 当前已编不过 | 不以其为门禁；P8 前用 `git stash` 保留旧文件副本 |
| 缓存分块需要引擎信息 | 我们引擎只有一条 `bufferedPositionMs` | 只做「缓冲独立色」，三色分块挂起（不做） |
| 皮肤分支删不干净 | `bilibiliStyle` 已渗透 4 个组件 | P3 一次性删；删完 grep `bilibiliStyle` 必须零命中 |

---
## 附：animeko 关键文件索引
```
app/shared/video-player/src/commonMain/kotlin/ui/
├── VideoScaffold.kt:81-369              脚手架（14 槽位 / insets / scrim / keepLayoutWhenHidden:371）
├── PlayerControllerState.kt:126-304     可见性状态机（六位 + 三请求者列表）
├── PlayerFullscreenState.kt             request(target) 幂等
├── VideoLoadingIndicator.kt             24/3 圈
├── VideoSideSheets.kt                   右侧滑出 + NavDisplay 导航栈
├── VideoAspectControllerState.kt        Fit/Crop/Fill
├── PlaybackSpeedControls.kt             0.25 量化 / preview+commit
├── top/PlayerTopBar.kt:49-86            M3 TopAppBar(Transparent)
├── progress/
│   ├── PlayerControllerBar.kt:813-886   两态底栏（本轮已完整读取）
│   ├── MediaProgressSlider.kt           951 行（4 层自绘 / 预览浮层 / TouchSeek）
│   ├── MediaProgressIndicatorText.kt    描边 + 占位 + 剩余时间
│   ├── MediaProgressFramePreview.kt     LRU 8 / 2s 网格 / debounce 50
│   └── VerticalSlider.kt                旋转 270° 的音量条
└── gesture/
    ├── PlayerGestureHost.kt             871 行（GestureIndicator 在 :302-430）
    ├── FastSkipState.kt                 长按 500ms / 3f
    ├── SwipeSeekerState.kt              97s 满宽 / 144dp 取消
    ├── SwipeVolumeControl.kt            LevelController / 40 档
    ├── SwipeToFullscreen.kt             64dp
    ├── GestureLock.kt                   锁钮 + LockedScreenGestureHost
    └── PlayerKeyboardShortcuts.kt       ±5s / F / 0-9 / M
上层装配：app/shared/src/commonMain/kotlin/ui/subject/episode/EpisodeVideo.kt:202-567
```
