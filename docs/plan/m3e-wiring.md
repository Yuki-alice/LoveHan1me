# P1 · M3E 接线台账（活计划）

- 来源：`docs/frontend-design-review-2026-10-09.md` §三 P1（§四为初版台账）。
- 本文件是**唯一活计划**；每接一件更新一件，全部收尾后删除（结论迁回评审报告）。
- 开工：2026-10-09。

## 状态总览

| # | 组件 / 主题 | 落点 | 状态 |
|---|---|---|---|
| 5 | ToggleButton + 连接形状（ButtonGroup 容器不采用，见分件记录） | 视频简介操作区（赞 / 踩） | 已接 2026-10-09 |
| 6 | SplitButton | 下载页主操作（开始全部 + 质量菜单） | 已评估 2026-10-09：不采用（现 IA 无"主操作 + 次级菜单"语义落点，理由见分件记录） |
| 7 | TooltipBox + hover 态（桌面交互基线） | 播放器 9 个图标按钮 tooltip；卡片悬停反馈（核验 = 平台 ripple 状态层，附存在性守卫） | 已接 2026-10-09（hover 自绘撤销；FloatingToolbar 缓做，理由见分件记录） |
| 8 | ShortNavigationBar | 紧凑底栏 Standard 档（A/B；自研悬浮胶囊保留） | 已接 2026-10-09（Standard 档切 M3E 新规范；悬浮胶囊保留为品牌档） |
| 9 | Medium / LargeFlexibleTopAppBar | 二级页标题（含 subtitle 折叠） | 已接 2026-10-09（收藏 / 稍后观看两路由落地） |
| 10 | SegmentedButton（单选 / 多选分段） | 发布日模式取舍（高级搜索弹窗）/ 弹幕类型三选 | 已接 2026-10-09 |
| 11 | ContainedLoadingIndicator + LoadingIndicator 家族对齐 | 播放器缓冲浮层（contained）+ 3 处内嵌裸转圈 | 已接 2026-10-09 |
| 12 | 动效补课 | animateItem() / toggle 弹簧 / 过渡语言统一 | 已接 2026-10-09（animateItem 机制化 + 6 列表迁移；12c 评估维持现状） |
| 13 | Emphasized 字阶铺开 | 标题 / 分区 / Hero 数字 | 已接 2026-10-09（视频页 3 消费点 + 1 字阶映射） |

## 验收协议（每件走完才算「已接」）

1. **意图声明**：视觉零变化 refactor 还是有意的视觉升级；升级需写清新旧差异与理由。
2. **三端编译 + 桌面测试**：`./gradlew --offline :shared:compileKotlinDesktop
   :shared:compileAndroidMain :shared:compileKotlinIosArm64 :shared:desktopTest`。
3. **离屏渲染**：涉及视觉的写 `ImageComposeScene` 渲染测试（PNG 落盘 + 落墨像素防空白断言，明暗双态）。
4. **行为守卫**：涉及规则的做反向验证（注入坏值 → 用例转红 → 还原）。
5. **台账更新**：本表状态 + 证据（测试名 / 报告段）。

## 分件记录

### #5 视频简介操作区（赞 / 踩 → M3E ToggleButton + 连接形状）已接（2026-10-09）

- **原现状**：`VideoRatingButtons`（`feature/video/VideoIntroductionSections.kt`）为自绘 32dp 连体胶囊 ——
  手工掏角（`CornerSize(0.dp)`）、4×`animateColorAsState`、1dp 分隔线；单一调用点 `MetaSection`
  （FlowRow，与 32dp 高的 MetaInfoItem 同排）。
- **实施修正（重要）**：`ButtonGroup` 容器**不采用** —— 锁定的 1.12.0-alpha03 里，空
  `overflowIndicator` 在测量时抛非法参数（`ButtonGroup.kt:725`），官方指示器则常驻一个点不动的
  "空菜单"死按钮（两者均以 `VideoRatingButtonsRenderTest` 渲染实测确认）。本组只有两个固定项、
  外层 FlowRow 保证整行宽度，容器机件无施展空间，故以 `Row` + 官方连接形状组合：
  - 形状：`ButtonGroupDefaults.connectedLeading / TrailingButtonShapes()` —— **选中态自动变全圆
    （CornerFull）**；间距取官方 `ConnectedSpaceBetween`；按压 / 选中形变保留；
  - 颜色：赞 = primary / primaryContainer·0.5α、踩 = error / errorContainer·0.5α，
    未选中 = surfaceContainerHigh·0.72α + onSurfaceVariant（`toggleButtonColors` 逐参定制；
    默认是 Filled 填色键位，不吃）；
  - 尺寸 32dp 与 MetaInfoItem 同高；触控目标与旧实现相当（外层 32dp 高度钳制 M3 命中区机制，
    48dp 兜底并入 P2 触控审计统一处理，本件不声称修复）；
  - 退役：手绘分隔线、四个手写颜色动画（ToggleButton 内置过渡）、手工连接圆角；
  - 新增 `rate_like` / `rate_dislike` 三语字符串 → Icon `contentDescription`（旧「踩」对读屏不可见）。
- **不做**：收藏 / 分享搬进本组 —— 属信息架构决策，另立。
- **证据**：`VideoRatingButtonsRenderTest`（三态 × 明暗 PNG，落墨 18044 采样点）+ 三端编译 + desktopTest。
- **后续**：若上游修复 ButtonGroup 容器溢出行为，可换回容器（`ToggleButton` 侧代码不变）。

### #10 分段按钮（SegmentedButton）已接（2026-10-09）

- **落点 A（单选取舍）**：`AdvancedSearchSheet.kt` 的 `AdvancedSearchReleaseDateDialog` ——
  "具体年月 / 大致范围"原是两个**完全同貌**的 `FilledTonalButton`，选中态零反馈
  （切换只改下半区内容，当前档要靠内容反推）。换 `SingleChoiceSegmentedButtonRow` + `SegmentedButton × 2`；
  抽出 `ReleaseDateModeSelector`（internal，可直接渲染测试）。
- **落点 B（多选过滤）**：`DanmakuSettingsDialog.kt` 的"类型"三 chips（滚动/顶部/底部，三个独立开关）
  → `MultiChoiceSegmentedButtonRow` + `SegmentedButton × 3`；抽出 `DanmakuTypeSelector`（internal）。
- **API 事实（修正初版口径）**：SegmentedButton 系列在锁定版 1.12.0-alpha03 是**稳定 API**
  （`javap -v` 的 RuntimeInvisibleAnnotations 只有 `@Composable`，无实验注解）；且本仓 `:shared` / `:video:ui`
  均为**模块级** opt-in（`build.gradle.kts` 的 `freeCompilerArgs`），新代码无需逐处 `@OptIn`。
  另：`SegmentedButtonDefaults.itemShape(index, count, baseShape)` 的 **baseShape 无默认值**
  （无 `$default` 合成方法，实测），必须显式传 `SegmentedButtonDefaults.baseShape`。
- **尺寸策略**：每段 `Modifier.weight(1f)` 等宽（两个 RowScope 子接口可用）——
  勾选标记出现/消失不引起宽度跳动。
- **证据**：`DanmakuTypeSelectorRenderTest`（三多选态 × 明暗，落墨 80194）、
  `ReleaseDateModeSelectorRenderTest`（两档 × 明暗，落墨 54489）+ 双模块三端编译 + 双模块 desktopTest。

### #11 加载指示器家族（ContainedLoadingIndicator + 内嵌对齐）已接（2026-10-09）

- **落点 A（contained，规格的正用法）**：`VideoLoadingIndicator.kt`（:video:ui）播放器缓冲浮层
  24dp 裸转圈 → `ContainedLoadingIndicator()`。规格依据："容器用于**叠在内容上**的场景"——
  视频画面是任意像素，容器（38dp）给指示器稳定对比底座；参考实现（Han1meViewer `VideoPlayerUi`）同款。
- **落点 B（家族对齐：裸转圈 → 形变 `LoadingIndicator`）**：
  - `FormLoginScreen` 登录按钮内嵌（40dp→20dp，**顺修布局跳动旧账**：原默认 40dp 出现时把按钮撑高）；
  - `CommentScreen` 刷新按钮内嵌（20dp，保留 onSecondaryContainer 色）；
  - `SearchResultsGrid` 列表页脚（24dp）——全仓 `LoadMoreFooter` 家族的最后掉队者。
- **不做（记口径）**：`VideoCommentCard` 回复行 16dp 转圈（尺寸太小、形变不可读，文字已承担语义）；
  `DownloadingCard` 10dp 徽标（过小）；全屏居中转圈（`SitePlaylistScreen` 等属"屏幕级加载"，
  与 LoadingContent 的铺开一并处理，另立批次）。
- **证据**：`VideoLoadingIndicatorRenderTest`（亮/暗两块模拟画面，落墨 52439；PNG 可见形变序列中间帧）
  + 双模块三端编译 + 双模块 desktopTest。

### #7 桌面交互基线（tooltip + hover）已接 · FloatingToolbar 缓做（2026-10-09）

- **落点 A（tooltip，9 处）**：新增 `PlayerTooltipBox`（:video:ui；桌面平台门控 + M3 `PlainTooltip`，
  气泡走 inverseSurface 键位、深色皮肤下"浅底深字"）。接入播放器全部图标按钮：
  - 底栏（`PlayerControllerDefaults`）：播放 / 暂停、弹幕开关、下一集、全屏；
  - 顶栏（`PlayerTopBar` / `PlayerTopBarActions`）：返回、收藏、更多、侧栏开关、主页。
  - 顺修 a11y 旧账：`"Play" / "Pause" / "Enter·Exit Fullscreen" / "Back"` 四处**写死的英文
    contentDescription** 全部资源化（新增 `player_play / player_pause / player_enter_fullscreen /
    player_exit_fullscreen` 三语）。
  - **不做**：`AudioIcon`（悬停已弹音量滑杆，再叠提示会打架）；文字型按钮（倍速 / 画质 / 画面比例
    / 选集自带标签，无识别问题）。
- **落点 B（卡片 hover）—— 核验修正（重要）**：初版给 `VideoCardItem` 自绘了 hover 叠色
  （`onSurface` 8% `compositeOver`），**反向验证当场证伪了它**：把自绘关掉（hover 恒关）重跑，
  差异像素仍有 ≈16.8 万 —— 真相是 **M3 `ripple()`（`LocalIndication` 默认值）本就画 hover
  状态层**（`androidx.compose.material3.internal.ripple.RippleNode` 订阅 `HoverInteraction`，
  `hoveredAlpha ≈ 8%`；解包 jar 全量 grep + 帧差实测双重确认），全仓可点击表面在桌面上
  天然有悬停反馈；自绘只会叠加成 ~16% 双重状态层。**终版：撤销自绘**，保留两件：
  - 可选 `interactionSource` 参数（测试注入用；null = 自持）；
  - `VideoCardHoverRenderTest` 改为**悬停反馈存在性守卫**（"悬停必须有可见状态层变化"）。
- **两次反向验证（已实测）**：
  ① 自绘版关掉 → 差异仍 16.8 万（**证伪自绘的必要性**，促成撤销）；
  ② 终版把生产侧 `indication` 临时置 null → 差异归零（0/0）→ 用例转红 → 还原后 167853/167782 转绿。
- **FloatingToolbar 缓做（设计判断，非遗漏）**：三个候选落点均不成立 ——
  ① 播放器顶/底栏动作已是完整双层结构，套 HFT 要么动作重复、要么等同于改播放器皮肤
  （与「不动播放器皮肤」的克制清单冲突）；② 视频页右栏 / 简介区没有"临时性上下文动作"
  （HFT 的规格语义是 contextual actions，非常驻动作架）；③ 下载页批量操作的前提是"多选模式"，
  属新功能、另立。API 已核验存在，出现真需求（如多选批量操作）时再接。
- **证据**：`PlayerTooltipRenderTest`（桌面 vs `LocalPlatform` 覆写 Android 的门控对照，
  落墨 1373 vs 170）、`VideoCardHoverRenderTest`（状态层帧差 167853/167782 + RV 0/0 转红；
  四张 PNG 已目检）+ 双模块三端编译 + 双模块 desktopTest。

### #6 SplitButton —— 已评估：不采用（2026-10-09）

- **评估结论**：现 IA 中不存在 M3 SplitButton 的语义落点（规范语义 = "单一主操作 +
  **相关**次级项的菜单"，如 Send + Schedule）。逐候选核过：
  - 下载中 Tab 的批量动作 [开始全部 / 暂停全部] 是**对等互斥意图**（"暂停"不是"开始"的变体，
    而是相反动作）；现 FAB menu 两者等权、一次进菜单即可达，拆成 SplitButton 反而把其中一个降级为二级；
  - 已下载页批量栏 [全选 / 移动分组 / 删除] 同为等权动作，删除属破坏性操作不能藏进次级菜单；
  - 视频页下载流程 = 质量选择模态弹窗（含"去官网"分支 + 二次确认），替换为展开菜单是
    信息架构改动（产品决策），不属"接线"。
- **API 留档（已核验）**：锁定版**没有**新版 `SplitButton` 组合函数，只有
  `SplitButtonLayout(leadingButton, trailingButton, modifier, spacing)` 容器 +
  `SplitButtonDefaults.LeadingButton/TrailingButton/Tonal*/Outlined*/Elevated*` 按钮族
  （形状/尺寸全配；未废弃）。真需求（如"快速下载上次画质"）出现时可直接接。

### #8 ShortNavigationBar —— 已接（2026-10-09）

- **落点**：`MainScaffold.kt` 的 Standard 档底栏 `MainNavigationBar`：
  `NavigationBar` + `NavigationBarItem` → M3E `ShortNavigationBar` + `ShortNavigationBarItem`
  （紧凑档新规范：图标胶囊指示器、更矮行高；`ShortNavigationBarDefaultson` 的窗口 inset 处理
  保真沿用原 safeDrawing 单点应用，防 iOS 手势区漏底色）。
- **不改的**：自研悬浮胶囊（`NavBarStyle.Floating`）保留为品牌档；`WideNavigationRail` 分支不动。
  `MainNavigationBar` 由 private 改 internal（渲染测试可见性）。
- **证据**：`ShortNavigationBarRenderTest`（明暗双态 × 双选中项错开，落墨 26479）+ 三端编译 + desktopTest。

### #9 MediumFlexibleTopAppBar —— 已接（2026-10-09）

- **落点**：新增 `HanimeMediumFlexibleTopAppBar`（`ui/component/appbar/HanimeTopAppBar.kt`）——
  M3E `MediumFlexibleTopAppBar` 的项目化封装：大标题随滚动收起（配
  `exitUntilCollapsedScrollBehavior` + Scaffold `nestedScroll`）、**自带 `subtitle` 槽**
  （原"标题下叠 Column 手写副标题"退役）、返回按钮 / 颜色沿用既有约定（pageSurface +
  FilledIconButton）。接入 `VideoGridScreen`（`MyListVideoGridScreen.kt`，服务收藏视频 /
  稍后观看两路由）——其"共 N 支影片"副标题改走 subtitle 槽。
- **证据**：`FlexibleTopAppBarRenderTest`（展开态明暗双态 + 返回/动作槽，落墨 57698）
  + 三端编译 + desktopTest。

### #12 动效补课（12a 机制化 / 12b 注记 / 12c 评估留档）—— 已接（2026-10-09）

**12a —— `animateItem` 全链路，含一次底层机制修正（重要）**：
- **起点**：按评审"观看历史/收藏/评论列表"给 3 个列表手写了 `Modifier.animateItem`。
  **离屏逐帧探针当场证伪**：包在项目 lazy 包装器（`AnimatedLazy.kt`，item 内容外再包一层 Box）
  里的 `animateItem` **静默失效**。对照实验（12 帧 × 16ms 步进，虚拟时钟）：
  foundation 根节点版 `movingFrames=10` + `hasInvalidations=true×12`；
  包装器嵌套版 `movingFrames=1`（仅重排那一帧的静态差异）+ `hasInvalidations=false`。
- **根因**：foundation 通过**根 placeable 的 parentData** 查找动画规格节点
  （`LazyLayoutAnimationSpecsNode`，`javap` 字节码核实 `getParentData→getSpecs` 链路），
  挂在任意内层都找不到 —— 不动画、也不报错。
- **修法**：`AnimatedLazy.kt` 新增逐列表开关 `enableItemPlacementAnimation: Boolean = false`
  （经组合本地值下发到 item 构造处，避免改全部 scope 重载签名），由 wrapper 把规格挂到
  **自己建的 item 根 Box** 上；文件头写明禁令：本封装下"在业务内容里手写 animateItem"永远无效。
  错峰入场（`enableItemAnimation`，全仓未启用）与 placement 同开的边界已在代码注释声明。
- **迁移面（6 列表）**：收藏 / 稍后观看（`VideoGridContent`）、下载中（`DownloadingScreen`）、
  已下载（`DownloadedScreen`，项与组头）、订阅（`SubscriptionContent` —— **存量同款误用**，
  原 `Box(animateItem)` 实为 no-op，一并迁移）、评论（`CommentScreen`）、
  观看历史（`WatchHistoryScreen` 双列表）。`HomeCategoryLayoutDialog` 用的是 foundation
  直挂（本就有效），不动。
- **守卫 + 反向验证**：`LazyItemPlacementAnimationTest` 双用例互为对照 ——
  正向（开启后 `movingFrames ≥ 3`，实跑 10）/ 负向哨兵（不开时 `≤ 1`，实跑 1）；
  反向验证把 wrapper 的 modifier 注入为 `Modifier` → 正用例转红（实测 1 帧，
  断言消息指名"动画没挂到 item 根节点上"）→ 还原复绿。

**12b —— toggle 弹簧（注记收口）**：评分组弹簧已由 #5 的 `ToggleButton` 内建覆盖；
主题切换过渡由 `animateColorScheme`（300ms）承担；收藏图标 / 弹幕开关等图标切换类**不做**
（M3 无强制动画语义，避免为动效而动效）。

**12c —— `animateColorScheme` 评估：维持现状**。理由：48 个角色**共用同一个 `tween(300)`**
（两个调用点 `Theme.kt:77` / `SubjectTheme.kt:72` 均走默认 spec，"语言统一"的前提已成立）；
按"实际消费角色"裁剪（方向 b）需先量化消费面、且会让未覆盖角色在切换时硬跳，收益证据不足，暂缓。

### #13 Emphasized 字阶铺开 —— 已接（2026-10-09）

- **消费点（视频页 3 处）**：
  - `TitleSection` 主标题：`headlineSmall` + 手写 `Bold` → `headlineSmallEmphasized`；
  - `SectionHeader`：`titleLarge` + 手写 `Bold` → `titleLargeEmphasized`；
  - "系列视频"折叠卡头：`titleMedium` + 手写 `SemiBold` → `titleMediumEmphasized`（零视觉变化）。
- `AppTypography` 按既有策略**补一行** `headlineSmallEmphasized → SemiBold`（"新增消费时按同一策略补"）。
- **有意变更口径**：前两处手写 Bold(w700) → 强调档收敛 SemiBold(w600)，统一到项目 CJK 强调取值
  （第三处 w600→w600 零变化）；`headlineSmall + Bold` 的旧组合在全仓消零。
- 余量（后续可续）：`DailyCheckInReport` 年 / 月切换标签等同类 `titleLarge + Bold` 未动（低曝光，避免面散）。
- **证据**：`TitleSectionRenderTest`（主/副标题明暗双态，落墨 14845）+ 三端编译 + desktopTest。
