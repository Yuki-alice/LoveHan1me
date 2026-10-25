# 外部同步改动审阅（2026-10-10）

审阅对象：另一设备推送的两笔提交（叠在本会话 `e459d2a` 之上；同一次推送，CI 只为 head 建 run）。
同步状态：本地 main = origin/main = `a07bba4`（fetch 核验 0/0）。
CI：run `38009610814` **success**（编译三端 + desktopTest）——树级验证已过，但见下文"确认回归"（CI 覆盖不到的时序缺陷）。

---

## 一、`18b25e6` feat(ui): M3E 强调字阶落地 —— AppEmphasis 语义层 + 手写 fontWeight 迁移

**结论：架构方向正确、行高修复是真洞；但提交信息"字重均不变、视觉零变化"不成立（实测约 19 处字重变化）。**

### 做得好的

- **AppEmphasis 语义层**（10 角色：heroTitle/dialogTitle/pageTitle/sectionTitle/groupTitle/counterValue/itemTitle/cardTitle/metricValue/emphasizedBody）把"为什么强调"收进 token，KDoc 完备、含"不要滥用"契约。
- **body 三档强调档行高跟随常规档（22/18）是真修复**：javap 核验 M3E tokens 中常规/强调行高**逐档本就一致**（Body 24/20/16、Title 28/24/20、HeadlineSmall 32、LabelLarge 20 == 各自 Emphasized），错位是项目自己给常规 body 加了 CJK +2sp 却没同步强调档造成的——混排会换行错位，修得对。
- 统一 Bold 的理由成立：各平台 CJK 字体缺 w600 档时 w600 可能落到合成加粗；且迁移主体（22 处手写 Bold）零变化。

### 口径问题（量化）

| 类别 | 数量 | 变化 |
|---|---|---|
| 迁移站点原为 SemiBold(600) | 9 处 | → Bold(700) 加重：WatchHistoryCard、DownloadGroupHeader、DownloadedVideoCard、BatchActionBar 计数、DownloadingItemCard、AppUpdateCard 标题、Getchu 详情价格、Getchu 列表日期、BaseGridConfigDialog 标题 |
| 常规档提级 | 2 处 | **VideoCardItem 卡片标题（titleSmall w500→w700，全站曝光最高的文本）**；PlaylistBottomSheet 标题（w400→w700，M3E 对话框标题要求，合理） |
| 既有强调档消费点静默加重 | 8 处 | AccountScreen×3、VideoIntroductionBlocks×2、TitleSection×1、OpenSourceLicenses×2（600→700） |

注：Android 上 w600 常回退渲染为 w700，delta 或不可见；macOS/桌面（PingFang 等有真 600）delta 可见。

### 遗留小项

- `Type.kt:53` 段落注释仍写"15 档全量收敛 **SemiBold**"，代码是 Bold——注释残留。
- `CategoryBlock` 注释"字号不变、只换字重"表述含混（实际含义应为"字重来源换了"）。
- AppEmphasis 尚有 **8 处直连 `MaterialTheme.typography.*Emphasized` 未收编**（部分有自然映射：TitleSection→heroTitle、AccountScreen username→pageTitle、开源许可→emphasizedBody…）。
- "保留 8 处"与全仓现存 ~17 处 `fontWeight` 覆盖口径对不上（保留类别本身站得住：叠层徽章/色文本/序号徽章/崩溃页/条件字重）；`AppUpdateCard:95` 仍留 SemiBold，与"避开 w600"论证自相矛盾。
- 无新增守卫（AppEmphasis 映射 / 15 档字重不变量均无断言）。

---

## 二、`a07bba4` perf(ui): 主题过渡 48→1 + 动效收编 motionScheme

**结论：VerticalBounce 修复正确、档位替换语义正确；但 AnimateColorScheme 重写引入已实测确认的回归。**

### 做得好的

- `VerticalBounce` 补 `remember`（Animatable 缺记忆 → 每次重组重建、回弹被重置）——真 bug，修法正确。
- `HomeCategoryLayoutDialog`：拖拽缩放→`fastSpatialSpec()`、`animateItem` 换位→`defaultSpatialSpec()`——spatial 域（位置/尺寸），语义正确。
- 48→1 对账无误（旧 48 个 `animateColorAsState` → 新 48 个 `lerp`），"重组次数不变、削减每帧计算量"的说明诚实。

### ❌ AnimateColorScheme 回归（虚拟时钟探针实测）

探针：`shared/src/desktopTest/kotlin/lovehan1me/ui/theme/AnimateColorSchemeProbeTest.kt`（未跟踪，只打印；跑法 `:shared:desktopTest --tests "lovehan1me.ui.theme.AnimateColorSchemeProbeTest"`）。

| 场景 | 期望 | 实测 |
|---|---|---|
| 静置后切换（最常见路径） | 逐帧过渡 | 首帧即终态（F8F8F8→121212，**0 过渡帧**）——**动画整体失效** |
| 过渡中打断 | 以当前显示值为起点、不跳变 | 121212→233A8D，**jump=123**（B 通道）——跳变 |

**根因**：`from = lerpColorScheme(from, targetColorScheme, progress.value)` 用**新目标**当插值终点去重建"当前显示值"——
- 静置时（进度=1）：`from := 新目标` → 动画塌缩为"新目标→新目标"的空动画 = 硬切；
- 打断时：`from := mix(旧起点, 新目标, 旧进度)`，既不是显示值也不连续。

**最小修复**（shape 已推演验证；返回侧也用 `to` 可同时消灭"目标已变、effect 未跑"窗口的一帧闪烁）：

```kotlin
val progress = remember { Animatable(1f) }
var from by remember { mutableStateOf(targetColorScheme) }
var to by remember { mutableStateOf(targetColorScheme) }
LaunchedEffect(targetColorScheme) {
    from = if (progress.value >= 1f) to else lerpColorScheme(from, to, progress.value)
    to = targetColorScheme            // 先捕获显示值，再更新目标
    progress.snapTo(0f)
    progress.animateTo(1f, animationSpec)
}
val fraction = progress.value
return if (fraction >= 1f) to else lerpColorScheme(from, to, fraction)
```

**语义建议**：颜色过渡用了 `slowSpatialSpec()`；M3E 语义 color/opacity 属 **effects** 域（md3e design-tokens：`slowEffectsSpec` = "Full-screen content refresh"，正对应全屏主题过渡），建议换 `slowEffectsSpec()`。

**影响面**：`Theme.kt:77`（全局换槽位/深浅/对比度/AMOLED）与 `SubjectTheme.kt:72`。
顺带：`Theme.kt:74-76` 注释仍描述"48 个 animateColorAsState"（过时）。

---

## 三、下一步建议（按优先级）

1. ~~P0 修 `animateColorScheme`~~ **✅ 已落地（2026-10-10 上午）**：`from` / `to` 双 state + 先捕获后更新（返回侧恒用 `lerp(from, to, fraction)`，不用裸参数，顺带消灭 effect 前一帧闪烁）；spec 换 `slowEffectsSpec()`（md3e design-tokens：颜色属 effects 域）；补调用方契约 KDoc（ColorScheme 按引用比，必须 remember 稳定实例——已核验两个调用点满足）。探针转正为守卫 `AnimateColorSchemeTransitionTest`：静置切换 14 个中间帧逐帧推进 / 打断 jump=32（= 单步步进量）≤48；**反向验证**：注入坏公式 → 两用例按预期断言转红 → 还原复绿。`Theme.kt` 过时注释同步修正。改动在工作树未提交。
2. 口径与注释小收尾：`Type.kt:53` / `Theme.kt:74-76` / CategoryBlock 注释；确认"统一 Bold"的设计接受度并记录（含 19 处加重是否接受）。
3. AppEmphasis 续迁 8 处直连消费点 + 补"15 档 Emphasized 全 Bold"不变量守卫。
4. ~~颜色过渡 spec 换 effects 档~~ ✅ 已随 P0 一并落地（`slowEffectsSpec()`）。
5. 回到评审报告 P2 主线（骨架屏铺开等）。
