# 下一阶段规划：M7 ~ M10（2026-09 起）

> 基于代码实况（HEAD=1656da5, M6-2c）制定。原则：三端齐头并进、按模块补齐、对齐原站功能优先。
> 已确认约束：每周 3-5 小时业余投入；桌面 + Android 双主力；先自用打磨后发布；
> 验收以 smoke 自动化为主、人工只验体验；攒完整模块做大提交（每里程碑一个 M-x 提交 + tag）。

## 背景基线（实证，非文档）

- 桌面可信：下载引擎 SMOKE PASS（M6-2b/2c），登录/CF 仅桌面真实验证过。
- Android/iOS：编译通过，真机状态**未验证**（M5 系列只做了适配下沉）。
- `:app` 残留 50 个 kt 文件（约 6.4k 行），search/video 已空。
- 最大风险点：登录/账号体系（CF 验证 + Cookie 持久化在三端的可靠性）。

---

## M7：三端真实验证 + 账号体系（最高优先，~4-6 周）

> 目标：把「编译通过」变成「真机能用」，优先消除账号体系风险。

### M7-1 Android 真机基线（~1 周）
- Android 真机跑通主链路：冷启动 → 首页 → 搜索 → 视频播放 → 暂停/续播。
- 产出：Android 基线缺陷清单（可修则修，不可修立 issue 式记录）。
- 验收：真机主链路无 crash，播放可用。

### M7-2 账号体系三端验证（~1-2 周，风险核心）
- Android 真机：登录 → CF 验证（弹 WebView）→ Cookie 持久化 → 杀进程重登态保持 → 收藏/历史读写。
- 桌面：同链路回归（已验证过，防回归）。
- iOS：模拟器 + 真机跑同一链路（WKWebView CF 直嵌路径）。
- 修复项：Cookie 存储路径（DataStore 跨端一致性）、CF 验证超时/重试策略。
- 验收：三端登录态跨进程重启保持；smoke 脚本覆盖登录态读取。

### M7-3 iOS 基础链路验证（~1 周）
- iOS 模拟器：冷启动 → 首页 → 搜索 → 视频页（播放可先黑屏但页面结构正常）。
- 产出：iOS 缺陷清单，区分「渲染问题」与「播放引擎缺失」。

### M7-4 iOS 播放路径决策 spike（~1 周）
- 并行评估两个方案，输出决策记录：
  - 方案 A：AVPlayer（系统组件，工作量小，格式兼容性受限）
  - 方案 B：VLCKIT / 现成 KMP 可绑库（快速补齐，体积代价）
  - （mpv 统一内核列为远期选项，不进本阶段）
- 决策标准：hls.ts 源兼容性、Seek 精度、字幕/音轨需求、集成工作量。
- 验收：spike 结论文档 + 选定方案的 demo 页可播放一条视频。
- 决策（2026-09-10 实测）：选定方案 A（AVPlayer）。模拟器 app 内实播真实 hanime 视频 408163（vdownload progressive mp4 1080P）：2s→Ready/playing、进度 6s/1920x1080/dur846s、seek(60s)误差 1580ms、2.0x 生效、坏链进 Error；真源为 mp4 非 m3u8，hls.ts 兼容风险不成立。
- 方案 A 自动化用例（shared/src/iosTest IosAVPlaybackEngineTest，6 项）在本机 test.kexe 进程内因环境 TLS -1202 全 SKIP（同机宿主 curl 200、同模拟器 app 200，属环境劫持非引擎缺陷），断言留给健康网络。放弃 VLCKIT：+30MB 体积与 ObjC 桥接成本无对应收益（真源无 AVPlayer 不支持的格式）。

---

## M8：功能对齐补齐（~4-6 周）

> 目标：原站能做的三端都能做。

### M8-1 下载体系三端补齐（~2 周）
- Android：HanimeDownloadWorker 真机验证（前台通知/断点续传/SAF 路径）。
- 桌面：下载管理页 UI（队列/进度/暂停/删除/打开目录），引擎已 SMOKE PASS。
- iOS：沙盒下载 + 文件 App 导出（联动 M7-4 决策后的播放器）。
- 验收：smoke 覆盖「入队 → 进度落库 → 完成回调」三端一致。

### M8-2 残留 :app 模块下沉（~1-2 周）
- getchu preview 族下沉 commonMain。
- AndroidVideoPageHost 精简评估：能否瘦身为纯壳注入。
- 产出：:app 收敛至纯平台壳（目标 ≤30 文件）。

### M8-3 对齐清单审计（~1 周）
- 对照原站功能清单（浏览/搜索/标签/收藏/历史/评论/账号/下载），逐项标注三端状态。
- 产出：差异矩阵，缺口排入后续迭代。

---

## M9：弹幕超集（~3-4 周，自用打磨期启动）

> 目标：原站没有的差异化能力，B站式体验。

### M9-1 弹幕数据层（~1 周）
- 弹幕模型（时间点/类型/颜色/发送者）+ Room 表 + 本地存储。
- 预留云端同步接口（WebDAV 远期，本期本地）。

### M9-2 弹幕渲染层（~1-2 周）
- Compose Canvas 自绘轨道分配（滚动/顶部/底部三种）。
- 与三端播放器时钟对接（AVPlayer/mpv/Exo 进度回调统一抽象）。

### M9-3 弹幕发送（~1 周）
- 发送框 + 本地即刻上屏 + 屏蔽词/透明度/速度设置项。

---

## M10：打磨与发布准备（~2-3 周，远期）

- 三端体验统一走查（桌面 + Android 双主力深度自用）。
- crash/日志上报收口；性能基线（启动时间/播放内存）。
- 发布链路：桌面安装包（dmg/pkg）、Android Release 签名、iOS TestFlight（如届时对外）。

---

## 执行纪律

1. **每里程碑一个大提交**：`M7-1: <摘要>` 式 message + `m7-1-done` tag（沿用 M 系列惯例）。
2. **smoke 优先**：可自动化的链路必须落 smoke 脚本后才算验收。
3. **进度记录**：以 git log 为准，本文件仅记规划与决策；里程碑完成时在文末勾选。
4. **风险升级**：账号体系若在 Android/iOS 实测暴露结构性问题（如 CF 验证无法通过），暂停后续里程碑优先攻坚。

## 里程碑总览

| 里程碑 | 内容 | 预估 |
|---|---|---|
| M7-1 | Android 真机基线 | 1 周 |
| M7-2 | 账号体系三端验证 | 1-2 周 |
| M7-3 | iOS 基础链路验证 | 1 周 |
| M7-4 | iOS 播放路径决策 | 1 周 |
| M8-1 | 下载体系三端补齐 | 2 周 |
| M8-2 | 残留模块下沉 | 1-2 周 |
| M8-3 | 对齐清单审计 | 1 周 |
| M9-1~3 | 弹幕三层 | 3-4 周 |
| M10 | 打磨与发布准备 | 2-3 周 |

- [ ] M7-1（Android 真机基线，需真机）
- [x] M7-2（iOS 代码 + 桌面落盘 + cookie 冒烟完成；Android/iOS 真机验证待执行）
- [ ] M7-3（iOS 模拟器/真机，需设备）
- [x] M7-4
- [ ] M8-1
- [x] M8-2
- [x] M8-3

## M8-3 功能对齐差异矩阵（2026-09-10，代码事实审计）

> 图例：✅=代码完备且有真机/模拟器实测证据；🟡=代码完备但缺实测，或平台降级；❌=缺失/占位。
> 审计基线：35 路由（`SharedTopNavigation.kt` entry×35）三端同一套；网络/解析三端同栈（`NetworkRepo.kt` + `Parser.kt` + Ktor），差异只在平台壳。
> 实测证据缩写：A真机=Redmi 真机 dump/log（M8-1b）、D烟=desktopApp 冒烟（M6-2）、i sim=iOS 模拟器（M7/M7-4）。

| 功能项 | Android | 桌面 | iOS |
|---|---|---|---|
| 首页浏览（Banner/多分区） | ✅ A真机首页 dump 出卡片 | 🟡 共享 `HomePageViewModel`，缺 UI 实测 | 🟡 i sim 首页 200+解析正常（M7），缺完整导航实测 |
| 分类/标签分区（里番/2D/3D…） | ✅ 同上（同一屏） | 🟡 同上 | 🟡 同上 |
| 基础搜索 | 🟡 共享 `SearchScreen`+`Parser.hanimeSearch`，未真机搜过 | 🟡 同左 | 🟡 同左 |
| 高级搜索/标签组合 | 🟡 共享 `AdvancedSearchSheet`+`getHanimeSearchResult`，未验 | 🟡 同左 | 🟡 同左 |
| 视频页播放 | ✅ A真机在线+本地均出画（Exo，`ExoPlaybackEngine.kt`） | ✅ D烟 mpv 出画（M3，`DesktopMpvPlaybackEngine.kt`） | ✅ i sim 真播实测（M7-4，`IosAVPlaybackEngine.kt`） |
| 简介/相关推荐 | ✅ A真机简介页 dump（`VideoIntroductionScreen.kt`） | 🟡 共享页，缺实测 | 🟡 共享页，缺实测 |
| 评论列表/发表 | 🟡 列表 A真机 logcat 见过（`getComments`），发表未验 | 🟡 共享 `CommentScreen`+`postComment`，未验 | 🟡 同左 |
| 收藏（喜欢/稍后看/播放清单） | 🟡 `addToMyFavVideo`/`addToMyList`+共享 UI，未验 | 🟡 同左 | 🟡 同左 |
| 观看历史（本地+在线） | 🟡 共享屏+`getOnlineWatchHistories`，未验 | 🟡 同左 | 🟡 同左 |
| 订阅（艺术家） | 🟡 `getMySubscriptions`/`subscribeArtist`+共享屏，未验 | 🟡 同左 | 🟡 同左 |
| 登录 | 🟡 WebView 槽位（`:app AuthRouteScreens.kt`）+共享表单，未走通一次 | 🟡 共享表单+KCEF CF 窗，未验 | 🟡 共享表单+WKWebView CF（M7-2 代码），未验 |
| CF 人机验证 | ✅ A真机 WebView 多次触发+通过（`CloudflareRouteScreen`） | 🟡 KCEF 独立窗（M5-5，`Main.kt:84`），缺实测 | 🟡 WKWebView 直嵌（M5-5），代码级 |
| 头像上传/裁剪 | 🟡 picker+crop 槽位（`AvatarCropScreen.kt`），未验 | ❌ 槽位 null→占位（`SharedTopNavigation.kt:298`） | ❌ 同左 |
| 每日打卡 | 🟡 共享路由+Widget（`CheckInWidgetProvider.kt`）+系统日历（`SystemCalendar.android.kt`），未验 | 🟡 路由有，日历 `=false`（`SystemCalendar.desktop.kt`） | 🟡 路由有，日历 `=false`（`SystemCalendar.ios.kt`） |
| 下载队列/续传/分组 | 🟡 WorkManager 桥：发起/完成/本地播/删除已验，通知/杀恢复/暂停待验（M8-1b） | 🟡 引擎 D烟 PASS（M6-2），目录配置占位（`DownloadSettingsRoute:473` null 回退） | 🟡 引擎 i sim 烟 PASS（M8-1a），无导入/外部播 |
| 下载导入（SAF 扫描） | 🟡 `AndroidDownloadWorkController.importDownloaded` 真实现，未验 | ❌ `=false`（`DesktopDownloadWorkController.kt:165`） | ❌ `=false`（`IosDownloadWorkController.kt`） |
| 外部播放器 | 🟡 ACTION_VIEW chooser（`AndroidShell.kt:145`），未验 | ❌ 无槽位 | ❌ 无槽位 |
| 设置（主题/语言/播放器/网络） | 🟡 共享设置屏全套，未逐项点验 | 🟡 同左 | 🟡 设置 Hub iOS 降级占位（`PlatformSettingsRoutes.ios.kt:15`，P7 再定） |
| 备份/恢复 | 🟡 `BackupManager.kt`（jvmMain），未验 | 🟡 同左 | ❌ 无 iOS 语义（同上文件） |
| 投屏 Cast | 🟡 `CastPlaybackEngine.kt`+Play 服务门控，待实测（无 receiver 无法断言） | ❌ `isCastAvailable()=false`（`CastAvailability.desktop.kt`） | ❌ `isCastAvailable()=false`（`CastAvailability.ios.kt`） |
| 画中画 PiP | 🟡 真实现但 `shouldEnterPip()=false`（`AndroidVideoPageHost.kt:88`） | ❌ 明确不支持（`DesktopVideoPageHost.kt:12` 注释） | ❌ `NoopVideoPageHost` |
| 桌面小组件 | 🟡 打卡 Widget 代码，未验 | ❌ 平台无语义 | ❌ 平台无语义 |
| 全屏/横竖屏/亮度/常亮 | 🟡 `AndroidVideoPageHost` 真实现，未逐项验 | 🟡 仅 AWT 全屏（`DesktopVideoPageHost.kt:31`） | ❌ Noop |
| 预告/新番时间表 | 🟡 共享 11 个 Getchu 文件+`Parser.hanimePreview`，未验 | 🟡 同左 | 🟡 同左 |
| 弹幕/HKeyframes 基建 | 🟡 路由/DB 就绪（M9 前置） | 🟡 同左 | 🟡 路由有；内核切换对 AVPlayer 无效（`PlaybackEngineFactory.ios.kt` 忽略 kernel，选 MPV 仍播 AVPlayer，易误导） |

计数：Android ✅5 🟡20 ❌0；桌面 ✅1 🟡18 ❌6；iOS ✅1 🟡16 ❌8。

### 缺口清单（用户价值×实现成本排序）

1. （M8 后续，紧邻）Android 通知权限申请：全新安装无 `POST_NOTIFICATIONS`→前台/完成通知全灭（M8-1b 实证）；修法=下载确认处申请一次，约 10 行 `:app` 内。
2. （M8 后续）三端登录成功链路实测（M7-2 遗留）：代码三端完备，缺一次走通；若暴露 CF/会话结构性问题则升级阻塞。
3. （M8 后续）下载收尾：Android 杀恢复/暂停（M8-1b 余项）＋桌面目录配置占位＋iOS 无导入/外部播（后两者需原生能力，成本中）＋M8-1c（桌面走查+打开目录）待办。
4. （M8 后续）iOS 设置 Hub 降级占位→真页＋备份（`PlatformSettingsRoutes.ios.kt:15` P7 项）；附带修 iOS MPV 内核选项误导（隐藏或禁用，成本低）。
5. （M9 前置）🟡转✅走查：搜索/收藏/历史/评论发表/打卡/预告，按屏走查即可，多为零代码。
6. （M10/远期）头像上传桌面/iOS、投屏桌面/iOS、PiP 桌面/iOS：价值低、需原生能力；桌面小组件：平台无语义，不做。
7. 环境债（非代码）：Clash 类规则代理出口漂移会使 IP 绑定的 `cf_clearance` 循环失效（M8-1b 实证：节点 `後藤 一里`→`ムラサメ`切换即复现/自愈）；验证环境优先固定节点。

- [ ] M9-1
- [ ] M9-2
- [ ] M9-3
- [ ] M10