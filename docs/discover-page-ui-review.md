# 发现页 UI 设计评价与改进意见

> 评审对象：发现页（`MainTab.Discover` → `SearchRoute` + `SearchScreen`）及其筛选器家族。
> 复刻预览：`discover-page-preview.html`（8 个状态：空态 / 结果态 / 无结果态 / 高级搜索弹窗 / 单选对话框 / 多选对话框 / 发布日期对话框 / 宽屏常驻筛选栏，含浅深双主题）。
> 依据：M3 / M3 Expressive 设计规范与官方组件样式标准；组件规格值从项目锁定的 `material3 1.5.0-alpha25` aar 字节码核验；配色由 `material-color-utilities 0.2.6` 对默认「樱」槽种子 `#F596AA`（TonalSpot）复算。

## 1. 页面结构（复刻结论）

| 区域 | 实现 | 说明 |
|---|---|---|
| 顶栏 | `SearchAppBar` | 自绘 BasicTextField + `surfaceContainerHigh` 单容器 + `extraLargeIncreased`(32dp) 圆角 + 4dp 阴影；tab 模式无返回箭头；漏斗按钮 = `FilledIconButton`（56dp） |
| 条件条 | `ActiveSearchCriteria` / `CollapsibleSearchCriteria` | FlowRow 装 AssistChip（点 = 移除该条件，「重置」收口）；随滚动经自定义 Layout 折叠 |
| 结果区 | `SearchResultsGrid` + `VideoCardItem` | `GridCells.Adaptive`；卡片 16:9 封面 + 已观看徽标 + 播放量/时长 scrim + 两行强调标题；分页 footer 为 M3E `LoadingIndicator` |
| 状态 | `SearchStateIndicator` / `EmptyContent` | 骨架屏 + 趣味文案 / 空态 / 错误态（带原因）；下拉刷新 |
| 高级筛选 | `AdvancedSearchSheet` | ModalBottomSheet（禁用 HalfExpanded）：历史组合 → 预设 → 6 维度块 → tips → 底部常驻动作区 |
| 维度对话框 | SingleChoice / MultiChoice / ReleaseDate | 单选列表 / 7 scope Tab + 分类搜索 + FilterChip 网格 + 模糊开关 / SegmentedButton + 年月滚轮 |
| 宽屏 | `AdvancedSearchSidePanel` | 内容宽 ≥900dp：左 240dp 常驻栏（单列）+ 右侧结果；与弹窗共用 `FilterBlockSpec` |
| 导航壳 | `MainScaffold` | `ShortNavigationBar`（紧凑）/ `WideNavigationRail`（Medium 折叠 80dp、ExtraLarge 展开 220dp） |

## 2. 总体评价

这是项目里**设计系统执行力最强**的页面：色板全部由 materialkolor 从种子色现场算（三端同源）、间距/圆角/字阶全部 token 化、筛选器做到「6 维度 + 预设 + 历史 + 模糊配对」完整闭环、窄屏（ModalBottomSheet）与宽屏（240dp 常驻栏）双形态零重复代码。

问题集中在**官方组件语义的替换**与**若干规格偏离**，多数可在不动信息架构的前提下修复。

## 3. 做得好的地方（保持）

1. **token 纪律**：间距唯一真源 `HanimeDefaults.Spacing`（4/8/12/16/24/32）；圆角显式阶梯 `AppShapes`（4/8/12/16/20/28/32/48）；强调字阶 15 档全量 Bold 并收敛到 `AppEmphasis` 语义层，业务层不直写 typography。
2. **tonal 优先**：选中态统一 `secondaryContainer/onSecondaryContainer` tonal 配对，不用 alpha 叠加；搜索栏已从「双 Surface 叠 tint」收敛为单容器。
3. **动效走 motionScheme**：历史列表进出场、chip 颜色切换、按压形变插值全部走 `fastEffectsSpec/fastSpatialSpec`。
4. **对比度实测全部过 AA**（见 §6 表，最低 6.50:1；outline 4.27:1 属非文本 3:1 档，通过）。
5. **无障碍基本盘**：`SelectableTag` 用官方 FilterChip（自带 Role.Checkbox + ✓ 前导）；haptic 反馈统一收口。
6. **状态完备**：骨架屏 / 空态 / 错误态（带原因）/ 下拉刷新 / 分页 footer 均有专门实现。
7. **M3E 选型正确处**：`PrimaryScrollableTabRow` 分类页、`SingleChoiceSegmentedButtonRow` 日期模式切换（P1 #10：旧版两个同貌按钮无选中反馈，已修）、`Switch` + 两行说明、`ShortNavigationBar` / `WideNavigationRail`。
8. **修复可追溯**：发布日期 chip 曾误绑 `onClearDuration` 等修复都留在注释里。

## 4. 改进意见（按优先级）

### P0 — 组件语义错配 / 规格偏离（影响可发现性与无障碍）

**1. 已选条件条：AssistChip → FilterChip（selected + trailing ✕）**
- 问题：点 chip = 移除该条件，但 AssistChip 无 ✕ 提示、无 selected 语义，TalkBack 读不出「已选中/可清除」。
- 依据：M3 chip 家族分工——AssistChip = 点击触发动作；FilterChip = 选中/取消选中（selected 容器即 secondaryContainer，与现配色一致）。
- 改法：`FilterChip(selected = true, onClick = onClearX, label = …, trailingIcon = { Icon(ic_close) })`，切换零视觉回归；「重置」保留 AssistChip（它确实是动作）。

**2. 高级筛选维度块：补 ✓ 前导（或改 FilterChip / ListItem 规格）**
- 问题：`AdvancedSearchChip` 选中仅换容器色，无任何勾选标记；色觉障碍 / 高对比度用户不可辨。
- 依据：M3 FilterChip selected 必有 ✓ leading icon；M3E 规范「状态不得仅靠颜色传达」。
- 改法：选中时补 18dp ✓ 前导；或按 M3 两行 ListItem 规格（72dp：headline = 维度名 / supporting = 值 / trailing = ✕）重构，天然获得官方形态与触控目标。

**3. 顶栏搜索栏自绘且超规格：≈80dp vs 官方 SearchBar 56dp**
- 问题：实测高度 = 8(容器) + 4(行) + 56(漏斗钮) + 4 + 8 ≈ 80dp，比 M3E SearchBar（`SearchBarTokens.ContainerHeight = 56dp`，字节码核验）高 43%；缺 leading 放大镜图标（M3 SearchBar 强制 leading icon）；clear 按钮在输入框外。
- 改法：首选迁移 M3E `SearchBar / DockedSearchBar`（自带 56dp、full 圆角、展开动画与 suggestions 槽）；保留自绘则至少补 leading icon、clear 内移、高度压到 56dp。

### P1 — 信息架构与交互流程

**4. Sheet 首屏信息序：核心筛选维度被历史/预设挤到首屏外**
- 依据：M3 bottom sheet 内容应「任务优先」+ 渐进披露。
- 改法：6 个维度块上移并加区标题（「筛选条件」）；历史组合收成一行入口（「最近使用 ▾」）或移到结果页条件条附近；预设保留一行横滑。

**5. 维度顺序两处不一致**
- 结果条件条序（类型/标签/排序/日期/时长/品牌/模糊）≠ Sheet blocks 序（类型/排序/标签/品牌/日期/时长）。同一组信息两种序列，用户每次重新扫描。统一为同一顺序（建议按站点字段序或使用频率）。

**6. 多选标签对话框改全屏对话框（full-screen dialog）**
- 依据：M3 对话框指南——简短确认用 AlertDialog；复杂任务（长列表 + 多步操作）用全屏对话框（头部标题 + 全高内容 + 底部动作）。当前 7 tab + 分类搜索 + 200+ 选项 + 开关塞在 312dp 宽的 dialog 里，pager 可视区仅约 320dp 高。

**7. 单选列表：图标与容器两处规格问题**
- ① 未选项容器 = `HanimeDefaults.Colors.card` = surfaceContainerHigh，与 AlertDialog 底（`DialogTokens.ContainerColor = SurfaceContainerHigh`，字节码核验）同色，行边界完全不可见；
- ② 单选用 check_circle（多选视觉语言）。
- 改法：未选项给 `surfaceContainerLowest` 或 1dp outlineVariant 边框；单选改 M3 `RadioButton`。

**8. 年月滚轮：打开停在 1990 + 无吸附/居中高亮**
- 问题：`WheelLikeColumn` 的 LazyColumn 无初始滚动位置，弹窗打开时列表在首项（1990），当前选中值（默认今年 2026）在 37 项之外，用户看不到当前值；也没有 wheel 惯用的「选中行居中 + 上下渐隐 + 点击居中」。
- 改法：`rememberLazyListState(initialFirstVisibleItemIndex = indexOf(selected))` + 居中高亮带 + 滚动吸附；或年份加「最近 5 年」快捷跳转。

**9. 空态 / 错误态补恢复动作**
- 依据：M3 empty state 规范 = 插画 + 标题 + 说明 + 动作按钮。
- 改法：空态给「重置筛选 / 修改关键词」TextButton，错误态给「重试」，走已有 HapticTextButton。

**10. 长按清除的可发现性**
- 项目自己在预设区注释里承认「同一块界面再叠一层长按语义会让用户不敢点」，但维度块仍用长按清除，仅靠底部 tips 一行小字告知。建议选中态直接露出 ✕（与第 2 条一并解决），长按退为快捷方式；tips 同步中文化。

### P2 — 文案与细节打磨

**11. 文案本地化与准确性**
- ① `search_options_tips` 中英混排（「Tips:长按某单元…所有tag」）→「提示：长按某个维度可清除该维度已选的全部标签」；
- ② `switch_to_year`「切换至年」→「仅按年筛选」；
- ③ 未选时值显示「全部」，但服务端空参可能有默认排序——建议显示「站点默认」或具体默认值名（需与 wire 行为核对）；
- ④ 历史卡条件串「 · 」连接 2 行截断，建议 chips 化或点击展开。

**12. 动效与细节**
- ① 条件条进出无过渡（`if (filter.isNotEmpty())` 直接装卸，清掉最后一个条件时整条瞬闪）——建议 AnimatedVisibility + motionScheme；
- ② ModalBottomSheet 禁用 HalfExpanded，内容多时无法「半展开瞄一眼」，建议放开；
- ③ Sheet 内层再包 `Surface`（默认 surface 色）属冗余一层，与搜索栏刚修掉的「双 Surface 叠 tint」同型，建议直接给 ModalBottomSheet 内容上色；
- ④ dialog 内「重置」与「取消」同槽相邻，重置是即时生效的破坏性操作，建议移出或加 error 色 / 二次确认。

**13. 两列维度块的值截断**
- 390dp 屏两列时块宽约 150dp，「发布日期: 2026 年 3 月」必截断（组件注释已承认）。改法：值允许 2 行（minLines=2 + maxLines=2）保信息完整，或窄屏改单列（需权衡一屏可见维度数）。

## 5. 关键规格核验记录（字节码，非记忆值）

| 规格 | 值 | 来源 |
|---|---|---|
| M3E SearchBar 容器高 | 56dp | `SearchBarTokens.ContainerHeight` |
| Medium IconButton 容器 / 图标 | 56dp / 24dp（CornerFull） | `MediumIconButtonTokens` |
| AssistChip / FilterChip 容器高 | 32dp（CornerSmall） | `AssistChipTokens` / `FilterChipTokens` |
| FilterChip 默认容器色 | 未选 `SurfaceContainerLow` + OutlineVariant 边；选中 `SecondaryContainer` | `FilterChipTokens` |
| AlertDialog 容器 / 圆角 | `SurfaceContainerHigh` / CornerExtraLarge(28dp) | `DialogTokens` |
| Baseline 按钮高 | 40dp | `BaselineButtonTokens` |
| ListItem（M3 规范） | 一行 56 / 两行 72 / 三行 88dp | M3 规范 |

## 6. 配色与对比度实测（樱 · 浅色）

由 `material-color-utilities 0.2.6` 对种子 `#F596AA`（TonalSpot、contrast 0.0）复算：

| 角色 | 色值 | 发现页用途 | 对比度 |
|---|---|---|---|
| primary / onPrimary | `#8d4959` / `#ffffff` | 漏斗按钮、滚轮选中项、主按钮 | 6.50:1 ✅ |
| secondaryContainer / onSecondaryContainer | `#ffd9df` / `#5b3f44` | 已选 chip、选中维度块、单选选中行、底栏指示器 | 7.25:1 ✅ |
| surfaceContainerHigh | `#f5e4e6` | 搜索栏、未选维度块、对话框容器 | — |
| surfaceContainerLow | `#fff0f1` | 视频卡、Sheet、宽屏常驻栏底 | — |
| surfaceContainerLowest | `#ffffff` | 页面底色 | — |
| onSurface / onSurfaceVariant | `#22191b` / `#524345` | 标题 / 副信息 | 16.37:1 / 8.91:1 ✅ |
| outlineVariant | `#d6c2c4` | 分隔线、未选 FilterChip 边框 | — |

深色方案全套角色齐备，无硬编码色散落；预览 HTML 可切换深浅验证。

## 7. 复刻映射（预览 ↔ 源码）

| 预览元素 | 源码 |
|---|---|
| 搜索栏 / 漏斗按钮 | `SearchAppBar.kt` |
| 条件条 + 滚动折叠 | `CollapsibleSearchCriteria.kt` |
| 结果网格 / 卡片 / 分页 | `SearchResultsGrid.kt` + `VideoCardItem.kt` |
| 空态 / 错误态 / 骨架屏 | `SearchStateIndicator.kt` + `EmptyContent.kt` |
| 高级搜索弹窗 | `AdvancedSearchSheet.kt` |
| 预设区 / 命名对话框 | `SearchPresetSection.kt` + `SearchFilterPresetStore.kt` |
| 单选 / 多选 / 日期对话框 | `AdvancedSearchSheet.kt`（+ `WheelLikeColumn.kt`） |
| 宽屏常驻筛选栏 | `AdvancedSearchSidePanel` + `SearchRoute.kt`（≥900dp 分支） |
| 底栏 / 侧栏 | `MainScaffold.kt` |
