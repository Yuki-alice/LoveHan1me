# 决定

每条格式：**决定** / 依据 / **失效条件** / 重认日期。
依据只写可核的代码位置或实测记录；拿不到依据的判断不进本文件。

---

- **网关是"候选出口"之一，不是必经的一层劫持。** 它必须能回答"现在该不该用它"，
  并且在被阻断时退得动、退得快。
  依据：网关被阻断后用户原本可用的系统代理被整个绕开；`CloudflareCdp.kt` 的 `proxyFlag()`
  KDoc 自己写明"网关在跑时出口 = 本机直连"。
  **失效条件**：站点侧不再对明文 SNI 做阻断、或 ECH 不再是唯一可用手段时，本条的紧迫性消失。
  **重认日期**：2026-12-28。

- **网关与代理的优先级规则：无可用代理时网关优先；有可用代理时网关仍先试一次，
  失败即熔断并让位于代理。** 目标是同时保住"免梯直连"的体验与"配了代理就必须能用"的底线。
  依据：`values-zh-rCN/strings.xml` 的 `use_ech_gate` 文案自称「免代理直连」，
  即它是代理的替代品而非叠加层，而代码里没有互斥判定。
  **失效条件**：站点侧主动放行本客户端、或代理不再是用户的主要出口手段时。
  **重认日期**：2026-12-28。

- **`useEchGate` 的默认值改为"条件默认开"：仅在用户没有配置可用代理时默认开启。**
  依据：存储默认 `true`（`AppSettings`）与代理默认 `System`（同文件）叠加，
  等于默认状态下两者同时生效，而 `EchGatePolicy.rewrite` 不检查代理设置。
  **失效条件**：网关的失败识别与熔断被证明足够可靠，以至于"默认开"不再有代价时，可退回无条件默认开。
  **重认日期**：2026-12-28。

- **失败识别必须覆盖"连上了但被阻断"，不能只看传输层异常。** 回退判据要包含
  403、`you have been blocked`、`Just a moment` 这几类"看起来像站点问题"的响应。
  依据：`EchGateInterceptor` 此前只在 `IOException` 与 `502 + "echgate:"` 两种情况下回退，
  403 会一路走到 `NetworkRepo.throwRequestException` 被解释成"IP 被封 / 请验证"。
  **失效条件**：站点不再下发挑战或屏蔽页时。
  **重认日期**：2026-12-28。

- **不引入任何中转服务端，网关是且只是本机回环进程。** 这是身份而非取舍，改判需重写定位。
  依据：`POSITIONING.md` 的红线段落写明"流量也不经任何中转服务器"。
  **失效条件**：不适用（身份条款）。
  **重认日期**：2026-12-28。

- **主题色算走 `materialkolor` 运行时方案，真三端（Android/Desktop/iOS）在 commonMain 内算色，
  弃用"预生成色表 + Android-only `m3color`"的旧架构。** 对齐 animeko 的 `AniTheme` 设计：
  运行时 seed→HCT→ColorScheme、平滑主题切换、语义色 token。
  依据：
  - `shared/src/commonMain/kotlin/lovehan1me/ui/theme/ThemeBoards.kt:8` 引入 `com.materialkolor.dynamicColorScheme`，
    `:124` 的 `boardColorScheme(...)` 在 commonMain 内调用它（不再依赖安卓 `m3color` 离线表）；
  - `shared/src/commonMain/kotlin/lovehan1me/ui/theme/AnimateColorScheme.kt:20` 的 `animateColorScheme(...)`
    用 `animateColorAsState` + `tween(300)` 做平滑切换（旧方案是硬切）；
  - `shared/src/commonMain/kotlin/lovehan1me/ui/theme/Defaults.kt:169` 的 `Bars` 提供导航 chrome 语义色
    （对齐 animeko `AniThemeDefaults`；同批加的 `Cards` / `topAppBarColors()` 因零调用已删除，见下条）；
  - 已删除 `GeneratedThemeBoards.kt`、`desktopApp/.../GenThemeBoards.kt`、`GeneratedBoardsTest.kt`
    与 `Main.kt` 里的 `runGenBoardsIfRequested()` 调用；
  - 依赖重定向：`build.gradle.kts` 用 `module("com.github.ajalt:colormath").replacedBy(...)`，
    `settings.gradle.kts` 把 jitpack `exclusiveContent` 正则改成 `com\.github\.(?!ajalt).+`，
    让 materialkolor 5.0.1 误报的 `com.github.ajalt:colormath` 落到 Maven Central 解析。
  三端编译已验证：`:shared:compileKotlinMetadata`/`:compileKotlinDesktop`/`:compileAndroidMain`/
  `:compileKotlinIosSimulatorArm64`/`:compileKotlinIosArm64` 均 BUILD SUCCESSFUL，
  `:shared:desktopTest` 与 `:shared:testAndroidHostTest` 通过。
  **失效条件**：materialkolor 停更且无法跟上 CMP/M3-Expressive 工具链、或官方 `MaterialExpressiveTheme`
  原生提供等价真三端运行时色算且更省依赖时，可回退。
  **重认日期**：2027-03-29。

- **命名主题槽位定为 12 个，按色相环排序；`PaletteStyle` 枚举只保留槽位真正用到的 5 个值。**
  动机：原 8 槽里 4 个彩色槽共用同一配方（只是色相不同），色相覆盖缺橙红与紫两段；
  而 `PaletteStyle` 抄了 8 个值却只有 2 个被使用，属于误导性死配置。
  依据：
  - `shared/src/commonMain/kotlin/lovehan1me/ui/theme/ThemeBoards.kt:32` 的 `ThemeBoard`
    现有 11 个命名槽 + 1 个系统槽，新增紫 `Fuji`(`:73`)、橙 `Kaki`(`:53`)、高彩度 `Kasumi`
    (`:78`，走 `Vibrant`)、品牌忠实 `Honmei`(`:48`，走 `Content`)；
  - 同文件 `:93` 的「墨」由 `Neutral` 改为 `Monochrome` —— materialkolor 里 `Neutral` 的定义是
    "比单色**略微**有色"，名"纯灰"实不符；
  - `shared/src/commonMain/kotlin/lovehan1me/core/domain/model/AppSettings.kt:40` 的
    `PaletteStyle` 收窄为 `TonalSpot / Neutral / Monochrome / Vibrant / Content`；
  - 同时删掉了 `ThemeBoard` 上从不被读取的 `secondArgb` / `thirdArgb`（旧预生成表遗留）。
  **失效条件**：用户反馈"槽位太多难选"、或某槽位长期无人使用需回收时，重新收窄槽位数。
  **重认日期**：2027-03-29。

- **详情页「封面取色」是页面级临时换肤，默认关闭，失败一律静默回退。**
  依据：
  - `shared/src/commonMain/kotlin/lovehan1me/core/domain/model/AppSettings.kt:116` 的
    `dynamicSubjectTheme` 默认 `false`（老用户零视觉变化）；
  - `shared/src/commonMain/kotlin/lovehan1me/ui/theme/SubjectTheme.kt:40` 的 `SubjectThemeOverride`
    在取不到种子色时原样透传 `content`，`:114` 的 `extractSeedColor` 在纯黑白封面下返回 null；
  - 取色复用列表封面同一条图片管线（`SubjectTheme.kt:76` 的 `rememberCoverSeedColor` 调
    `rememberHanimeImageLoader`），保证与页面上显示的那张封面走同一出口；
  - 三端像素转换在 `SubjectTheme.kt:100` 声明为 expect，各端 actual 见
    `shared/src/{androidMain,desktopMain,iosMain}/kotlin/lovehan1me/ui/theme/CoverImageBitmap.*.kt`；
  - 接线点在 `shared/src/commonMain/kotlin/lovehan1me/feature/video/VideoRouteHostScreen.kt:762`。
  五目标编译 + 两套单测已验证（`:app:compileDebugKotlin`、`:desktopApp:compileKotlin` 亦通过）。
  **失效条件**：若页面级换肤被证明干扰用户对全局主题的预期，或取色在大量真实封面上
  产出不可用配色（过灰/过艳），则降级为"仅当开关打开且种子彩度达标才生效"或撤下该功能。
  **重认日期**：2027-03-29。

- **彩色槽位的表面（中性）家族固定取 `TonalSpot`，不再取 `Neutral`；导航 chrome 底色与
  页面底色分别定死 `surfaceContainer` / `surfaceContainerLowest`。**
  动机：导航栏"发灰"的根因是中性源色度过低，不是明度不够 —— 实测 `Neutral` 的中性调色板
  色度约 2、`TonalSpot` 约 6，两者 tone 完全相同（都是 94）。
  依据（可用 `./gradlew :shared:desktopTest --tests "lovehan1me.ui.theme.ThemeColorAuditTest"`
  重跑，输出在 `${java.io.tmpdir}/theme-audit.txt`）：
  - 改前「樱」导航栏 `#F4ECEC` HCT 色度 2.0 / 次要文字 `#4A4646` 色度 1.7；
    改后 `#FBEAEB` 色度 5.8 / `#524345` 色度 8.2 —— 明度不变，染上色了；
  - `shared/src/commonMain/kotlin/lovehan1me/ui/theme/ThemeBoards.kt:143` 的
    `boardColorSchemeValue(...)` 是配色内核（非 `@Composable`，供审计直接调用），
    `:156` 附近按"表面固定 TonalSpot"合并；style 本身即 TonalSpot 的槽位直接返回，不再白算一遍；
  - `shared/src/commonMain/kotlin/lovehan1me/ui/theme/Defaults.kt:178` 的
    `Bars.navigationContainerColor` = `surfaceContainer`；此前宽屏分支硬编码
    `surfaceContainerLow`，同一导航角色在紧凑底栏（M3 默认 `surfaceContainer`）与宽屏
    Rail 之间差一档，宽窗口 1600dp 起还会再变一次；
  - `shared/src/commonMain/kotlin/lovehan1me/ui/theme/Defaults.kt:145` 的 `Colors.pageSurface`
    由 `surface` 改为 `surfaceContainerLowest`：chrome 与内容的 tone 差从 4 提到 6；
  - 两个 `ModalBottomSheet` 曾挂在 `pageSurface` 上，页面底变纯白后会与身后内容同色，
    已改为显式 `surfaceContainerLow`（`MyplayListBottomSheet.kt` / `PreviewCommentRoute.kt`）；
  - 死 token 同批清理：`Bars.pageContentBackgroundColor`（与 `Colors.pageSurface` 重复）、
    `Bars.topAppBarColors()`（与 `HanimeTopAppBar` 自带的一份冲突：`scrolledContainerColor`
    一个取 `surfaceContainer`、一个取 `surfaceContainerHigh`）、整个 `Cards` 对象（与
    `Colors.card` / `Colors.homeVideoCard` 取值重复）—— 三者均零调用。
  **失效条件**：若 TonalSpot 的表面染色在某个槽位上被判定"太艳"、需要按槽位回调中性源时，
  改为可配置而非全局固定。`雾`（`Neutral`）与`墨`（`Monochrome`）两个刻意的灰阶槽不受本条影响。
  **重认日期**：2027-03-29。

- **对比度设置收敛为两档（`standard` / `high`），删掉实测负收益的 `medium`；语义定位为无障碍
  而非外观装饰。**
  动机：`medium`（spec 0.5）在浅色下把 `onPrimaryContainer` / `primaryContainer` 的对比率
  从标准档的 7.2 **降到** 5.2，是"名义提高对比度、实际降低关键配对"的负收益档。
  依据（重跑命令同上，同一份审计输出第 3~5 节）：
  - 三种子（樱/藤/苍）× 深浅实测，`medium` 的该配对一律降到 5.2 / 6.6，非种子特例；
  - 标准档正文（`onSurface`/`surface`）已 16.4，远超 WCAG AAA 的 7.0 —— 开关的价值不在正文，
    而在次要文字（`onSurfaceVariant`/`surface`：8.9 → 20.0）与控件边界；
  - `high` 会把 `onSurface` 与 `onSurfaceVariant` 压成同一色（浅色 `#000000`/`#000000`，
    深色 `#FFFFFF`/`#FFFFFF`），文字层级消失 —— 这是已知取舍，故只在无障碍档暴露；
  - 三档下 `surface` 恒为 `#FFF8F7`、`surfaceContainer` 仅 94→92→90，所以它**不是**
    "调背景明暗"的开关，文案不得这样描述；
  - `shared/src/commonMain/kotlin/lovehan1me/core/domain/model/AppSettings.kt:96` 的
    `ContrastLevel` 现只有 `Standard(0.0)` / `High(1.0)`。
  **失效条件**：materialkolor 更新 ColorSpec 使 `medium` 变得单调且无负收益时，可加回三档。
  **重认日期**：2027-03-29。

- **深色模式选择改为单选组形态（圆点 + 左对齐文字），文案为「浅色 / 深色 / 自动」，并新增
  专用文案键 `theme_mode_*`（不复用 `follow_system`）。**
  动机：`follow_system` 同时被**语言**选择器复用（`HomeSettingsScreen.kt:259`、
  `OnboardingWizard.kt:340`），在那里它表示"跟随系统语言"；把它的值改成"自动"会连带
  改掉语言选择器的语义。故主题模式另立三个键，顺序由 跟随系统/总是关闭/总是开启
  改为 浅色/深色/自动（两个明确档在前，唯一的"跟随外部"档放末尾）。
  依据：
  - `shared/src/commonMain/kotlin/lovehan1me/feature/settings/AppearancePickers.kt:112` 的
    `DarkModePicker` 现在只读 `theme_mode_light/dark/auto` 三个键；
  - 同文件 `:268` 用 `Role.RadioButton`、`:180` 用 `selectableGroup()` 表达"三选一"；
    选中态由 `RadioButton` 承担，卡片**不再**加 3dp 主色描边 —— 描边与圆点双指示会互相打架；
  - `shared/src/commonMain/composeResources/values-zh-rCN/strings.xml:496` 起为三个新键；
    `always_off` / `always_on` 已无引用，从 composeResources 与 `app/src/main/res` 一并删除
    （两处均无 `R.string.*` / `@string/*` 引用）；
  - `shared/src/commonMain/kotlin/lovehan1me/feature/onboarding/OnboardingWizard.kt:372`
    的开屏主题选择同步改用同一组文案 —— 同一设置项在两处不得文案分叉。
  **失效条件**：若"自动"被判定不如"跟随系统"表意清晰（例如被误读为自动亮度），
  只需改 `theme_mode_auto` 的值，键名与结构不动。
  **重认日期**：2027-03-29。

- **内容 sheet 的圆角覆盖整个起始侧（`topStart` + `bottomStart` 28dp），末端两角保持直角。**
  动机：此前只圆左上。"sheet 贴窗口右半边、另外三角落在屏幕边缘"这条推理对**末端两角**
  成立，对**左下角**不成立 —— 左下角贴着导航 chrome，只圆左上会让同一块 sheet 一半圆
  一半方，并在 chrome 一侧留出一个直角缺口。
  依据：
  - `shared/src/commonMain/kotlin/lovehan1me/ui/theme/Defaults.kt:108` 的 `contentSheet`
    现为 `RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)`；
  - 两个使用点同规：`shared/src/commonMain/kotlin/lovehan1me/app/navigation/main/MainScaffold.kt:161`
    （首页宽屏）与 `shared/src/commonMain/kotlin/lovehan1me/app/navigation/settings/SettingsTwoPaneHost.kt:377`
    （设置双栏右栏）；两者都位于 `fillMaxHeight()` 的 Row 内、底部与窗口同高，
    所以左下角是**可见边缘**而非屏幕边缘。
  **失效条件**：若宽屏改为让 sheet 与窗口底边/侧边留出外边距（浮动卡片式），
  则四个角都落在可见边缘，应改用全角（`shapes.extraLarge`）。
  **重认日期**：2027-03-29。

- **下拉刷新只认触摸；桌面端的刷新改由快捷键（F5 / Ctrl+R / Cmd+R）承担。门控按「最近一次
  实际用到的指针类型」判定，不按平台。**
  动机：桌面没有下拉手势，而 M3 的下拉刷新判定只看滚动来源、不看指针类型 —— 鼠标滚轮在
  列表顶部继续上推（过卷）会被当成"下拉"，指示器被整块拉出且因滚轮没有抬手/fling 而**挂在
  屏幕上不回弹**。按平台门控会让触屏笔记本/平板一起失效，故按指针类型。
  依据（重跑命令、环境、输入可达性表见 `docs/evidence/2026-09-29-下拉刷新指针类型门控.md`）：
  - 库层无指针类型检查：`PullToRefreshModifierNode.onPostScroll` 只比较 `NestedScrollSource.UserInput`，
    而桌面滚轮的唯一路径 `MouseWheelScrollingLogic` 派发的正是 `UserInput`（两者均经 javap 核实）；
  - 门控在 `shared/src/commonMain/kotlin/lovehan1me/ui/component/HanimePullRefreshBox.kt:122`
    的 `TouchOnlyConnection.onPostScroll`：非 `UserInput`、或最近指针非 `Touch`、或位移非正方向，
    一律不消费；挂载序见同文件 `:93`（门控必须挂在 M3 的**内层**，反了则位移先被 M3 吃掉）；
  - 端到端两轮对照（同一取证文档第三节，可重跑）：过卷输入下门控**关** `distanceFraction = 2.0`、
    门控**开** `0.0`；触摸下拉两轮都触发 `onRefresh` 1 次；
  - 指针类型在全 App 范围被观察：`shared/src/commonMain/kotlin/lovehan1me/app/App.kt:147` 建
    `ActiveInputSourceState`，`:150` `LocalActiveInputSource provides` 它，`:159` 在内容**之上**挂
    `trackActiveInputSource` —— 此前该 CompositionLocal 从未被 provides，照抄门控会把触摸一起挡掉；
  - 快捷键入口挂在**窗口**级而非组合内：`desktopApp/src/main/kotlin/lovehan1me/desktop/Main.kt:178`
    的 `Window(onKeyEvent = ...)` 经 `:180` 的 `isPageRefreshShortcut` 判定后于 `:182` 调
    `refreshHub.refresh()`（焦点可能落在搜索框里，组合内监听不到）；判定函数见
    `shared/src/commonMain/kotlin/lovehan1me/ui/refresh/PageRefreshHub.kt:59`；
  - 派发用**栈**而非单值：`PageRefreshHub.kt:18` 只认最后登记的页面，转场期间栈顶页面退出后
    自动回落到仍在屏上的下一张；
  - 7 个可刷新页面统一改用 `HanimePullRefreshBox`（旧的自绘 `ui/component/PullRefreshOverlay.kt` 已删除），
    指示器观感归一。
  已核（2026-09-29）：`:shared:desktopTest` 371 用例 0 失败（含新增 7 条回归）；
  三端编译（Android / 桌面 / iOS Arm64 + Simulator）+ 四模块 `desktopTest` / `testAndroidHostTest`
  全 BUILD SUCCESSFUL。
  **失效条件**：Compose 改变滚轮/拖动的嵌套滚动派发方式（例如让鼠标拖动也走 `UserInput`）时，
  上面第一、二条依据要重测；若 M3 官方改为按指针类型门控，`TouchOnlyConnection` 可整体撤下。
  **重认日期**：2027-03-29。
