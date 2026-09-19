# 漫画 Tab 设计（基于站点实测结构）

> 实测日期：2026-09-18。全部结论来自对 `hanimeone.me` 的 curl 直连抓取与 DOM 结构解析，
> 未引用任何既有文档。文中"未定"项均明确标注，未做推测填充。

---

## 一、结构事实（实测）

### A. 入口与 IA

| 能力 | 端点 | 每页条数 | 分页 | 备注 |
|---|---|---|---|---|
| 列表 | `/comics?page=N` | 36 卡 | 有 | 单一扁平网格，**没有分区块的漫画首页** |
| 搜索 | `/comics/search?query=X&page=N` | 30 卡 | 有 | 表单仅 `name="query"` 一个字段 |
| 分类索引 | `/comics/{tags,parodies,groups,characters,artists}` | — | **无** | 单页全量，446KB / 656KB / 3.1MB / 2.4MB / 4.3MB |
| 分类筛选 | `/tags/X`、`/artists/X`、`/languages/X`、`/categories/X` | 30 卡 | 有 | **纯漫画**（`/watch` 链接数 = 0），与视频侧分类机制不同源 |
| 详情 | `/comic/{id}` | — | — | 无 CF 挑战 |
| 阅读 | `/comic/{id}/{n}` | — | — | n = 1..N，扁平序列 |

其它：
- 漫画侧**没有"首页"**。`/comics` 本身就是列表，站点没有提供 banner / 推荐行 / 分区块。
- 五个分类索引页内部链接指向 `/tags/X` 这类**不带 `/comics` 前缀**的路由。
- 列表页**无排序、无筛选控件**（`<select>`/`<option>` 计数为 0）。

站点导航条（实测）——漫画侧的全局入口，含与视频并列的一项：

| 导航项 | 路由 |
|---|---|
| 隨機推薦 | （无 href，`comic-random-nav-item`） |
| 標籤 | `/comics/tags` |
| 作者 | `/comics/artists` |
| 角色 | `/comics/characters` |
| 同人 | `/comics/parodies` |
| 社團 | `/comics/groups` |
| H動漫 | 视频侧 |

→ 站点自己的导航就把漫画与视频**并列**，这直接支持"漫画作第 4 个一级 tab"。
→ 这 6 项也正好可以作为漫画 tab 首页的分类入口清单。

### B. 卡片模型（列表 / 搜索 / 分类筛选 / 详情页相关作品，四类入口共用同一张卡）

```
div.comic-rows-videos-div
└── a[href=/comic/{id}]
    ├── img.lazy[src=占位图][data-srcset=t{n}.nhentai.net/galleries/{gid}/cover.{webp|jpg}]
    └── div > div.comic-rows-videos-title     ← 标题
```

- 卡片字段**只有三样**：`comicId` + 封面（含 `gid`）+ 标题。**没有页数、没有标签徽章。**
- 封面是懒加载：真实地址在 `data-srcset`，`src` 只是占位图 → **解析必须读 `data-srcset`**。
- 封面主机分布（一页 36 张样本）：`t2.nhentai.net` × 35，`img4.qy0.ru` × 1 → 存在**两种封面来源**。

### C. 详情页模型

```
div.comics-panel-margin.comics-panel-padding
└── div.row
    ├── div.col-md-4 > a[href=/comic/{id}/1] > img[data-srcset=t{n}.nhentai.net/galleries/{gid}/cover.jpg]
    └── div.col-md-8
        ├── h3.title.comics-metadata-top-row > span.before + span.pretty + span.after
        ├── h4.title.comics-metadata-margin-top > span.before + span.pretty + span.after
        └── div.comics-metadata-margin-top
            ├── h5  無链接  #67399                               作品号徽章
            ├── h5  標籤：  a[href=/tags/X]         × 8           标签
            ├── h5  作者：  a[href=/artists/X]      × 1           作者
            ├── h5  語言：  a[href=/languages/X]    × 2           语言
            ├── h5  分類：  a[href=/categories/X]   × 1           分类
            ├── h5  頁數：  纯文本（2 位数字）                    页数
            └── h5  上傳：  纯文本，**相对时间**（形如 "3年前"）   上传时间
        └── div.comics-metadata-margin-top
            ├── button.no-select                        操作（图标 favorite）
            └── a[href=nhentai.net/g/{id}/download]     原站下载入口
```

**字段清单**

固定字段（每个作品都有）：

| 分组标签 | 形态 | 链接 |
|---|---|---|
| `#67399` | 作品号徽章 | 无 |
| `標籤：` | 多条 | → `/tags/X` |
| `作者：` | 1 条 | → `/artists/X` |
| `語言：` | 多条 | → `/languages/X` |
| `分類：` | 1 条 | → `/categories/X` |
| `頁數：` | 纯数字文本 | 无 |
| `上傳：` | 相对时间文本 | 无 |

**条件字段（仅当作品有对应数据时才出现）**：

| 分组标签 | 链接 | 实测样本中的条数 |
|---|---|---|
| `角色：` | → `/characters/X` | 2 条 / 6 条 |
| `同人：` | → `/parodies/X` | 1 条 |
| `社團：` | → `/groups/X` | 1 条 |

> ⚠️ **这是本文档的一处自我更正。**
> 初版写的是"`角色`/`同人`/`社團` 不在详情页出现，只是导航里的全局索引"——**这是错的**。
> 错因：只枚举了 2 部作品的 metadata 分组（67399 / 100671），而这两部恰好都不带这三个字段，
> 就下了"不存在"的结论。**n=2 不足以判定字段不存在。**
> 修正依据：抽样 20 部（`/comics` 第 1-2 页）的元数据区，共命中 7 个这类分组，
> 标签分别实测为 `角色：` / `同人：` / `社團：`，与导航用词一致。

关键推论（修正后）：

1. **详情页字段集不是固定的**：7 个固定字段 + 3 个条件字段。
   → **元数据必须按"有序分组列表"建模**（见第四节），不能写死字段集。
   → 这也是初版分组列表方案的真正理由，而我中途把它改成具名字段是**过早收敛**。
2. **上传时间是相对时间字符串，全页无 `<time datetime>`**（实测 `datetime=` 属性数 = 0）。
   → 拿不到绝对时间戳 → **无法按上传时间做精确排序或做"最近更新"筛选**。
   只能原样展示，或按相对时间做粗粒度（今天 / 本周 / 更早）。

两个必须记住的解析坑：

1. **标题是三段式** `span.before + span.pretty + span.after`。只能取整段 `text()`；
   只取 `span.pretty` 会丢掉首尾（`pretty` 只是被高亮的那一段）。
2. **封面点击直达 `/comic/{id}/1`** —— 站点的语义是"点封面即开始阅读"，不是进详情。

> 方法备注（两条教训）：
> 1. 字段名不能只靠"跨作品不变"的交集法。实测交集只捞到 5 项，且多为语言值与图标名。
>    必须结合导航条上的分类标签反查，才能把 `h5` 分组对上。
> 2. **判定"某字段不存在"必须足够大的样本。** 本次用 2 部作品就断言三个字段不存在，是误判。
>    正确姿势：抽样 ≥20 部（跨列表页多页），并对每个分类轴**直接按路由正则命中计数**，
>    而不是先去猜标签名。按路由统计是免疫标签措辞的。

### D. 阅读页模型

- `/comic/{id}/{n}`，n = 1..N，**扁平、无卷、无章**。抽样 3 部的页数上限：355 / 2622 / 381，
  均无任何非数字子路径 → 不存在第二级层级。
- **权威取图信息**（直接内嵌在阅读页）：

```html
<img id="<token>"
     src="https://i{N}.nhentai.net/galleries/{gid}/1.{ext}"
     data-prefix="https://i{N}.nhentai.net/galleries/{gid}/"
     data-extension="webp|jpg">
```

- 站点自带预取：页面内联 `var image1 = new Image(); image1.src = '{prefix}2.{ext}'`，当前页往后 3 张。
- 导航控件：`comic-show-content-nav-item-wrapper` 带 `data-page`：
  `fast-rewind` → 第 1 页 · `arrow-left` → 上一页 · `arrow-right` → 下一页 · `fast-forward` → 第 N 页。
- 另有 `comic-nav-item comic-random-nav-item` → **随机看一部**。
- 图标集：`zoom_in` / `zoom_out` / `favorite` / `search` / `menu` / `reply`。
  **没有** `fullscreen`、没有双页/翻页模式类、没有连续滚动的容器类。

### E. 图片侧

- 主机 `i{N}.nhentai.net`。样本 12 部全部为 `i2`，但**不得硬编码** —— 必须读 `data-prefix`。
- **扩展名不固定**：样本 webp 9 / jpg 2 → **必须读 `data-extension`**。
  （实测教训：按 `.jpg` 硬拼会让 9/12 假 404，看起来像"图片不可用"。）
- **无防盗链**：无 Referer / 带 hanimeone Referer / 带 nhentai Referer 三者均 200，字节数完全一致。
- 越界返回 404（第 N+1 页），边界干净。
- **覆盖率**：抽样 12 部，**11 部能解析出取图前缀，且 11/11 首页图片真能取到**；
  1 部无 `data-prefix` → 少数作品不可用，需要降级。
- **抖动真实存在**：曾出现 5.5KB 轻量页（无图，重试即恢复）、1 次超时 → 取页与取图都必须重试。

### F. 三个性能雷

1. 分类索引页 446KB–4.3MB **单页无分页** → 不能"进分类就整页拉"。
2. 单作品页数可达 **2600+** → 阅读器必须虚拟化，不能预载全篇。
3. 每页条数不统一（列表 36 / 搜索 30 / 筛选 30）→ **按 DOM 实际卡数解析，不要硬编码条数**。

---

## 二、由结构推出的设计约束

| 结构事实 | 推出的设计约束 |
|---|---|
| 站点没有漫画首页 | 漫画 tab 首页要**我们自己造**，且只能用站点给的数据 → 不做推荐流 |
| 列表无排序控件 | 「发现」只有两条路：分类入口 + 搜索。首页重心应放分类入口 |
| 卡片只有 id/封面/标题 | 卡片不自带页数、标签；要展示需额外请求 → 默认不富化 |
| 标题三段式 | 解析取整段 `text()`，禁取 `span.pretty` |
| 无卷无章 | **不建 Series/Chapter 模型**，进度就是 `(comicId, page)` |
| `data-prefix` + `data-extension` | 图片 URL 由页面给出，禁止拼公式 |
| 图片无防盗链 | 离线整篇下载可行，且不经过 CF |
| 索引页 MB 级、无分页 | 索引页按需拉 + 落缓存，或只做入口按钮 |
| 页数可达 2600+ | 阅读器虚拟化 + 预取窗口 |
| 抖动存在 | 统一重试与退避 |
| 11/12 覆盖率 | 必须有"该作品暂不可读"的降级态 |

---

## 三、导航与信息架构

### 一级

`MainTab` 增加第 4 项 `Comic`（route = `ComicHome`）。
`MainTab` 的 `route` 是 `TopLevelBackStack` 的顶层键，追加一项是安全改动，`Fallback` 仍为 Home。
桌面 Rail / 手机底栏自动获得第 4 项。

### 二级（漫画 tab 内部）

站点只给了"列表 / 搜索 / 分类 / 详情 / 阅读"，**没有首页**，因此：

```
漫画 Tab
├── 漫画首页（自造）
│   ├── 继续阅读        ← 本地进度表，倒序
│   ├── 最新            ← /comics?page=1，36 卡，无限滚动续页
│   └── 分类入口        ← 5 个按钮（tags / parodies / groups / characters / artists）
│                         点击进入后再拉取，绝不预拉索引页
├── 分类浏览            ← /tags/X 等，30 卡 / 页 + 分页
├── 搜索                ← /comics/search?query=&page=
└── 阅读器              ← /comic/{id}/{n}
```

- 首页**不做推荐流**：站点不提供任何排序/热度数据，硬做就是编。
- 「最新」用无限滚动续 `?page=N`，因为站点分页是干净的数字页码。
- 「随机」放在首页与阅读器（对齐站点的 `comic-random-nav-item`），实现成本极低。

---

## 四、数据模型

```kotlin
// 卡片三要素，来自任何列表入口
data class ComicSummary(
    val comicId: String,
    val title: String,
    val coverUrl: String,   // 取 data-srcset，不是 src
    val galleryId: String,  // 从封面 URL 解析，供预取探测用
)

// 详情：元数据按「有序分组列表」建模（字段集不固定，见下）
data class ComicDetail(
    val comicId: String,
    val title: String,                 // h3 整段（三段 span 拼接后）
    val subTitle: String?,             // h4 整段
    val metaGroups: List<MetaGroup>,   // 按页面出现顺序，忠实保留
    val sourceDownloadUrl: String?,    // nhentai.net/g/{id}/download
) {
    // 固定字段的便捷访问器（类型安全，UI 用；缺失即为空）
    val tags get() = group("標籤")
    val artists get() = group("作者")
    val languages get() = group("語言")
    val categories get() = group("分類")
    val characters get() = group("角色")   // 条件字段
    val parodies get() = group("同人")     // 条件字段
    val groups get() = group("社團")       // 条件字段
    val pageCount get() = group("頁數")?.entries?.firstOrNull()?.text?.toIntOrNull()
    val uploadedText get() = group("上傳")?.entries?.firstOrNull()?.text
    private fun group(label: String) = metaGroups.firstOrNull { it.label?.startsWith(label) == true }
}

data class MetaGroup(val label: String?, val entries: List<MetaEntry>)
data class MetaEntry(val text: String, val url: String?)

// 阅读：无卷无章，进度就是 (comicId, page)
// 独立表，不复用 WatchHistoryEntity（那边 progress 是毫秒语义）
data class ComicProgress(
    val comicId: String,
    val page: Int,
    val totalPages: Int,
    val updatedAt: Long,
)
```

**关于元数据模型（两次修正后的结论）**

- 第一版：建议「分组列表」对冲未知分组。
- 第二版：只枚举了 2 部作品，以为字段集固定为 7 项，**改成具名字段——这是过早收敛，已撤回**。
- 最终：**回到「有序分组列表」**，理由是实测依据而非猜测——
  抽样 20 部，`角色：` / `同人：` / `社團：` 三个字段**有条件出现**，共命中 7 组。
  详情页字段集是「7 个固定 + 3 个条件」，**不固定**，所以不能写死字段集。
  同时用 `group(label)` 提供固定字段的便捷访问器，兼顾类型安全与 UI 摆放。

**为什么进度独立建表**：`WatchHistoryEntity.progress` 是毫秒，漫画是页码，语义不同。
混表会让"继续观看"和"继续阅读"两边都难做。若将来要合并展示，用 `ContentKind` 在查询层聚合，而不是合表。

**上传时间不能排序**：详情页只给相对时间文本、全页无 `<time datetime>`。
所以**不要**做"按上传时间排序/最近更新筛选"——没有数据支撑。
「最新」只能靠列表页的默认顺序（站点自己的顺序）。

---

## 五、解析层落位

- 新增 `site/hanime1/ComicParser.kt`（若先落品类轴，则 `site/hanime1/comic/`）。
- **网络层零改动**：`SiteId.Hanime1` 不变，`hanimeone.me` 已在 `SiteCatalog.hostnames` 内。
- 可直接复用：ksoup、`Parser.kt:1139 parseMaxPage()`（漫画分页同样只用 `?page=`）、
  CF 处理、图片缓存与 Coil3、`HanimeUrlRegex` 的站点判定。
- 新增能力：`data-srcset` 懒加载封面、`data-prefix`/`data-extension` 图片规格、`data-page` 导航。

---

## 六、阅读器设计

### 站点阅读器 vs 我们

| 能力 | 站点 | 我们 |
|---|---|---|
| 滚动 | 单页，一页一个 URL | **连续纵向滚动**（默认）+ 单页翻页可切 |
| 全屏 | 无 | 有 |
| 双页 / 跨页 | 无 | 可做 |
| 进度记忆 | 无 | `(comicId, page)` 三端同步 |
| 预取 | 固定后 3 张 | 窗口可配 |
| 虚拟化 | 不适用（单页） | **必须**（页数可达 2600+）|
| 离线 | 无 | 整篇落盘（图片无防盗链） |
| 缩放 | zoom_in / zoom_out | 手势 + 双击 |
| 随机 | 有 | 有（对齐） |
| 失败重试 | 未知 | 单页重试 + 占位 |

### 取图策略（关键）

1. 进阅读器先拉一次 `/comic/{id}/1`，解析 `data-prefix` + `data-extension`。
2. 之后所有页 URL = `{prefix}{n}.{ext}`，不再逐页拉 HTML。
3. 兜底：若 `data-prefix` 缺失（约 1/12），尝试用封面 `galleryId` + `jpg`/`webp` 双试；
   仍失败则显示"该作品暂不可读"，不要白屏。
4. 进入阅读器前用 `HEAD` 探测首页可读性，避免用户进去才发现打不开。

---

## 七、分期

| 阶段 | 内容 | 说明 |
|---|---|---|
| S1 | 解析 + 数据模型 + 列表 / 详情 / 最小阅读器（纵向滚动 + 进度） | 打通第 4 个 tab 的完整动线 |
| S2 | 搜索 + 分类浏览 + 随机 | 补齐"发现"的两条路 |
| S3 | 离线整篇下载 + 三端进度同步 + 阅读器设置（单页/双页/方向/亮度） | 超越站点的部分 |

---

## 八、未决与风险

**已解决**

1. ~~详情页那两个无链接 `h5` 的身份~~ → 已确认为 `#作品号` 徽章 与 `頁數：`。
2. ~~详情页字段标签名未完整识别~~ → 固定字段 7 个：
   `#id` / `標籤：` / `作者：` / `語言：` / `分類：` / `頁數：` / `上傳：`。
3. ~~`角色` / `同人` / `社團` 不在详情页~~ → **这是误判，已更正**。
   三者是**条件字段**，标签实测为 `角色：` / `同人：` / `社團：`，
   抽样 20 部共命中 7 组。详情页字段集 = 7 固定 + 3 条件，**不固定**。
   → 元数据模型因此回到「有序分组列表」（见第四节）。

**已犯过的方法错误（留档，避免重犯）**

| 错误 | 表现 | 正确姿势 |
|---|---|---|
| 样本太小就断言"字段不存在" | 只枚举 2 部作品 → 断言 3 个字段不存在（实际存在） | 判定"不存在"要抽样 ≥20 部，跨列表页多页 |
| 样本太小就断言"字段集固定" | 据 2 部改成具名字段 → 过早收敛，已撤回 | 字段集不固定时，用分组列表 + 便捷访问器 |
| 硬编码推导值 | 图片按 `.jpg` 硬拼 → 9/12 假 404 | 一律读页面给的 `data-extension` |
| 正则分支写错 | `A|B` 使两条分支互相污染 → 统计值失真 | 每次统计后用对照项自检（如同时统计已知存在的 `tags`） |

**未决（需要确认）**

1. 少数作品（约 1/12）无 `data-prefix` 的真实原因未知 → 只能先降级。
2. 是否存在按语言/分类的跨品类混筛（实测 `/languages/X` 返回纯漫画，但未穷举全部取值）。
3. 「上傳」相对时间的粒度（今天 / 本周 / 更早 这类粗分类能否做，需要看站点文案的取值集合）。
4. 条件字段的出现概率（本次 20 部命中 7 组，但未统计"有多少部带至少一个"），
   影响详情页布局的稳定性——若比例高，UI 需要预留位置而不是条件插入时才布局。

**风险**

1. **外部依赖**：图片托管在 nhentai CDN，非本站。改分片规则、加签名或加防盗链都会断掉。
   同站资源已出现 `?secure=` 签名（`vdownload.hembed.com` 的 logo），说明他们有此能力。
   → 缓解：一切从页面解析，公式只作兜底。
2. **抖动**：实测出现过轻量页与超时 → 统一重试与退避，并限制并发。
3. **性能**：索引页 MB 级、单作品页数 2600+、每页条数不统一 → 三处都按上面的约束处理。
4. **规模判断**：这是独立工作块（首页 + 列表 + 搜索 + 分类 + 详情 + 阅读器 + 进度 + 离线），
   与"播放器重写"同级，应单独排期，不作为顺手活。
