# LoveHan1me 前端设计评审（MD3 / M3E 专项）

- **日期**：2026-10-09
- **范围**：`shared/`（commonMain 全部 UI，409 个 Kotlin 文件）+ `app/navigation/` 导航壳 + `ui/theme` / `ui/component` / `ui/adaptive` / `ui/transition` / `ui/refresh` / `ui/preview` 设计基建。`feature/*` 12 个功能域做了逐目录盘点。
- **方法**：全量源码扫描 + **依赖制品级 API 核验**（对 `org.jetbrains.compose.material3:material3-desktop:1.12.0-alpha03` 逐字节码核验 M3E 组件可用性）+ 复核项目自带审计资产（ThemeAuditScreen / ThemeColorAuditTest / Skeleton / 守卫测试）。
- **结论先行**：整体 **A-（88/100）**。设计基建达到"教科书级"；P0 是三处点状一致性漏网；P1 的主线是**把已就位的 M3E 骨骼长出肌肉**——8 件 M3E 组合组件 + 4 项字阶/动效铺开，全部已核验在当前锁定版本可落地。

---

## 一、分项评分

| 维度 | 评级 | 一句话结论 |
|---|---|---|
| 色彩系统 | A+ | materialkolor 现场色算 · 12 槽位 · AMOLED · 对比度档 · 审计测试。全 commonMain 仅 3 个文件出现硬编码色 |
| 形状系统 | A | 与 M3 1.5 默认逐档对齐 + 按压形变（M3E 哲学的项目级延伸）。6 处散装圆角漏网 |
| 动效体系 | A- | motionScheme 四档集中定义、12 个文件铺开；`animateColorScheme` 语言游离、`animateItem()` 仅 2 处 |
| 字阶排版 | B+ | CJK 行高修正 + Emphasized 策略有据；但存在阶梯外字号、硬编码字重，Emphasized 仅 2 处消费 |
| 响应式 / 多端 | A- | 窗口/内容宽度"双链"设计、五档断点、WideNavigationRail；桌面 hover / tooltip 缺位 |
| 组件体系 | B+ | 基座组件完整统一；M3E 组合件（ButtonGroup 等 8 件）尚未接线 |
| 无障碍 | B | contentDescription 纪律好；滚轮语义缺失、liveRegion 缺位、无 fontScale 回归清单 |
| 性能意识 | A+ | tick 状态隔离、流级订阅收敛、骨架单动画、实例记忆——项目级差异化优势 |
| M3E 采用广度 | B- | 主题/动效/单件已用；组合组件族与 Emphasized 铺开是下半场 |

（相对评级，参照对象为"生产级 KMP 三端应用"。）

---

## 二、已经做对的事（含证据）

### 2.1 色彩系统 —— 全项目最强的一环

- **真三端现场色算**：materialkolor 5.0.1（SPEC_2025），commonMain 内直接算 HCT 展开色（`ThemeBoards.kt:143-176`），三端零差异。
- **12 槽位的配方设计**：11 命名 + 1 系统；彩色族表面固定 TonalSpot（色度约 6，"有颜色但不吵"）、强调保留槽位风格；灰阶族整套手调感；系统族接 Android 壁纸取色（`ThemeBoards.kt:32-108`）。**"表面/强调双 scheme 合并"**（`withNeutralSurfaces`，`ThemeBoards.kt:186-210`）是比"直接吃 dynamicColorScheme"高一层的设计决策。
- **AMOLED 与对比度**是与深浅正交的独立开关（`ThemeBoards.kt:226-238`）；对比度直通 materialkolor（`Theme.kt:61`）。
- **切换不硬切**：48 个角色逐帧渐变（`AnimateColorScheme.kt:20-74`），且为 ColorScheme 实例做了 remember 防止过渡期每帧新建实例（`Theme.kt:63-71`）。
- **硬编码纪律**：全 commonMain 只有 3 个文件出现 `Color(0x…)` —— `ThemeBoards.kt`（种子色 5 处，合理）、`NetworkSettingsScreen.kt`（延迟色阶 6 处，**待收敛，见 P0-1**）、`VideoCardItem.kt`（封面遮罩 1 处，装饰性可接受）。
- **审计资产**：`ThemeAuditScreen`（把角色/对比度渲染出来）+ `ThemeColorAuditTest`（把"线上算出什么颜色"变成可测断言）。同类项目里几乎见不到。

> 设计水准例证：`Defaults.kt:172-183` 的注释明确主张"同一个导航角色在两种窗口宽度下必须是同一个色"，并为此把宽屏 Rail 的硬编码 `surfaceContainerLow` 收回成 `surfaceContainer`。这种自觉正是 MD3 语境色系统的核心精神。

### 2.2 形状系统 —— M3E 按压形变已上桌

- `AppShapes` 显式对齐 M3 1.5 默认（4/8/12/16/20/28/32/48，含 `largeIncreased`/`extraLargeIncreased` 档），并声明为全项目圆角唯一入口（`Shape.kt:24-44`）。
- **`shapeByInteraction` + `InterpolatedCornerShape`**：按压时圆角从常态插值到 `pressedShape`，动画走 `motionScheme`（`Shape.kt:48-114`）。这是 M3E `ButtonShapes` 哲学的项目级延伸——比"自绘 ripple"高级，且比 M3E 官方按键更广泛地应用在卡片/视频卡上。
- 补齐了 M3 `Shapes` 未暴露的档：`Corners.contentSheet`（起始侧 28dp，宽屏内容 sheet 专用）、`Corners.pill`（percent=50，`Defaults.kt:87-119`）。
- **漏网 6 处**（与"唯一入口"宣称不符，见 P0-3）：`ArtistDetailScreen.kt:144`（20dp）/`:325`（50，与 pill 语义重复）/`:349`（12dp）/`:370`（8dp）；`DanmakuMatchPickerDialog.kt:252`（8dp）；`AppearancePickers.kt:277`（12dp）。

### 2.3 动效体系 —— 语言统一，覆盖面有留白

- `MaterialExpressiveTheme`（默认 expressive `MotionScheme`）统一挂载（`Theme.kt:30-37`）。
- `Motion.kt` 集中定义页面级转场，且**每一档都对应 M3E 的语义档位**：页面位移走 `slowSpatialSpec()`（整屏位移的正确档位）、进场 `defaultEffectsSpec()`、退场 `fastEffectsSpec()`；注释明确交代了"淘汰 M2 的 tween+手写错峰"的决策（`Motion.kt:18-48`）。
- motionScheme 已铺开 **12 个文件**（AnimatedLazy stagger、TagChipGroup、SettingItem、MainScaffold 悬浮胶囊、骨架屏等），非弹簧语言是少数派且都有注释理由（骨架 shimmer 用 `tween(LinearEasing)`、滚动条淡出用 tween——都合理）。
- **播放器专属决策值得点名**：弹幕层显隐用 `animateFloatAsState`(alpha) 而非 `AnimatedVisibility`，注释写明是为避免 `wrapContentSize` 把弹幕层裁到左上的坑（`VideoPlayerShell.kt:725-733`）——动画手段的选择基于渲染行为而非习惯。
- 留白：见 P1-12（`animateItem()`、toggle 弹簧、`animateColorScheme` 语言统一）。

### 2.4 响应式与多端适配 —— "双链"设计是加分项

- 五档断点（350/600/840/1200/1600）与 M3 2026 window size class 命名对齐（`Breakpoints.kt:18-31`）。
- **窗口宽度 vs 内容宽度双链**：chrome（Bar/Rail）由窗口档决策；布局（列数/边距/双栏）由**扣除 Rail 后的内容宽度**决策（`WindowSize.kt:13-20`、`MainScaffold.kt:94-97`）。这规避了"840dp 窗口 + 220dp Rail 仍按整窗宽排 4 列"的经典错误，比裸用 `WindowSizeClass` 高一层。
- `WideNavigationRail` 折叠/展开用 `snapTo` 防拖拽跨档时动画方向打架（`MainScaffold.kt:334-357`）；全屏路由显式摘 chrome（`MainScaffold.kt:409-441`）。
- `ContentColumn` 限宽（formMax 640 / readingMax 720）补齐了"宽屏行宽失控"的问题，且注释带 M3 可读性依据（40–60 字符/行，`Defaults.kt:41-54`）。
- 留白：桌面端 hover/tooltip 缺位（见 P1-7）。✅ 2026-10-09 处理完毕：播放器 9 个图标按钮 tooltip（桌面门控）+ 卡片悬停反馈**核验后确认本已由 M3 `ripple()` 状态层提供**（本条"缺位"结论修正 —— 当时只 grep 了 `hoverable`，漏了指示层；未新增自绘，防双重状态层；见活台账 #7）。

### 2.5 性能与状态 —— 项目级差异化优势

- **`PlaybackUiState` 组合期投影**（剔除逐 tick 的 position/buffered），从架构上消灭"整页逐帧重组"（`PlaybackController.kt:67-154`）。
- B4：主题流只订阅四量，改弹幕字号不重组整树（`Theme.kt:48-49`）。
- 骨架屏全屏共享一个 shimmer 动画（`Skeleton.kt:41-45`，注释直指"8 卡 × 3 块 = 24 个动画"的问题）。
- 主题过渡期 ColorScheme 实例记忆（`Theme.kt:63-71`）。
- 这些是"设计系统能长期存活"的底层保障，建议在评审结论里与视觉资产同等权重看待。

### 2.6 工程文化 —— 注释即决策记录

`Shape.kt`/`Defaults.kt`/`Theme.kt`/`Motion.kt` 的注释均包含"为什么是 T94 不是 T98""为什么 slow 档""为什么不用 AV"级别的论证。设计评审角度看，这比任何单个组件都值钱：**它让下一轮迭代有据可依、可反向验证**。

---

## 三、差距与优化建议

### P0 —— 一致性硬伤（建议本周清零；均为点状收敛，改动小）

| # | 问题 | 证据 | 修法 |
|---|---|---|---|
| 1 | 延迟色阶硬编码三色，绕过语义色且无暗色适配 | `NetworkSettingsScreen.kt:575-577`、`:627-629`（`0xFF4CAF50`/`0xFFFFC107`/`0xFFF44336`） | 在 `HanimeDefaults.Colors` 增补 `success`/`warning` 扩展位（浅色取深档、深色取浅档），换用后把三色纳入 `ThemeColorAuditTest` 深浅双模断言 |
| 2 | 阶梯外字号：设置行主标题 18sp（titleMedium 16 → titleLarge 22 之间插入） | `SettingItem.kt:133` | 回归 `titleMedium`（密度更合适）；若坚持 18sp，则在 `AppTypography` 显式定义，不许存在于调用点 |
| 3 | 散装圆角 6 处 | 见 §2.2 漏网清单 | 20→`shapes.largeIncreased`；50→`Corners.pill`；12→`shapes.medium`；8→`shapes.small` |
| 4 | 硬编码 `FontWeight.SemiBold`（4 处） | `AccountScreen.kt:325/379/445/598` | 改用 Emphasized 字阶（正好是 M3E 资产）；同时给标题两档补 CJK"强调加半档"处理，与 `Type.kt` 现有策略一致 |

附注（顺手项）：`AnnouncementCard.kt:84` 的 `bodySmall.copy(fontSize = 12.sp)` 是冗余 copy（bodySmall 本就是 12sp），建议删。

**落地状态（2026-10-09 晚）：✅ 四项已实现并验证。**
- `NetworkSettingsScreen` 6 处硬编码色换用语义 token（快/中 → 新补充色 `HanimeDefaults.Colors.success / warning`；慢 → `colorScheme.error`）——补充色为浅深固定双档 + `isDarkScheme()` 亮度判定，走 `HanimeDefaults` 唯一入口；
- 18sp 阶梯外字号与两处冗余覆盖（`FontWeight.Medium` / `fontSize = 12.sp`）清除；
- 6 处散装圆角收敛到 `MaterialTheme.shapes.*` / `Corners.pill`；
- `AccountScreen` 换 Emphasized 字阶；`AppTypography` 将消费到的 title 两档强调收敛为 SemiBold（M3E 逐档字重经字节码核实：titleMedium / labelLarge 为 w700、其余 w500；收敛依据 = 中文可辨 + 视觉零变化）。
- 新增 `SemanticColorsTest` 钉四档对固定锚底色的 AA 对比度，**已反向验证**（注入坏值 → 2 用例转红 → 还原转绿）；`ThemeColorAuditTest` 增第 6 节打印段。
- 验证命令：`./gradlew --offline :shared:compileKotlinDesktop :shared:compileAndroidMain :shared:compileKotlinIosArm64 :shared:desktopTest` —— 三端编译绿；desktopTest 614 用例 / 0 失败 / 4 跳过。

### P1 —— M3E 表达力扩展（"充分发挥 md3e"的主战场）

**接线表（全部已在锁定版本 `material3-desktop:1.12.0-alpha03` 中核验存在）：**

| # | M3E 组件 | 建议落点 | 现状与收益 |
|---|---|---|---|
| 5 | `ToggleButton` + `ButtonGroup`（含溢出） | 视频简介区操作行（赞/踩/藏/享） | 现为自绘 `toggleable` + `animateColorAsState`（`VideoIntroductionSections.kt:365-386`）；换后自动获得 M3E 形状层、弹簧与组规范 |
| 6 | `SplitButton` | 下载页主操作（"开始全部" + 质量/并发菜单） | ❌ 已评估（2026-10-09）：**不采用** —— 现 IA 不存在"单一主操作 + 相关次级菜单"语义落点（批量动作为等权互斥；质量选择是模态决策弹窗），理由与 API 可用性核验详见活台账 |
| 7 | `TooltipBox` + hover 态（`HorizontalFloatingToolbar` 缓做） | 播放器 9 个图标按钮 tooltip + 卡片悬停反馈 | ✅ 已接线（2026-10-09）：tooltip 桌面门控 + 播放器全图标覆盖；**卡片悬停反馈经核验本已由 M3 `ripple()` 的 hover 状态层提供**（自绘会叠加成双重状态层，已撤销；附存在性守卫测试 + 反向验证）；HFT 三候选落点均不成立，留待真需求 |
| 8 | `ShortNavigationBar` | 紧凑底栏 Standard 档 | ✅ 已接线（2026-10-09）：`MainScaffold` 的 Standard 档 `NavigationBar` → `ShortNavigationBar`（紧凑指示器）；悬浮胶囊保留为品牌档；见活台账 |
| 9 | `MediumFlexibleTopAppBar` / `LargeFlexibleTopAppBar` | 二级页标题区（详情/评论/系列） | ✅ 已接线（2026-10-09）：新增 `HanimeMediumFlexibleTopAppBar`（大标题滚动收起 + subtitle 槽），落地收藏 / 稍后观看两路由；见活台账 |
| 10 | `SegmentedButton`（单选取舍 / 多选过滤） | 高级搜索"具体年月/大致范围"；弹幕类型三选（滚动/顶/底） | ✅ 已接线（2026-10-09）：两处选择器抽出为 internal 组件并配离屏渲染测试；详见活台账分件记录 |
| 11 | `ContainedLoadingIndicator` + `LoadingIndicator` 家族对齐 | 播放器缓冲浮层（contained，叠在内容上的规格正用法）；登录按钮 / 评论刷新 / 搜索页脚 3 处裸转圈 → 形变指示器 | ✅ 已接线（2026-10-09）：详见活台账分件记录 |
| 12a | 动效补课：`animateItem()` | 观看历史/收藏/评论列表增删改 | ✅ 已接线（2026-10-09）：**机制化落地** —— 逐帧探针证实"手写在包装器内容里的 animateItem 静默失效"（根因 = foundation 经根 placeable parentData 查规格），改为 wrapper 逐列表开关（规格挂 item 根节点），迁 6 列表（含存量误用 1 处）；正/负双用例守卫 + 反向验证，详见活台账 |
| 12b | 动效补课：toggle 状态弹簧 | 收藏图标、弹幕开关、主题切换 | ✅ 已覆盖 / 评估留档：评分组弹簧由 #5 `ToggleButton` 内建；主题切换过渡由 `animateColorScheme` 承担；图标切换类不做（无强制动画语义） |
| 12c | `animateColorScheme` 语言统一 | `AnimateColorScheme.kt:22` | ✅ 已评估（2026-10-09）：**维持现状** —— 48 角色共用同一个 `tween(300)`（两个调用点均默认 spec，"语言统一"前提本就成立）；逐角色裁剪收益证据不足，暂缓 |
| 13 | Emphasized 字阶铺开 | 视频标题、分区标题、Hero 数字（连签/成就） | ✅ 已接线（2026-10-09）：视频页 3 消费点（TitleSection / SectionHeader / 系列视频卡头）+ `AppTypography` 补 `headlineSmallEmphasized` 映射；配 `TitleSectionRenderTest`；见活台账 |

### P2 —— 打磨项

14. **骨架屏铺开**：`Skeleton` 质量高但目前只服务搜索首屏（`SearchStateIndicator.kt`）；首页分类块、订阅、简介、评论、播放页首屏都还是转圈。
15. **无障碍补丁**：`WheelLikeColumn` 加 `selectableGroup` + `stateDescription`；`BaseGridConfigDialog.kt:232/287` 中文字面量 contentDescription 资源化；`PageContent`/`LoadMoreFooter` 加 `liveRegion = Polite`（加载完成/到底/报错可被播报）。
16. **魔法数收敛**：`SharedHomeScreen.kt:86`（72dp）、`WatchHistoryScreen.kt:437/530`（64dp）、`VideoViewModel.kt:73`（250dp）、`DailyCheckInReport.kt:293-296`、`VideoCardItem.kt:94-95`（12sp/14dp）→ `HanimeDefaults` 或文件级命名常量。
17. **减弱动效开关**：桌面/iOS 不跟随系统动画缩放；`InterfaceInteraction` 设置页可加（Android 侧读 `MotionDurationScale` 自然跟随）。
18. **fontScale=2.0 截图回归清单**：热力图 10sp/14dp 格、日历 50dp 单元格、`VideoCardItem` 角标 10-12sp 是优先抽查对象。
19. **MaterialShapes 细节**：已有 1 处先例（`OpenSourceLicensesScreen.kt:411` Cookie7Sided）；空态底座、签到成就徽章可再 1-2 处，克制使用。
20. **预览补强**：`@PreviewSizeClasses`/`@PreviewComponent` 基建就绪，但 shared 内实际屏幕级预览仅 `SettingItemPreview` 一处。给 5 个关键组件 + 2 个屏幕铺预览，成本极低。

---

## 四、M3E 接线台账（初版；2026-10-09 起逐件接线，**活台账**：`docs/plan/m3e-wiring.md`）

| 状态 | 资产 | 备注 |
|---|---|---|
| ✅ 已接线 | `MaterialExpressiveTheme` / `MotionScheme`（四档 spec 有集中定义） | `Theme.kt:30`、`Motion.kt` |
| ✅ 已接线 | `LoadingIndicator` / `FloatingActionButtonMenu` / `WideNavigationRail` | `LoadingContent.kt`、`DownloadScreen.kt`、`MainScaffold.kt` |
| ✅ 已接线 | `ButtonShapes`/`IconButtonShapes` + 自研 `shapeByInteraction` | `Defaults.kt:185-204`、`Shape.kt` |
| ✅ 已接线（2026-10-09） | `ToggleButton` + 官方连接形状（`connectedLeading/Trailing` + `ConnectedSpaceBetween`） | 视频简介赞 / 踩组；`ButtonGroup` 容器因 alpha 缺陷不采用（空指示器崩溃 / 官方指示器常驻死按钮），见活台账 |
| ✅ 已接线（2026-10-09） | `SegmentedButton`（SingleChoice + MultiChoice 两型） | 发布日模式取舍（高级搜索）+ 弹幕类型三选；两处选择器抽出为 internal 并配离屏渲染测试，见活台账 |
| ✅ 已接线（2026-10-09） | `ContainedLoadingIndicator`（+ `LoadingIndicator` 家族对齐 3 处） | 播放器缓冲浮层（叠在视频上的规格正用法）；登录按钮 / 评论刷新 / 搜索页脚裸转圈 → 形变指示器 |
| ✅ 已接线（2026-10-09） | `TooltipBox` / `PlainTooltip`（桌面门控的 `PlayerTooltipBox`） | 播放器 9 图标按钮 tooltip；卡片悬停反馈核验 = M3 `ripple()` 自带 hover 状态层（未自绘，附存在性守卫）；门控对照与帧差测试见活台账 #7 |
| ✅ 已接线（2026-10-09） | `ShortNavigationBar` + `ShortNavigationBarItem` | Standard 档底栏（`MainScaffold`）；悬浮胶囊保留为品牌档，见活台账 #8 |
| ✅ 已接线（2026-10-09） | `MediumFlexibleTopAppBar`（封装 `HanimeMediumFlexibleTopAppBar`）+ `exitUntilCollapsedScrollBehavior` | 收藏 / 稍后观看大标题（subtitle 槽 + 滚动收起），见活台账 #9 |
| ✅ 已接线（2026-10-09） | `Modifier.animateItem`（wrapper 机制化开关，6 列表） | 空间连续性；嵌套失效根因与修法（根 placeable parentData）见活台账 #12 |
| ◐ 半接线 | `MaterialShapes`（1 处） | 扩散即可，无需新引入 |
| ❌ 已评估不采用 | `SplitButton`（现 IA 无语义落点，见台账 #6）；`HorizontalFloatingToolbar`（候选落点均不成立，缓做，见台账 #7） | 评估留档，真需求出现后可接 |

---

## 五、不建议做的事（克制清单）

1. **不要动播放器皮肤**：播放器控件强制深色（`VideoPlayerShell.kt:240-247`）+ 自绘进度条是正确工程决策，不因"M3E 一致性"回退成浅色 M3 控件。
2. **不引入自定义字体**：项目已有明确决策（`Type.kt:18-19`，许可/体积/三端加载为独立议题）。若要品牌感，优先用 emphasized 字阶与 weight 层次，而不是字体族。
3. **MaterialShapes 不要滥用**：cookie 形状是点缀资产，超出空态/成就/加载形态的范围会立刻显得玩具化。
4. **不要在紧凑档强推 SideSheet/双栏**：现有"悬浮胶囊 + 全屏路由"的移动端形态与 M3 自适应规范一致，不要为了"更 M3"而加复杂度。

---

## 六、附录：核查证据

**源码统计（全 commonMain，排除 reference/）**

| 指标 | 结果 |
|---|---|
| Kotlin 文件 | 409 |
| `Color(0x…)` 硬编码 | 3 个文件：ThemeBoards(5)、NetworkSettingsScreen(6)、VideoCardItem(1) |
| `motionScheme` 引用 | 12 个文件 |
| `hoverable` | 1 个文件（BannerCarousel） |
| `animateItem()` | 2 处 |
| Emphasized 字阶消费 | 2 处（OpenSourceLicensesScreen） |
| `@Preview` 屏幕级落点 | 1 处（SettingItemPreview）；基建 4 文件 |
| 散装圆角（数值形） | 6 处（§2.2 清单） |
| 硬编码 fontSize | 10sp/11sp/12sp/18sp/20sp/22sp/36sp 共 13 处（多为角标/emoji，P0-2 与 P2-18 重点） |

**制品核验**（`material3-desktop-1.12.0-alpha03.jar` 字节码字符串，非类名猜测——Kotlin 顶层函数不落类名，必须查 `*Kt.class`）：
- 存在：`MediumFlexibleTopAppBar`、`LargeFlexibleTopAppBar`、`MediumTopAppBar`、`LargeTopAppBar`、`CenterAlignedTopAppBar`、`ButtonGroup*`（含 Overflow/Menu 状态）、`ToggleButton*`、`SplitButton*`、`FloatingToolbar*`（水平/垂直/FAB 位）、`ShortNavigationBar*`、`ContainedLoadingIndicator`、`SwipeToDismissBox*`、`MaterialShapes`、`MotionScheme`、15 档 `*Emphasized` Typography 字段、`MaterialExpressiveTheme`（签名含 `motionScheme` 参数）。
- 第二轮补充核验（2026-10-09）：`SplitButton` **组合函数不存在**——只有 `SplitButtonLayout(leadingButton, trailingButton, modifier, spacing)` 容器 + `SplitButtonDefaults.LeadingButton/TrailingButton/Tonal*/Outlined*/Elevated*` 按钮族（未废弃）；`Medium·LargeFlexibleTopAppBar` 自带 `subtitle: (@Composable () -> Unit)?` 槽 + `collapsedHeight/expandedHeight` + 走 `TopAppBarDefaults.topAppBarColors`；`TopAppBarDefaults.exitUntilCollapsedScrollBehavior` 存在；`ShortNavigationBar` 族含 `ShortNavigationBarItemDefaults`、`NavigationItemIconPosition(Top/Start)`、`ShortNavigationBarArrangement(EqualWeight/Centered)`。
- foundation 机制核验（`foundation-desktop-1.12.0.jar`，`javap` + 逐帧实测）：`Modifier.animateItem` 的动画规格经**根 placeable 的 parentData** 查找（`LazyLayoutAnimationSpecsNode implements ParentDataModifierNode`；`LazyLayoutItemAnimator$ItemInfo` 走 `getParentData(measuredItem, i) → getSpecs(...)`）——**挂内层 = 静默 no-op**（根节点版实测 moving=10 / 嵌套版 moving=1）。
- 实验性与 opt-in 口径（2026-10-09 修正）：`ContainedLoadingIndicator` / `LoadingIndicator` / `FlexibleTopAppBar`
  等属 `ExperimentalMaterial3ExpressiveApi`；而 `SegmentedButton` 系列**已是稳定 API**（无实验注解）。
  另：本仓 `:shared` 与 `:video:ui` 均为**模块级** opt-in（`build.gradle.kts` 的 `freeCompilerArgs`），
  新代码无需逐处 `@OptIn`（初版"全部为实验 API、需文件级 opt-in"的表述过宽，以此为准）。

**关键文件索引**：`ui/theme/{Theme,ThemeBoards,Type,Shape,Motion,Defaults,AnimateColorScheme}.kt`；`ui/adaptive/{Breakpoints,WindowSize,PageMetrics}.kt`；`ui/component/{SettingItem,VideoCardItem,SettingsSegments,content/Skeleton,appbar/HanimeTopAppBar}.kt`；`app/navigation/main/MainScaffold.kt`；`feature/video/{VideoPlayerShell,VideoIntroductionSections}.kt`；`feature/settings/{NetworkSettingsScreen,ThemeAuditScreen,AppearancePickers}.kt`。

---

## 七、建议的下三个动作

1. 开一张 `docs/plan/` 活计划：P0 四项 + P1 的 #5/#6/#7（视频页与桌面端优先，用户感知最强）。
2. 建立"**M3E 组件接线台账**"（§四即初版），每接一件：截图对比（ImageComposeScene 离屏渲染）+ 反向验证一次。
3. 把 `ThemeAuditScreen` 扩成**设计系统总控台**：色彩 + 形状 + 动效 spec + M3E 接线进度，一处肉眼验收。

> 本文是评审快照（非 `docs/plan/` 活计划、非 `docs/evidence/` 取证件）。行号引用以 2026-10-09 工作树为准；代码改动后需重新核对。
