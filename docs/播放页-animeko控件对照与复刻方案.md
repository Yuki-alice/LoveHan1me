# 播放页对照：animeko 视频控件 vs 本项目视频详情页

> 制定：2026-09-15（通读两边源码后逐行核对）
> 目标：**保留本项目的「信息显示」与「组件样式」，复刻 animeko 的「布局骨架」与「视频控件结构/交互」。**
> 红线：animeko 为 **AGPLv3**，只学结构与数值，不整段搬代码（clean-room 重写）。
> 与既有文档的关系：本文只谈 **UI 层（布局 + 控件 + 手势 + 状态机）**；
> 引擎/渲染面所有权问题见 `docs/播放器重写定稿.md`（那份解决"崩"，本文解决"像不像"）。

---

## 〇、结论速览

| 层 | 差距 | 判断 |
|---|---|---|
| **控制器可见性状态机** | ★★★★★ | 我们是「一个 Boolean + 一堆特例标志」，animeko 是「6 位掩码 + 引用计数 + 优先级」。**这是根，其余差距多由它派生** |
| **进度条** | ★★★★★ | animeko 951 行（缓存分块 / 章节 / 预览浮层 / 上滑取消 / 描边时间），我们 ~110 行三条色块 |
| **底部栏行结构** | ★★★★ | animeko 非全屏 = 进度条**内联在按钮排中间**；我们 = 进度条独占一行 |
| **播放器脚手架分层** | ★★★★ | animeko 有 `VideoScaffold` 抽象（14 个具名槽位 + 统一 insets）；我们把 9 个控件平铺在 839 行的 `VideoPlayerUi` 里 |
| **侧栏** | ★★★ | animeko 右侧滑出 + 内部 `NavDisplay` 导航栈；我们是底部 `ModalBottomSheet` 平铺选项 |
| **全屏状态对象** | ★★★ | animeko 把 `isFullscreen` 与 `request(target)` 绑成**同一对象**；我们拆成 Bool + 回调两处 |
| **手势语义** | ★★★ | animeko 横向拖动 HUD 显示**秒数**、上滑取消 seek、双击分端；我们显示百分比、无取消、双击三区一刀切 |
| **加载/缓冲态** | ★★ | animeko 7 种文案 + **下载速度** + 描边文字；我们转圈 + 重试卡（重试卡我们更好） |
| **图标/控件尺寸** | ★★ | 我们整体偏小一号（20dp vs 32/36dp） |
| **信息密度（我们更强，保留）** | — | 清晰度切换、重试卡、超分、截图/GIF、主页键、电量、双 Tab 信息排列 |

---

## 一、布局骨架：`VideoScaffold` vs `VideoShellContent` + `VideoPlayerUi`

### animeko

`VideoScaffold.kt:81-369`（406 行，单文件），结构是**一层 Box + 全部覆盖层 `matchParentSize`**：

```
BoxWithConstraints(expanded ? fillMaxHeight : fillMaxWidth)   // 16:9 box 或全屏
└─ Box(!maintainAspectRatio ? fillMaxSize : fillMaxWidth().height(maxWidth*9/16))
   ├─ Box.matchParentSize            → video()            （唯一调用点，不套 insets）
   ├─ Box.matchParentSize            → danmakuHost()      （只套 Vertical insets + 8dp）
   ├─ BoxWithConstraints.matchParentSize → gestureHost()  （拿 constraints.maxWidth 算 swipe 比例）
   ├─ Box.matchParentSize            → playerStatsOverlay()
   ├─ Box { Column {
   │     AniAnimatedVisibility(topBar)      → 自带顶 scrim 渐变
   │     Box.weight(1f)                     （占位，把底栏推到底）
   │     AniAnimatedVisibility(bottomBar)   → 自带底 scrim 渐变
   │     AniAnimatedVisibility(detachedSlider)
   │   }  + AniAnimatedVisibility(floatingBottomEnd).align(BottomEnd) }
   ├─ Column(End insets) { rhsBar(), gestureLock() }
   ├─ Box { leftBottomTips（下方 50% 区域垂直居中）}
   ├─ Box.matchParentSize(Center)   → floatingMessage()   （缓冲/错误）
   ├─ Box.matchParentSize(Center)   → framePreviewOverlay()
   └─ Box.matchParentSize            → rhsSheet()
```

关键设计点（**都值得抄**）：

1. **`maintainAspectRatio = !expanded`**：全屏只是「不放 16:9 约束」，**不改结构、不换分支**。注释原话：*"全屏时此框架会 fillMaxSize, 否则会限制为一个 16:9 的框"*。
2. **insets 单一真相源**：Scaffold 收一个 `contentWindowInsets`，然后按层分发 —— 视频层**不套**、弹幕层只 `Vertical`、顶栏 `Horizontal+Top`、底栏 `Horizontal+Bottom`、右栏 `End`、framePreview **不套**。每个覆盖层自己不做 `statusBarsPadding()`。
3. **顶/底 scrim 由 Scaffold 画，不由 bar 画**：`VideoScaffold.kt:176-182` 顶渐变 `Transparent(0.72) → 0.32f:Transparent(0.45) → 1f:Transparent`；`:252-258` 底渐变是它的镜像。
4. **`keepLayoutWhenHidden(sliderOnly)`**（`:371-382`）：需要「视觉隐藏但保留布局与命中拦截」时用 `alpha(0f)` + `clearAndSetSemantics` + 吞指针事件，**而不是 `if (!visible)` 摘掉子树**。这是"拖动进度条时不被替换"能成立的前提。

### 本项目

`VideoShellContent.kt`（269 行，只做单/双栏分叉）+ `VideoPlayerUi.kt`（839 行，渲染 + 手势 + 9 个控件平铺）。

```
VideoShellContent
├─ showSideRelated → Row[ 左 weight(1f) PlayerBox | 右 width(rememberRelatedPaneWidth()) railTabsContent ]
└─ else           → MainContent: Box[ 状态栏黑条 ] + Column[ PlayerBox(height=playerHeightDp) | tabsContent ]
        PlayerBox = VideoPlayerUi(...)  ← 40+ 个参数
```

`VideoPlayerUi` 内部：`Box(modifier…pointerInput 手势)` 里依次挂
`BoxWithConstraints(视频渲染 + 自己算 aspectRatio letterbox)` → `Box(拖动手势 + 双指缩放 + 滚轮缩放)` → `GestureIndicatorOverlay` → `PlayerBackdropLayers` → `PlayerTopBar` → 长按倍速 HUD → `PlayerBufferingOverlay` → `PlayerCenterControls` → `PlayerBottomBar` → `PlayerStateCards` → `PlayerSidePanelHost`。

### 差距

| # | 差距 | 证据 | 影响 |
|---|---|---|---|
| 1.1 | **没有 Scaffold 抽象**：覆盖层直接平铺在 839 行的巨型 composable 里，槽位没有名字 | `VideoPlayerUi.kt:398-838` | 想加/挪一个层（如 framePreview、leftBottomTips、stats）要动主函数；animeko 是加一个具名 lambda |
| 1.2 | **尺寸职责双重**：外框高度由 Shell 给（`height(playerHeightDp)`），内框又由 UI 自己 `BoxWithConstraints` 算 aspectRatio 做 letterbox | `VideoShellContent.kt:206-215` + `VideoPlayerUi.kt:486-529` | animeko 只有 Shell 一处决定尺寸（16:9 约束或 fillMaxSize）。我们两处，改一处容易打架 |
| 1.3 | **insets 分散 4 处** | `VideoShellContent.kt:192-204`（状态栏黑条 + statusBarsPadding）、`VideoPlayerTopBar.kt:203`、`VideoPlayerBottomBar.kt:219,399`（navigationBarsPadding）、`VideoShellContent.kt:253`（右栏 statusBarsPadding） | 没有单一真相源；iOS/桌面容易出现"补了两次"或"漏了一次" |
| 1.4 | **scrim 由 bar 自己画**，且默认分支只在全屏画毛玻璃卡 | `VideoPlayerTopBar.kt:213-241`、`VideoPlayerBottomBar.kt:229-253`（默认）/ `:401-414`（B 站风） | 非全屏顶栏是**裸的**（无 scrim 无底），文字压在画面上可读性靠运气 |

---

## 二、控制器可见性状态机（**最该先复刻的一处**）

### animeko：6 位掩码 + 3 个请求者列表 + 优先级

```
ControllerVisibility(topBar, bottomBar, floatingBottomEnd, rhsBar, gestureLock, detachedSlider)

visibility = when {
    inlineProgressSliderRequesters 非空 → InlineSliderOnly   // 拖底栏进度条中：只留底栏+进度条
    alwaysOnRequests 非空               → Visible            // 有人请求常亮（hover/下拉菜单）
    fullVisible                         → Visible            // 用户单击切出来的
    progressBarRequesters 非空          → DetachedSliderOnly // 控件隐藏时横滑 seek：独立进度条
    else                                → Invisible
}
```

- `alwaysOnRequests` / `progressBarRequesters` / `inlineProgressSliderRequesters` 都是 `SnapshotStateList<Any>`，`Any` 作为**请求者身份**。
- `rememberAlwaysOnRequester(controllerState, "bottomBar")` 返回带 `request()/cancelRequest()` 的对象，`DisposableEffect` 里自动 `cancelRequest()` 防泄漏。
- 两条现成用法：`Modifier.hoverToRequestAlwaysOn(requester)`（**hover 底栏/顶栏 = 请求常亮**）、`SpeedSwitcher(onExpandedChanged)`（**弹层展开 = 请求常亮**）。
- `withGestureLocked(locked)` / `withExpanded(expanded)` 是纯函数式派生：锁定时把 topBar/bottomBar/detachedSlider/rhsBar 全置 false，**不改变状态源**。

### 本项目

```kotlin
var showControlsState by remember { mutableStateOf(true) }   // 唯一真值，布尔
LaunchedEffect(showControlsState, isPlaying, activeSidePanel,
               gestureType, isProgressGestureActive, isHovered) {
    if (showControlsState && isPlaying && activeSidePanel == null &&
        gestureType == null && !isProgressGestureActive && !isHovered) {
        delay(CONTROLS_AUTO_HIDE_MS); showControlsState = false
    }
}
val effectiveShowControls = showControls && showControlsState && !isLocked
val playerUiVisible = effectiveShowControls && activeSidePanel == null
```

`VideoPlayerUi.kt:289-311`。

### 差距

| # | 差距 | 具体后果 |
|---|---|---|
| 2.1 | **没有「请求者」概念**，只能给每个新场景加一个 `&& xxx == null` 特例 | 现在已经串了 5 个特例（panel / gestureType / isProgressGestureActive / hover / locked）。再加"侧栏展开""菜单下拉""弹幕输入聚焦"就要继续串 |
| 2.2 | **拖动进度条无法阻止隐藏**：靠 `isProgressGestureActive` 这个专用标志，而不是"谁在拖谁请求" | 手势结束瞬间 `isProgressGestureActive=false` → 计时器重启 → 底栏可能闪一下；animeko 是引用计数，天然无此问题 |
| 2.3 | **hover 语义错位**：`hoverable` 挂在**整个播放器 Box** 上 | 鼠标停在画面正中央（不是控件上）也会一直不隐藏。animeko 是挂在**顶栏/底栏容器**上，只有 hover 控件才算 |
| 2.4 | **没有"控件隐藏时的独立进度条"（detached slider）** | animeko 在控件收走时横滑 seek，底部会浮出一条独立进度条（`DetachedSliderOnly`）；我们只能把整排控件叫回来 |
| 2.5 | **没有 `keepLayoutWhenHidden`** | animeko 拖进度条时保留底栏布局只做视觉隐藏，**不替换正在接收触摸的组件**；我们从一开始就没替换，但也没有这个工具，将来做 detached slider 会踩坑 |
| 2.6 | ~~锁定态锁钮不常驻~~ **（2026-09-15 复核：不是差距，已纠正）** | 原判断有误。animeko `ControllerVisibility.Invisible.gestureLock = false`，而 `withGestureLocked(true)` 只关 topBar / bottomBar / detachedSlider / rhsBar、**不动 gestureLock** —— 所以锁定态的路径同样是「点屏 → 只剩锁钮可见 → 超时收起」，与我们「点屏 → 解锁钮亮 3 秒」**行为等价**。仍值得做的只有「抽成具名槽位」这一件（已做，见 §十一） |

> **建议**：P0 先把 `PlayerControllerState` 按 animeko 的形状重建（六个布尔 + 三个请求者列表 + `derivedStateOf` 优先级），**同时**把现有 5 个特例迁移成"请求/取消请求"。这一步做完，后面所有控件改动都会变简单。

---

## 三、底部控制栏逐行对照

### animeko `PlayerControllerBar(expanded, sliderOnly)`

**非 expanded（窄屏，`:808-880`）**：
```
Column(padding 4/2) {
  Column {
    Row(padding start 4) { progressIndicator() }        ← 时间文字独占一行
  }
  Row(spacedBy(4)) {
    Row   { startActions()  }        // 播放/暂停、下一集、弹幕开关、音量
    Row.weight(1f) { progressSlider() }   ← 进度条内联在图标之间！
    Row   { endActions()    }        // 全屏
  }
}
```

**expanded（全屏 / 宽屏左列，`:826-878`）**：
```
Column(padding 8/4) {
  Column {
    Row(padding start 8)   { progressIndicator() }   // 时间
    Row(fillMaxWidth)      { progressSlider()   }   // 全宽进度条独占一行
  }
  Row(spacedBy(8)) {
    Row   { startActions()  }                 // 播放/下一集/弹幕/音量
    Row.weight(1f) { danmakuEditor() }        // ← 中间是弹幕输入框
    Row   { endActions()    }                 // 选集/音轨/字幕/比例/倍速/全屏
  }
}
```

两端共同点：
- **播放/暂停永远在最左**，全屏永远在最右（`startActions` / `endActions` 对称命名）。
- `sliderOnly = (visibility == InlineSliderOnly)` 时用 `keepLayoutWhenHidden` 把 icon 行视觉隐藏但保留布局。

### 本项目 `PlayerBottomBar` / `BilibiliBottomBar`

两套皮肤**都是 2 行**，但行内容不同：

| | 默认分支（`VideoPlayerBottomBar.kt:209-362`） | B 站风（`:390-523`） |
|---|---|---|
| 背景 | 毛玻璃卡（`posterBlur()` + `barSurface` + 1dp `border` + `largeIncreased` 圆角） | 底 scrim 渐变（`scrimBottomStart→scrimBottomEnd`），无卡无边 |
| 第 1 行 | `PlayerSlider` 全宽（命中 48 / 视觉 12） | 同 |
| 第 2 行 | `[play] gap [时间文字] Spacer(weight 1f) [倍速 chip] [清晰度 chip] [全屏]` | `[play] [下一集] gap [时间] gap BiliDanmakuField(weight 1f) [倍速白字] [清晰度白字] [全屏]` |

### 差距

| # | 差距 | 判断 |
|---|---|---|
| 3.1 | **进度条位置**：animeko 非全屏把进度条放在图标排中间（`weight(1f)`）；我们永远独占一行 | 抄。这是"看起来像不像 animeko"最直观的一处 |
| 3.2 | **全屏时缺「时间 / 全宽进度条 / 操作行」三行结构** | 抄。我们全屏只是同一套两行，中间没有弹幕输入位 |
| 3.3 | **时间文字位置**：animeko 在按钮行**上方**独立一行；我们夹在 play 与 chip 之间 | 抄（与 3.1 一起改） |
| 3.4 | **没有弹幕输入框**（B 站风那个是占位，点了提示"暂未开放"） | 我们无弹幕功能 → **保留现状**，但把位置留出来 |
| 3.5 | **B 站风底栏已非常接近 animeko 的 expanded 行结构**（左图标 / 中输入框 / 右入口） | 说明方向对，把默认分支也统一过去即可 |
| 3.6 | 底部按钮视觉 26dp / 图标 18-20dp | animeko 播放键 36dp、全屏 32dp → **偏小一号**，桌面端尤其明显 |

---

## 四、进度条（控件层最大差距）

### animeko `MediaProgressSlider`（951 行）

| 能力 | 实现 |
|---|---|
| 22dp 容器 / 6dp 轨道 / 圆角胶囊 | `MediaProgressSlider.kt:358-367` |
| **4 层自绘 Canvas** | 底轨 → **缓存分块**（`DOWNLOADING` 黄 / `DONE` 灰 / `NOT_AVAILABLE` 红，**连续同色块合并绘制避免精度缝隙**，`:378-405`）→ 播放进度 → **章节标记点**（`:444-464`） |
| M3 `Slider` 只做载体 | 轨道全 `Color.Transparent`，thumb 换成自绘 `Canvas(12dp × 24dp)` 画 `r=8dp` 圆（`:622-632, :586-592`） |
| **预览浮层** | hover / 拖动时 `Popup` 跟随鼠标 X：胶囊形只显示时间；**有帧预览时变圆角矩形 160×90 显示预览帧 + 时间**（`:556-570`、`ProgressSliderPreviewPopup:709-770`） |
| **时间文字带章节名** | `renderPreviewTime()`：如果预览位置落在某章节内，**第一行显示章节名**（`:473-485`） |
| **上滑取消 seek** | `TouchSeekState` 状态机 `Idle→Seeking→(上滑过阈值)Cancelling→(回退)Seeking→Idle`（`:283-335`），取消时中央显示"松手取消" |
| **帧预览按缓存位置节流** | 只对**已缓存完成**的位置请求预览帧，避免抢占下载优先级（`:548-552`） |
| thumb 上的时间 | `showPreviewTimeTextOnThumb`（expanded 时为 true）—— 拖动时**时间跟着 thumb 走** |

### 本项目 `PlayerSlider`（`VideoPlayerControls.kt:343-452`）

M3 `Slider` + 自绘 thumb（glow 圆 + 白圆）+ 3 层 `Box` 轨道（底轨 `track` / 缓冲 `trackBuffered` / 播放进度 primary 渐变），厚度 3dp，容器 18dp。

另在 `VideoPlayerUi.kt:241-242, 783-798` 有一层**乐观值 + 120ms 节流**（`sliderDragValue` / `SLIDER_SEEK_THROTTLE_MS`）—— 这一层 animeko 是靠 `PlayerProgressSliderState.previewPositionRatio` + `onPreview/onPreviewFinished` **在状态对象里**做的（`MediaProgressSlider.kt:113-174`），职责更清楚。

### 差距

| # | 差距 | 价值 |
|---|---|---|
| 4.1 | **无预览浮层**（hover/拖动不出时间气泡） | ★★★★★ 桌面端体感差距最大的一处 |
| 4.2 | **无预览帧** | ★★★★ 需要引擎抓帧能力（我们已有 `supportsFrameCapture`，可复用） |
| 4.3 | **无上滑取消 seek** | ★★★★ 移动端误触保护，animeko 的 `TouchSeekState` 可整体照抄形状 |
| 4.4 | **无缓存分块**（只有一条 `bufferedProgress`） | ★★★ 网络片看"哪段能拖"很有用；需要引擎给分块信息（我们目前没有） |
| 4.5 | **无章节标记**（我们数据源无章节概念） | ★ 数据不支持 → 跳过 |
| 4.6 | **缓冲区没有独立颜色语义** | ★★ 低成本 |
| 4.7 | 拖动状态的乐观值散落在 UI 主函数里 | ★★★ 应下沉到 `PlayerProgressSliderState` 形状的对象里，与 4.1/4.3 一起做 |

---

## 五、顶部控制栏

| 维度 | animeko | 本项目 |
|---|---|---|
| 载体 | M3 `TopAppBar`（`containerColor = Transparent`），`PlayerTopBar.kt:49-86` | 自绘 `Box` + `Row`（`VideoPlayerTopBar.kt:193-355`） |
| 返回 | `Icons.AutoMirrored.Rounded.ArrowBack`，默认 `IconButton` | `Res.drawable.ic_arrow_back_ios`，`iconLarge`(20dp) |
| 标题 | **仅 `expanded` 时给 title**，非全屏 `title = null` | **恒显示**（窄屏也在） |
| 主页键 | 无 | 有（我们的导航需要，**保留**） |
| 右侧 | `IconButton` 圆形：跳过 OP/ED、画质增强、媒体源、置顶、更多菜单（下拉里：一起看/弹幕设置/统计/外链/缓存）、侧栏折叠（桌面） | `PlayerMenuChip` **玻璃药丸带边框**：超分 / 截图 / GIF + 时间 + 电量 |
| scrim | Scaffold 提供 | 自己画（B 站风分支）/ 非全屏裸着（默认分支） |
| 桌面坑 | `needWorkaroundForFocusManager`：返回键 `onFocusEvent` 里 `clearFocus()`（issue #288） | 无 |

**差距**：① 非全屏时我们给了标题，animeko 不给（`expanded` 才给）—— 但这属于"信息显示"，**按用户要求保留**；
② 药丸 chip vs 圆形图标：**保留我们的样式**，但可考虑把 scrim 补齐（1.4）；
③ 桌面 focus workaround 值得抄（我们现在没处理）。

---

## 六、手势层

| 手势 | animeko（`PlayerGestureHost.kt` / `LockableVideoGestureHost`） | 本项目（`VideoPlayerUi.kt:405-682`） | 差距 |
|---|---|---|---|
| 单击 | `toggleFullVisible()` | 切 `showControlsState` | — |
| 双击 | **分输入设备**：触屏 = 播放/暂停；鼠标 = 全屏。左/右双击 = 快退/快进（`FastSkipState`，按区间累计） | **三分区一刀切**（左 1/3 退 / 右 1/3 进 / 中键暂停），缩放 ≠ 1 时双击归零 | 交互语义偏"通用播放器"，非 animeko；但三分区是 B 站/YouTube 主流，**可保留** |
| 长按 | 3x 速 + `>> 3.0x` 指示器（`GestureIndicatorState.PLAYBACK_SPEED`） | `longPressSpeedTime` 倍速 + 左侧小图标 HUD（**无倍速数值**） | 补数值显示 |
| 横向拖动 | `SwipeSeekerState`：像素 → **秒数**；**上滑过阈值 = 取消** | `abs(dx) > abs(dy)` 判轴，`size.width * sensitivity` → **百分比** | 抄"显示秒数 + 上滑取消" |
| 左/右竖拖 | `SwipeVolumeControl` / 亮度 `LevelController`（无灰化逻辑） | 亮度（左半屏）/ 音量（右半屏）；**平台无亮度能力时不接管**（比 animeko 严谨，保留） | 我们更严谨 |
| 上/下滑全屏 | `SwipeToFullscreen` | 无 | P2 |
| 锁定 | `LockedScreenGestureHost`：点屏显控件，**2 秒**后自动收（锁定时只剩锁钮可见） | 点屏（`unlockButtonTimeoutToken++`）→ 解锁钮亮 **3 秒**后收起 | **行为等价，无差距**（见 §二 2.6 的复核） |
| 键盘 | Space / ←→ / F / B / ↑↓ / `,` `.`（逐帧）/ 双击区间 | Space / ←→ / ↑↓ / F 等（79 行） | 补逐帧、B（黑边） |
| 指示器 | `GestureIndicator`：`<< 00:00` / `>> 00:00` / 音量亮度条 / `1.5x`；`fadeIn(spring)` + `fadeOut(tween 500)`，**淡出期间保留最后一帧**避免空 Surface | 中央 170×190 玻璃卡（图标 + 标题 + 进度条 + 百分比） / B 站风顶部小卡 | 内容维度：animeko 给**实际值**（秒/音量/倍速），我们给**百分比** |

---

## 七、侧栏 / 面板 / 加载态

| 维度 | animeko | 本项目 |
|---|---|---|
| 侧栏 | `VideoSideSheets`（`VideoSideSheets.kt`）：**右侧滑出面板 + 内部 `NavDisplay` 导航栈**，路由栈底恒为 `None`，`Saver` 可恢复（列表 → 详情 → 再进一层） | `PlayerSidePanelHost` + M3 `ModalBottomSheet`，3 个扁平面板（倍速/超分/清晰度），无导航 |
| 倍速 | `SpeedSwitcher`：入口 TextButton 显示当前值 → **就地 `Popup` + `SteppedSlider`（280dp，圆角 16，强制暗色）**，拖动 `previewSpeed`、松手 `commitSpeed` | chip → 底部弹窗**选项列表**，选中即关 |
| 加载 | `VideoLoadingIndicator`：`CircularProgressIndicator(24dp, 3dp)` + 下方文字；**7 种文案**（自动选择 / 解析源 / 解码数据 / 正在缓冲 / 缓冲过久 / 失败分类）；**缓冲时显示下载速度 `/s`**；文字 `TextWithBorder` 描边保证任何画面上可读 | 转圈 + 状态卡 + 重试卡（重试卡我们更好，**保留**） |
| 指示器文案 | `MediaProgressIndicatorText`：`00:00 / 00:00 (-00:00)`，等宽 + **双层 Text 描边**，透明占位 `88:88 / 88:88` 防抖动，倍速≠1 时给**按倍速重算的真实剩余时间** | 单个 `labelSmall` 文本，无剩余时间、无描边、无占位 |
| 悬浮控件 | `floatingBottomEnd` = 浮动全屏钮；`rhsButtons` = 截图钮（右侧竖排悬浮圆角卡，0.5dp 描边 + 5% 底） | 无 |

---

## 八、可直接抄的数值表

| 项 | animeko | 本项目现状 | 建议 |
|---|---|---|---|
| 顶栏高度 | M3 `TopAppBar` 默认 64dp | `topBarMinHeight = 52dp` | 保留（我们的信息行更高） |
| 顶栏图标 | 默认 24dp | 20dp | 提到 24dp |
| 底栏播放/暂停图标 | **36dp** | 18dp（`iconSmall`）/ 20dp（`iconLarge`） | 提到 28–32dp |
| 底栏全屏图标 | **32dp** | 18–20dp | 提到 28dp |
| 底栏下一集图标 | 36dp | 20dp | 提到 28dp |
| 按钮命中区 | IconButton 默认 48dp | 48dp（`playerHitTarget` 已做） | ✅ 已达标 |
| 进度条容器 | 24dp | 18dp（`trackBox`） | 提到 22–24dp |
| 进度轨道 | **6dp** | 3dp | 提到 4–6dp |
| thumb | 自绘 `12dp × 24dp`，`r=8dp` 圆 | glow + 白圆（`thumb` / `thumbGlow` / `thumbBox`） | 保留我们的形，尺寸对齐 |
| 顶 scrim | `0→0.32→1`，alpha `0.72 / 0.45 / 0` | `scrimTop = 120dp` | 补中间停靠点 |
| 底 scrim | 镜像 | `scrimBottom = 180dp` | 同上 |
| 预览浮层 | 有帧 160×90 圆角 12；无帧胶囊 | 无 | 新增 |
| 指示器动画 | `fadeIn(spring StiffnessMedium)` + `fadeOut(tween 500)` + 保留最后一帧 | `fadeIn()` / `fadeOut()` 默认 | 抄（含保留帧技巧） |
| 锁定后自动收 | 2s | 3s | 保留 3s |

---

## 九、复刻路线（建议按此顺序，每步可独立回退）

### P0 — 状态机与脚手架（结构，不动视觉）

1. **重建 `PlayerControllerState`**：六个布尔位 + `alwaysOnRequests` / `progressBarRequesters` / `inlineProgressSliderRequesters` 三个 `SnapshotStateList<Any>` + `derivedStateOf` 优先级 + `rememberAlwaysOnRequester` / `hoverToRequestAlwaysOn`。
   - 迁移现有 5 个特例（panel / gestureType / isProgressGestureActive / hover / locked）为"请求/取消请求"。
   - hover 挂点从**整个播放器 Box** 移到**顶栏/底栏容器**。
2. **抽出 `VideoPlayerScaffold`**：14 个具名槽位（topBar / video / gestureHost / centerOverlay / floatingMessage / framePreviewOverlay / rhsButtons / gestureLock / bottomBar / detachedProgressSlider / floatingBottomEnd / rhsSheet / leftBottomTips / playerStatsOverlay），统一 `contentWindowInsets` 分发；scrim 收到 Scaffold 里画。
   - `VideoPlayerUi` 瘦身为"装配 + 渲染宿主"，兼容现有 40+ 参数（先搬家，不改形状）。
3. **`PlayerFullscreenState`**：把 `isFullscreen` + `request(target)` 绑成一个对象，图标方向与点击行为读同一个源。

**验收**：三端编译绿；现有视觉**零变化**（这一步是纯搬家）；窗口跨 840dp、全屏进出、切集回归。

### P1 — 进度条重做（控件层，视觉新增但不改信息）

4. **`PlayerProgressSliderState`**：把 `sliderDragValue` / `SLIDER_SEEK_THROTTLE_MS` 从 UI 主函数下沉，提供 `previewPositionRatio` / `finishPreview` / `cancelPreview` / `isPreviewing` / `displayPositionRatio`。
5. **`TouchSeekState` 上滑取消**：`Idle→Seeking→Cancelling`，取消时中央指示器显示"松手取消"。
6. **预览浮层**：hover / 拖动时 `Popup` 跟随；先做**只有时间**的胶囊（复用现有 120ms 节流取位置），帧预览留 P2 接到 `supportsFrameCapture`。
7. **缓存分块**：先给"已缓冲"独立色（不等引擎分块），后续有分块信息再升级成彩块。
8. **时间指示器**：等宽 + 描边 + 占位防抖 + 倍速剩余时间。

**验收**：窄屏拖动进度条不闪不跳；桌面 hover 出时间气泡；上滑取消可用。

### P2 — 布局行结构 + 数值对齐

9. **底部栏两态化**：`expanded` 假 → `[时间] / [play… | 进度条 weight(1f) | …全屏]`；`expanded` 真 → `[时间] / [全宽进度条] / [操作行]`。默认分支与 B 站风共用同一套行结构，只换皮肤。
10. **图标尺寸**：播放 32、全屏 28、下一集 28（视觉），命中区仍 48。
11. **进度条数值**：容器 22、轨道 4–6。
12. **`detachedProgressSlider`**：控件收走时横滑 seek，底部浮出独立进度条（含"松手取消"提示）。
13. **锁定槽独立**：`gestureLock` 从 `PlayerCenterControls` 抽到独立槽位，锁定时常驻。

### P3 — 面板与加载

14. **侧栏改造**：`ModalBottomSheet` → 右侧滑出面板（可选内部导航栈）；倍速改**就地 Popup + Slider**（preview/commit 两段）。
15. **加载态**：7 种文案 + 下载速度 + `TextWithBorder` 描边。
16. **`leftBottomTips` / `floatingBottomEnd` / `rhsButtons`**：按需（跳过片头/浮动全屏/截图悬浮卡）。
17. **桌面 focus workaround**（issue #288）。

### 明确保留（不抄）

- **信息显示**：简介/评论/相关 Tab 排列、清晰度切换、超分、截图/GIF、主页键、电量、重试卡、`PlayerTrace` 埋点。
- **组件样式**：`HanimeDefaults` 色板、毛玻璃卡、B 站风 scrim 皮肤（`bilibiliStyle` 双分支模式）。
- **我们更严谨的点**：亮度能力探测（无能力不接管手势）、重试从**失败位置**续（animeko 无此语义）。
- **无数据支撑的**：章节标记、弹幕开关/输入/渲染、字幕/音轨/画面比例、一起看、stats for nerds。

---

## 十、风险

| 风险 | 说明 | 缓解 |
|---|---|---|
| AGPL 传染 | animeko 是 AGPLv3 | clean-room：只记结构与数值，代码自己写；评审看"像不像抄" |
| P0 搬家引入回归 | 839 行主函数重排，容易碰坏渲染面结构 | **先搬家不改形状**，视觉零变化作为验收；与 `docs/播放器重写定稿.md` 的 S2/S3 合并做 |
| 进度条重做碰引擎 | `TouchSeekState` / 预览浮层需要引擎提供 `bufferedPosition` 与抓帧 | 只读现有 `PlaybackEngine` 接口（红线：不许改引擎接口） |
| 皮肤双分支膨胀 | `bilibiliStyle` 已在 4 个组件里各挂一套分支，P2 行结构改造会让分支变多 | **行结构共用**，只把皮肤部分（背景/前景色/圆角）抽成参数；不要行结构也分叉 |

---

## 十一、实施进度（2026-09-15 20:30，第一轮「快速复刻」）

本轮只做**视觉复刻 + 交互体感**两块，**零改动**引擎接口与渲染面所有权（那部分见 `docs/播放器重写定稿.md`）。
验收口径：`./gradlew :shared:compileKotlinDesktop :shared:compileAndroidMain` **全绿**。

### 已落地

| # | 改动 | 文件 | 对应本文 |
|---|---|---|---|
| A | **底栏两态行结构**：非 expanded = `[时间行] / [播放·下一集 · 进度条 weight(1f) · 倍速·清晰度·全屏]`；expanded = `[时间行] / [全宽进度条] / [操作行]` | `VideoPlayerBottomBar.kt` | §三 3.1 / 3.2 / 3.3 |
| B | **皮肤与行结构解耦**：新增 `PlayerBarSkin { Glass, Bilibili }` + 共享骨架 `PlayerBottomBarContent`，皮肤只换背景/前景 | 同上 | §十「皮肤双分支膨胀」 |
| C | **进度条时间预览气泡**：hover / 拖动时 `Popup` 跟随指针或 thumb，自定义 `PopupPositionProvider` 锚在进度条上沿 | `VideoPlayerControls.kt` | §四 4.1 |
| D | **上滑取消 seek**：在 `PointerEventPass.Initial` **只读**观察竖直位移，过 48dp 判取消；气泡文案切「松手取消」，松手回吐拖动起点 | 同上 | §四 4.3 |
| E | **数值对齐**：播放/下一集 18/20 → **32**；全屏 → **26**；顶栏图标 20 → **24**；轨道 3 → **5**；容器 18 → **22**；thumb 9 → **12** | `ui/theme/Defaults.kt` | §八 数值表 |
| F | **时间文字等宽 + 描边**：`tnum` + 「同字画两遍」，线宽沿用 animeko 的 **字号 / 15** | `VideoPlayerBottomBar.kt` | §七 指示器文案 |
| G | **hover 挂点修正**：从「整个播放器 Box」移到**顶栏 / 底栏容器** | `VideoPlayerUi.kt` + 顶/底栏 | §二 2.3 |
| H | **锁钮具名槽位**：抽成 `PlayerGestureLockButton`，**行为不变** | `VideoPlayerCenterControls.kt` | §二 2.6（复核） |
| I | **`expanded` 独立参数**：与 `bilibiliStyle` 当前同源但语义分开（行结构 vs 皮肤） | `VideoPlayerUi` / `VideoShellContent` | §一 1.1 |

### 未做（下一轮候选）

- **缓存分块三色**（§四 4.4）—— 需要引擎给分块信息，撞「不许改 `PlaybackEngine` 接口」红线 → 挂起。
- **预览帧 160×90**（§四 4.2）—— 等接到 `supportsFrameCapture` 再升级，位置提供者已留好。
- **detached slider / 侧栏滑出改造 / 加载态 7 文案 / `leftBottomTips`**（§九 P2 剩余 + P3）—— 本轮未动。
- **`PlayerControllerState` 六位掩码重建**（§二，原 P0）—— 本轮先用「补语义」而非「重建状态机」的方式解决
  了最影响体感的两条（hover 挂点、锁定槽），完整重建留到下一轮。

### ⚠️ 一处**既有**问题（非本轮引入，需独立排期）

`./gradlew :shared:compileKotlinIosSimulatorArm64` **当前编不过**，报错分布在：

- `commonMain/.../core/util/StartupTrace.kt`、`commonMain/.../feature/video/PlayerTrace.kt` —— `synchronized`（JVM 专属）
- `commonMain/.../feature/settings/ThemeAuditScreen.kt` —— `String.format`
- `commonMain/.../feature/video/VideoRouteHostScreen.kt:176` —— `Dispatchers.IO`（internal）
- `iosMain/.../core/platform/PngEncoding.ios.kt` —— **未闭合注释**（语法错）
- `iosMain/.../core/platform/MediaExport.ios.kt` —— 缺 `@OptIn(ExperimentalForeignApi::class)`
- `iosMain/.../feature/player/IosFrameCapture.kt` —— `copyPixelBufferForItemTime` 等 API 名不对

即 iOS 移植尚未收尾。**本轮改动的 7 个文件在 iOS 报错清单里一个都没出现**，
即 iOS 的失败与本轮无关（本轮对 iOS 的正确性只到「前端分析无报错」这一层，
真机/模拟器起播验证需等 iOS 编译先修好）。

---

## 附：关键文件索引

**animeko**
- `app/shared/video-player/src/commonMain/kotlin/ui/VideoScaffold.kt`（脚手架，406）
- `.../ui/PlayerControllerState.kt`（可见性状态机，348）
- `.../ui/progress/PlayerControllerBar.kt`（底栏 + 内置控件，880）
- `.../ui/progress/MediaProgressSlider.kt`（进度条，951）
- `.../ui/progress/MediaProgressIndicatorText.kt`（时间文字，160）
- `.../ui/top/PlayerTopBar.kt`（顶栏，92）
- `.../ui/gesture/PlayerGestureHost.kt`（手势，871）
- `.../ui/gesture/GestureLock.kt` / `ScreenshotButton.kt` / `PlayerFloatingButtonBox.kt`
- `.../ui/VideoSideSheets.kt`（侧栏导航栈，223）
- `.../ui/PlayerFullscreenState.kt`（全屏状态对象，101）
- `app/shared/src/commonMain/kotlin/ui/subject/episode/EpisodeVideo.kt`（播放器装配，996）
- `app/shared/src/commonMain/kotlin/ui/subject/episode/EpisodePage.kt`（页面布局，1334；宽屏 `:459`，窄屏 `:683`）

**本项目**
- `shared/src/commonMain/kotlin/lovehan1me/feature/video/VideoRouteHostScreen.kt`（屏幕边界，1009）
- `.../feature/video/VideoShellContent.kt`（单/双栏分叉，269）
- `.../feature/video/VideoPlayerUi.kt`（播放器主函数，839）
- `.../feature/video/VideoPlayerControls.kt`（`PlayerSlider` / `PlayerMenuChip` / `GestureIndicatorOverlay`，680）
- `.../feature/video/VideoPlayerBottomBar.kt`（底栏两套皮肤，584）
- `.../feature/video/VideoPlayerTopBar.kt`（顶栏两套皮肤，499）
- `.../feature/video/VideoPlayerCenterControls.kt`（中央键/锁钮，293）
- `.../ui/theme/Defaults.kt:161-200`（`PlayerSizes` 数值表）
