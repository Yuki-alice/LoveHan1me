# 弹幕功能可行性与 animeko 对标调研

> 调研日期：2026-09-18
> 方法：通读 `E:\hanime1\references\animeko-main\animeko-main` 的 danmaku 四模块源码
> + 在本项目 `site/` 层与全仓做弹幕相关取证。
> 结论先行：**不建议做**。

---

## 0. 结论

**没有可用数据源，这是硬约束。** 渲染引擎写得再好，也只是一台没有燃料的发动机。

叠加一条：animeko 的 danmaku 实现是 **AGPLv3**（每个文件头都有声明），抄过来会把本项目从 GPLv3 锁进 AGPLv3，与"保持 GPLv3、未来可能宽松化"的目标直接冲突。

---

## 1. animeko 弹幕系统（源码实证）

### 1.1 分层与依赖方向

| 模块 | 职责 | 依赖 |
|---|---|---|
| `danmaku/ui-config` | 纯配置数据类 | 只依赖 kotlinx-serialization |
| `danmaku/api` | 数据模型 + 拉取/调度抽象 | coroutines / ktorClient / **datasourceApi** |
| `danmaku/dandanplay` | 弹弹play 数据源实现 | `api(danmaku-api)` |
| `danmaku/ui` | Compose 渲染引擎 | `api(danmaku-api)` + `api(danmaku-ui-config)` |

方向严格单向：`ui-config ← api ← {dandanplay, ui}`，**`ui` 不依赖 `dandanplay`**。
⇒ 渲染与数据源彻底解耦。这是整个设计里最值得抄的一点。

### 1.2 数据从哪来

`danmaku/dandanplay/.../DandanplayClient.kt`（224 行），硬编码 `https://api.dandanplay.net/api/v2/`：

- `searchSubject()` → `GET /search/anime?keyword=`
- `searchEpisode()` → `GET /search/episodes?anime=&episode=`
- `matchVideo()` → `POST /match`
- `getDanmakuList()` → `GET /comment/{episodeId}?chConvert=0&withRelated=true`
- 鉴权：`X-AppId / X-Timestamp / X-Signature = Base64(SHA256(appId+ts+path+appSecret))` —— **需申请**

匹配策略（`DandanplayDanmakuProvider.kt:107-182`）：Bangumi.tv subjectId 映射 → 季度表别名精确匹配
→ 名字搜索 → 集内按 `episodeSort → episodeEp → episodeName → Levenshtein 模糊` → `matchVideo` 兜底。
**匹配不精确时有人工选集**：`provider/MatchingDanmakuProvider.kt` +
`ui-episode/.../danmaku/MatchingDanmakuDialogs.kt`。

### 1.3 数据模型

- `DanmakuInfo(id, serviceId, senderId, content)`
- `DanmakuContent(playTimeMillis: Long, color: Int(RGB), text, location: {TOP|BOTTOM|NORMAL})`
  —— **没有字号/字重字段**，统一样式由 Config 决定
- `DanmakuSession { events: Flow<DanmakuEvent>; requestRepopulate() }`
- `DanmakuEvent.Add / DanmakuEvent.Repopulate`

### 1.4 渲染（Compose Canvas 自绘）

- 画布：`danmaku/ui/.../DanmakuHost.kt:125-154`，`Canvas` + `forEachFloatingDanmaku/forEachFixedDanmaku`
- 文字离屏位图缓存：`StyledDanmaku.kt:77-82`（`ImageBitmap`），平台实现 `StyledDanmaku.skiko.kt` / `.android.kt`
- **轨道分配**：`FloatingDanmakuTrack.kt:206-235` 的 `isNonOverlapping()`
  （二分查找插入位 + `willClash()` 追尾检测 + `safeSeparation`）
- **时间轴调度**：`DanmakuCollection.kt:288-390`，20fps 循环，
  `repopulateThreshold = 3s × 倍速`、`repopulateDistance = 20s`，`binarySearchBy(playTimeMillis)` 定位
- **位置是纯函数**：`distanceX = (frameTime - placeFrameTime)/1e9 × speed`
  —— 不逐帧积分、无累计误差（这是很漂亮的一点）
- seek/暂停/倍速：`DanmakuHostState.setPaused()` / `interpolateFrameLoop()`（`withFrameNanos` + `FrameTimeSmoother`）。
  ⚠️ 倍速**只影响 repopulate 阈值**，弹幕滚动速度不跟倍速

### 1.5 配置与发送

- `DanmakuConfig`：fontSize 18.sp、alpha .8、strokeWidth 4f、speed 88 dp/s、safeSeparation 36.dp、
  displayArea 0.25、enableColor/Top/Floating/Bottom
- 屏蔽正则**不在** danmaku 模块，在 `app-data/.../DanmakuRegexFilter.kt` + `DanmakuFilterConfig.kt`
- **发送弹幕必须登录第三方账号**（animeko 自建后端 `AniDanmakuSender.kt:39-89`）；
  `danmaku/dandanplay` 模块里**没有任何发送代码**，弹弹play 只读

### 1.6 规模

| 模块 | 源文件 / 行数 | 测试 |
|---|---|---|
| api | 9 / 784 | 3 / 793 |
| dandanplay | 5 / 946 | 1 / 201 |
| ui | 12(+2 actual) / 2651 | 4 / 1010 |
| ui-config | 1 / 150 | 0 |
| **合计** | **29 / 4531** | **8 / 2004** |

（`DanmakuHost.kt` 736 行中约 570 行是 Preview；`DanmakuHostState.kt` 913 行是真正的硬骨头。）

---

## 2. hanime1.me 的可行性：零数据源

### 2.1 站点解析层取证

`shared/src/commonMain/kotlin/lovehan1me/site/` 共 11 个文件，搜
`danmaku|弹幕|bullet|barrage|chat|comment|字幕|subtitle`：

**唯一命中** `hanime1/Parser.kt:243` 的 `div.subtitle a, div.subtitle` —— 实为卡片元信息
`artist • uploadTime`（见 `:247-252`），与弹幕无关。

⇒ **零命中。**

### 2.2 第三方弹幕服务痕迹

全仓搜 `cc.163|bilibili|dandanplay|弹弹|danmaku|弹幕|tucao|acfun`：
- `bilibiliStyle` 参数（`VideoPlayerUi.kt:233` 等）—— 纯 UI 皮肤分支，非 B 站服务
- 站点域名仅 `hanime1.me / hanimeone.me / hanime1.com / javchu.com / getchu.com`

⇒ **无任何第三方弹幕服务调用痕迹。**

### 2.3 五个可能来源逐个评估

| 来源 | 可行性 |
|---|---|
| ① 站点自带 | **无**。评论接口 `HanimeCommentService.kt:25-45`，模型 `VideoComments` 只有 avatar/username/date/content/thumbUp，**无 `playTimeMillis`**，做不了时间轴弹幕 |
| ② 弹弹play | **≈0 命中率**。其库是 Bangumi.tv 体系的动画番剧；本项目 `site/` 层不具备 bgmtvSubjectId / 季度表 / 番剧别名任何一项；还需申请 appId/appSecret |
| ③ B站 / CC 弹幕 | 内容不存在于 B 站；cc.163.com 已停用 |
| ④ 自建后端 | 需账号+存储+审核+CDN；成人内容的云厂商/应用商店合规风险极高，与"GPLv3 独立客户端"定位冲突 |
| ⑤ 本地 XML/ASS 导入 | 技术可行，但用户没有现成弹幕文件来源，等于把成本转嫁给用户 |

---

## 3. 如果硬要做：代价与许可风险

### 3.1 最小可用范围

- **必需**：`ui-config`（150 行，可重写）+ `ui` 渲染核心 ≈ 2100 行 + `api` 模型/调度 ≈ 500 行
- **可砍**：`dandanplay` 全部（946）、`api/provider/`（252）、`DanmakuHost.kt` 的 ~570 行 Preview、全部测试
- **估算**：纯引擎 ~2600 行 / 8–10 人日；含播放器接线、设置页、屏蔽规则持久化、本地导入的 MVP
  约 5000–6000 行 / 3–4 周 —— **且仍解决不了"数据从哪来"**

### 3.2 AGPL 传染（关键）

animeko `LICENSE.txt` = **AGPLv3**，danmaku 四模块**每个 .kt 文件头**都有 AGPLv3 声明。

**可以安全借鉴（思想/接口形状，不受版权保护）：**
- 四层分层与单向依赖；渲染与 provider 解耦
- `DanmakuEvent { Add, Repopulate }` 的语义划分
- `distanceX = f(placeFrameTime, speed)` 纯函数位置模型（不逐帧积分）
- `repopulateThreshold / repopulateDistance / safeSeparation` 这组参数的存在与调参思路
- `DanmakuTrack.canPlace/tryPlace/tick` 的接口契约

**抄了即引入 AGPLv3（逐个文件头都有声明）：**

| 文件 | 行数 | 风险 |
|---|---|---|
| `danmaku/ui/.../FloatingDanmakuTrack.kt` | 332 | 高 |
| `danmaku/ui/.../DanmakuHostState.kt` | 913 | 高 |
| `danmaku/api/.../DanmakuCollection.kt` | 391 | 高 |
| `danmaku/ui/.../StyledDanmaku.kt` + 2 actual | 212 | 高 |
| `FrameTimeSmoother.kt`、`FixedDanmakuTrack.kt`、`DanmakuTrack.kt` | 270 | 中 |
| `danmaku/ui-config/.../DanmakuConfig.kt` | 150 | 中 |
| `danmaku/dandanplay/*` | 946 | 高（且本项目用不上） |

**后果**：GPLv3 项目混入 AGPLv3 代码后，合并作品整体必须按 **AGPLv3** 分发
（AGPL 第 13 条"网络交互也要提供源码"不可卸），一次性锁死未来宽松化的可能。

**若将来真要做**：只抄概念、自己从零实现；或参考 Apache-2.0 许可的弹幕库
（如 DanmakuFlameMaster，Apache-2.0，但为 Android View 体系，需自行移植到 CMP Canvas）。

---

## 4. 建议

1. **维持现状**（弹幕占位 UI 已于阶段 A 删除）。
2. 若未来某天出现可用数据源，再按 §1 的架构重新立项——届时的成本估算看 §3.1。
3. 在那之前，把精力放在"有数据源就能立刻见效"的功能上（骨架屏、帧预览、桌面端体验）。
