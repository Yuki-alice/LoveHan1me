# 决策与边界

只有用户拍过板的东西进这份文件。每条格式：**决定** —— 失效条件 —— 重认日期。
重认日期指用户对"这条还是不是你的意思"的逐条确认；2026-09-25 是文档体系重建那次的重认。
新规则：agent 若发现某条与代码冲突，**当场来问用户**，不要自行推测它是否还生效。

## A. 范围外（结构性不做，不是"还没做"）

- **BT 下载 / 数据源 SPI 插件平台 / 60 模块拆分 / Bangumi 同步 / 放送时间表与评分体系** 不做 ——
  失效条件：用户明确改判 —— 重认 2026-09-25
- **UI 不做六件**：底栏整体抬到 60dp、播放页布局重构、每源独立时间偏移、正则屏蔽词、
  OP/ED 跳过、锁屏手势 —— 失效条件：用户点名要做其中某件（`OP/ED 跳过` 特别注意：
  animeko 有整套实现，不要把它当我们的缺口补上）—— 重认 2026-09-25
- **字幕轨 / 外挂字幕 / 音轨选择 / 音频偏移 UI 永久不做**（源为单音轨 + 内嵌硬字幕）——
  失效条件：源结构变化出现多音轨/软字幕 —— 重认 2026-09-25
  现核：`player/` 与 `shared/` 内 `TrackSelection|subtitleTracks|audioTracks` 零实现
- **桌面不内嵌浏览器（JCEF/KCEF）**，永久不考虑 —— 失效条件：无 —— 重认 2026-09-25

## B. 播放器与画面

- **向 animeko 靠拢的正确落点是播放底座 mediamp，不是抄它的 UI** ——
  失效条件：mediamp 停止维护 —— 重认 2026-09-25
- **改判补充（用户 2026-09-26 拍板）：分层与状态契约可以搬，页面结构与外观不搬** ——
  可搬：api/ui 分离、领域层不依赖 UI 层、请求值与真值同快照分列、
  能力声明取代接口默认实现、控件可见性的请求者令牌仲裁。
  不可搬：播放页布局与视觉外观（仍受上面"不得回退四处"约束）——
  失效条件：用户点名改 —— 重认 2026-09-26
- **mediamp 内核与超分实现本次不重做，只重做组织形态** ——
  三端后端已落地（`player/build.gradle.kts:34` 起），超分双端已实现
  （桌面 `player/src/desktopMain/kotlin/lovehan1me/feature/player/DesktopMpvPlaybackEngine.kt:415`）——
  失效条件：用户改判 —— 重认 2026-09-26
- **视频模块三分 `:video:contract` / `:video:engine` / `:video:ui`，硬边界是 contract 与 ui 都不得碰 mediamp 类型** ——
  依据：`:player` 对 mediamp 是 `implementation`（`player/build.gradle.kts:34`）而非 `api`，
  抬成 `api` 会漏进 iOS 导出框架；现核 `shared/src/` 搜 `org.openani.mediamp` 零命中 ——
  失效条件：用户改判，或 iOS 不再需要 framework export —— 重认 2026-09-26
- **桌面继续用 mpv 是长期决定**（需要 Anime4K 真链 + 画面调节），已知代价写在
  `docs/evidence/` 之前不写、也不许被当作 bug 重提换底 —— 失效条件：用户改判 —— 重认 2026-09-25
- **截图 / 录 GIF：代码保留，入口先藏；预览帧滑条不做** ——
  现核 `player/src/commonMain/kotlin/lovehan1me/feature/player/PlaybackEngine.kt:169` 默认 false，
  仅 `player/src/desktopMain/kotlin/lovehan1me/feature/player/DesktopMpvPlaybackEngine.kt:377` override true
  —— 失效条件：接上真实用途时 —— 重认 2026-09-25
- **超分致错时吞异常降级为 OFF，绝不断播** —— 失效条件：用户改判 —— 重认 2026-09-25
- **分辨率门控：渲染面尺寸未知时不门控**（宁花算力，不静默关掉功能）——
  现核 `player/src/androidMain/kotlin/lovehan1me/feature/player/ExoSuperResolution.kt:60` ——
  失效条件：用户改判为"未知就保守关掉" —— 重认 2026-09-25
- **四处不得因"对齐 animeko"而回退**：双皮肤结构、右栏配色跟随主题、
  时间文字描边 + `tnum`、亮度能力探测 —— 失效条件：用户点名改 —— 重认 2026-09-25

## C. 弹幕

- **主路径 = 人工选集 + 持久化记住关联**；自动匹配只在唯一高置信时启用，有歧义一律不猜 ——
  失效条件：命中率前提被重新实测推翻 —— 重认 2026-09-25
- **v1 不做六件**：发送弹幕（需 JWT）、文件哈希匹配、本地自发弹幕池、正则屏蔽词、
  多源叠加、弹幕统计面板 —— 失效条件：用户逐条例外放行 —— 重认 2026-09-25
- **凭据构建期注入、打开即用**：真实值只在全机不入库的 `local.properties`，
  由 `shared/build.gradle.kts:269` 的 `generateDanmakuCredentials` 生成到 `build/generated/`；
  设置页那两个输入框是**覆盖**入口，不是必填项 —— 失效条件：用户改判 —— 重认 2026-09-19
- **弹幕引擎不重写**：闭式几何解算是资产，只从 animeko 取两处外科手术
  （帧平滑 + 文本一次栅格化）—— 失效条件：实测证明确实解不动 —— 重认 2026-09-25
- **不要动四件**（改动前必须先取证，不许当缺陷顺手修）：
  `DanmakuPositionTracker` + `seekGeneration`、`PlaybackStallDetector`、
  `PlayerTrace` 会话归因、`DanmakuRepository` 的 TTL / 失败冷却 / 宁取陈旧缓存 / `TOMBSTONE_CID`
  —— 失效条件：用户点名 —— 重认 2026-09-25

## D. 出口与站点

- **不自建托管反向代理**（流量经第三方服务器）。
  例外且必须写清：**本地 ECH 网关 `echgate/` 不属于这条禁区**——流量仍从用户本机出、
  没有中转，它解决的是 SNI 层问题，与反代不是一件事 —— 失效条件：用户改判 —— 重认 2026-09-25
- **不得对用户承诺 SOCKS 也能播**：播放器那一段只吃 HTTP 代理，SOCKS 档必须明确告知
  "这一段是直连" —— 失效条件：上游支持逐请求代理 —— 重认 2026-09-25
- **站点范围五条**：手动 cookie 流程永久保留为降级路径 / 不扩第三站 / 不做站点插件化 /
  iOS 不追付费分发 / comic 子站延后单列不阻塞主线 —— 失效条件：用户逐条改判 —— 重认 2026-09-25

## E. 代码来源

- **禁止文件级复制**：允许照着参考实现的思路重写同名逻辑，但包名、变量名、结构必须自己重做。
  `NOTICE` 里"未包含 animeko 源码"那句必须与实际一致——若发生函数级移植，就把它改写为
  "参考实现"，不许留着过期声明 —— 失效条件：用户改判 —— 重认 2026-09-25
- **clean-room 闸门**：`tools/license_similarity_scan.py` 存在就是给你跑的，
  涉及参考项目改动后跑一遍。不预设相似度阈值（旧文档那个数字无法核实出处，已作废）——
  失效条件：脚本被删除或不再覆盖源码目录 —— 重认 2026-09-25

## F. 会被反复重提的实现纪律

- **改任何字段的默认值语义，必须同时回答"老存档里已落盘的旧值怎么办"**：
  读侧针对性回落，或显式迁移。首启会把全部键（含空串、含 false）逐个落盘，
  此后默认值对老装机无效 —— 现核 `shared/src/commonMain/kotlin/lovehan1me/data/datastore/DataStoreManager.kt:75`，
  读侧回落样例 `shared/src/commonMain/kotlin/lovehan1me/data/datastore/DataStoreManager.kt:297`
  —— 失效条件：存档改为按需写键（不再首启全量落盘）之后 —— 重认 2026-09-25
- **超分档位落盘：2026-09-26 用户正式立项**（此前是"已知未做，别在别的任务里顺手修"）——
  现核 `shared/src/commonMain/kotlin/lovehan1me/feature/video/VideoRouteHostScreen.kt:233`
  仍是 `remember { mutableStateOf(0) }`；实现时必须同时回答"老存档里已落盘的旧值怎么办"（见上面那条）——
  失效条件：落盘实现并验收后本条删除 —— 重认 2026-09-26
