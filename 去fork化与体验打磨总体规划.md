# 去fork化与体验打磨总体规划

> 制定：2026-09-12 ｜ 修订：2026-09-13 v4 ｜ 状态：**已评审；M1–M3-a 已闭环；M3-b/c 代码完成（iOS 验证暂缓）；M4 部分开工**
> 定位：LoveHan1me 从"能跑的 fork"变成"独立项目"的总路线图。只含决策与验收，不含实现细节。

---

## 0.1 变更记录（走「变更流程」的条目，仅记与本轮决策冻结清单的偏离）

| 日期 | 原决定 | 现决定 | 理由 |
|---|---|---|---|
| 2026-09-13 | GIF 时长**固定 5s 不可调** | **恢复三选 2s/3s/5s + 各自估算体积**（已否决固定档） | 固定档丢掉了"按内容长短取舍体积"的能力；超预算档位本就由 `GifCapturePolicy` 提前过滤（用户点不到必然失败的按钮），固定档并不额外换来安全性 |
| 2026-09-13 | M3-b/c 以"三端各录一段可播放 GIF"为**闭环硬门槛** | **门禁放宽**：iOS 编译/真机验证列为后续项，不阻塞闭环 | 当前 macOS 设备不可用；iOS 代码已实现并标注了 5 处待验符号形态，风险可控（失败会返回 null 并被如实报"抓帧失败"，不会产出错帧） |
| 2026-09-13 | 文件头 28 处"`@project` 批量改 LoveHan1me"（验收 grep 0 命中） | **追加修正归属**：`@project` 全仓统一为 LoveHan1me（51 处），并把裸 `@author Yenaly Liew` 标为「上游原作者，见 NOTICE」 | 原批量替换只改了 `@project` 没改 `@author` → 形成"文件属于 LoveHan1me 却署名原作者"的自相矛盾；且 `Hanime1`/`Han1meViewer` 两旧名残留，三个项目名并存 |
| 2026-09-13 | M3-c 能力矩阵写"Android FileProvider 分享 / iOS `UIActivityViewController`" | **继承 M3-b 已落地的 `MediaExport` 取舍**：Android 走 **MediaStore**（省掉 FileProvider 覆盖目录与 `file_paths.xml`）、桌面"落盘 + 打开所在文件夹"、iOS 仍降级为"存 `Documents` + 提示位置"（`SavedOnly`，无分享面板） | 截图与 GIF 是**同一个"保存并分享"手势**（M3-b 已合并为一个平台动作），共用一处实现才不会两边漂移；iOS 的 `UIActivityViewController` 接入属独立议题（既有先例 `rememberShareText` 同样是降级实现），与"iOS 门禁放宽"同批处置 |

---

## 0. 决策冻结清单（拷打结论，不再 reopen，有变走变更流程）

| # | 议题 | 决定 |
|---|---|---|
| 1 | 应用显示名 | **LoveHan1me**（窗口标题/安装包/关于页/桌面-iOS 包名全改） |
| 2 | 包 ID | 统一 **`me.lovehan1me`**（namespace `lovehan1me` 保留；`io.github.*` 旧空壳删除） |
| 3 | LICENSE | 先保持 **GPLv3 + 诚实归属**，Parser 重写完成后评估换 MIT/Apache |
| 4 | 伪装图标 | **删除**（4×activity-alias + 设置项 + 字符串） |
| 5 | Getchu 预览 | **保留**（已移植完，不追加投入） |
| 6 | 更新通道 | 指向自己的 GitHub 仓库 `https://github.com/Yuki-alice/LoveHan1me` |
| 7 | 头像裁剪 | 统一自研共享裁剪页，**删 mucute 依赖**与 Android 注入分支 |
| 8 | 登录/CF 屏 | 统一 shared 实现（FormLoginScreen / CloudflareVerificationWindow），删 :app 旧屏 |
| 9 | `:app` 终态 | **纯壳**：只留 Activity/Shell/Worker/Application/系统能力 |
| 10 | 模块拆分 | **暂不拆**（:shared 353 文件单模块 + 包内整理；E3 余热未消） |
| 11 | 旧空壳 | **直接删**（`app/src/main/java` 下约20空目录 + `shared/.../io/github/.../dao` 空壳） |
| 12 | reference/ | **保留**（213MB，gitignored；对照期虽过，留作不时之需） |
| 13 | H 帧功能 | **全删含数据**（代码+表+设置项+路由），改为 GIF + 截图分享（见 M3） |
| 14 | GIF 形态 | **片段转 GIF，限制时长**（录制几秒转码，需技术 spike，见 §4） |
| 15 | 截图分享 | **截图 + 系统分享面板**（不做编辑器，不做相册管理） |
| 16 | infra H 前缀 | **Hanime前缀语义化，仅改4类**：HDns→HanimeDns、HProxySelector→HanimeProxySelector、HCacheManager→HanimeCacheManager、HFileManager→HanimeFileManager（HANIME/HAN1常量不动以控改动面）；HKeyframe 随功能删除自然消失 |
| 17 | Parser 重写 | **下阶段**（M6，换 MIT 的必经之路） |
| 18 | 体验方向 | 6 个全要：播放 / 下载离线 / 搜索发现 / 订阅追番 / 账号多端 / 启动性能 |
| 19 | 推进节奏 | **先出完整计划**（本文档），评审后再动 |

---

## 进度快照（2026-09-13 git fetch 实测）

| 里程碑 | 状态 | 证据 |
|---|---|---|
| M1 身份切换 | ✅ 已闭环 | 2a9d1cd，显示名/包名/更新通道/删4×alias/桌面落盘路径/文案全切；仅剩 LICENSE双行与28处文件头注释为本轮收尾（见 M1-R） |
| M2 功能取舍 | ✅ 已闭环 | fe23fa6，登录/CF统一shared、mucute依赖清零（grep 0命中），:app 27kt但未至纯壳（见 M4） |
| M3-a H帧删除 | ✅ 已闭环 | 6提交 95文件 -4112行，grep HKeyframe 源码0命中，assets/h_keyframes双源集已删，仅剩3处死代码与1处孤儿注释（见 M3-a-R） |
| M3-b/c GIF+截图 | ✅ 代码完成（iOS 验证暂缓） | M3-b：4 提交 + 52/8 测试。M3-c：三端 PNG 编码（`encodePngArgb`）+ `ScreenshotCapturer` 取帧管线 + 播放器入口 + 三语文案，**+20 测试（113/113）**；APK 级验收 `.workbuddy/m3c_verify_apk.py` 通过。iOS `1996235` 与 M3-c 的 `PngEncoding.ios.kt` 未在 macOS 编译验证（**门禁已放宽，不阻塞**） |
| M4 结构收尾 | 🔶 部分开工 | **已完成**：H 前缀 4 类更名（旧类名 grep 0 命中）、LICENSE 双行、文件头归属、死代码清零、旧文档归档 6 份、空壳目录清零。**待做**：`:app` 下沉（HanimeCacheManager/HanimeFileManager 仍在 :app）、根目录 md 收敛、SettingsRepository 契约抽离 |
| M5 体验6方向 | ⏳ 未开工 | 需子计划，顺序已定依赖序 |
| M6 Parser | ⏳ 下阶段 | 契约先立，1180行实现后重写 |

### 本轮拷打增补（2026-09-13，9轮）

| 议题 | 增补决定 |
|---|---|
| LICENSE | 追加双行保留（GPL模板+LoveHan1me/Yuki-alice） |
| 文件头 28 处 | 批量改为 @project LoveHan1me → **追加修正归属**（全仓 51 处统一 LoveHan1me，裸 @author 标注为「上游原作者，见 NOTICE」），详见 §0.1 |
| 空壳 | 现在删（M4收尾首项）→ **已清零** |
| infra命名 | Hanime前缀，仅4类，裸H不取 → **已完成**（旧类名 grep 0 命中） |
| 架构违规 | 立规矩逐步治：抽 SettingsRepository契约到 core/domain，feature互引17处后续守规矩 |
| 旧文档 | 归档到 docs/history，根目录只留4份真源 → **已归档 6 份，但根目录仍余 10 份 md，待收敛** |
| :app纯壳 | HCacheManager/HFileManager下沉shared后才真纯壳（**本轮只改名，仍在 :app**） |
| GIF验收 | 时长**三选 2s/3s/5s + 估算体积**（固定 5s 档**已否决**，见 §0.1）；截图一键保存并分享；死代码现在清；三端真机验证列为后续项 |
| Parser | 先立 SiteId/SiteConfig 契约，后重写实现 |
| 体验/后端/弹幕 | 6方向按依赖序、仍做解析代理、弹幕只接外部源 |
| 性能/发布 | 先埋点立基线再优化；不发版只推main |

---

## M1 身份切换（E1）：从 Han1meViewer 变成 LoveHan1me

**目标**：用户在任何地方看不到 Han1meViewer 和 daisukiKaffuChino（除 NOTICE/GPL 归属区）。

**本轮收尾（M1-R，低风险扫尾，不计入M1是否闭环）**：

| 项 | 内容 | 验收 |
|---|---|---|
| LICENSE双行 | 追加 LoveHan1me Copyright (C) 2026 Yuki-alice，与上游双行保留 | LICENSE含双持有人 |
| 文件头28处 | @project Han1meViewer → @project LoveHan1me 批量替换 | grep 0命中 |

| 项 | 内容 | 验收 |
|---|---|---|
| 显示名 | 窗口标题、安装包名、iOS PRODUCT_NAME、`hanime_app_name` → LoveHan1me | 三端装包看名字 |
| 包 ID | applicationId/namespace/iOS bundle 统一 `me.lovehan1me` 系；删两处旧包名空壳 | 空目录清零，`package` 与目录 0 错位（沿用 E3 脚本校验法） |
| 更新通道 | HA1_GITHUB_URL、AppUpdateCard、App.kt 自检、设置关于页 → `https://github.com/Yuki-alice/LoveHan1me` | 关于页点得开，更新检查跑得通 |
| 关于页 | 作者/仓库条目换自己；原作者移入"致谢/上游"区（GPL 诚实归属） | — |
| LICENSE | 保持 GPLv3；NOTICE 收敛表述（原作者 + Yenaly + MomoQR 保留） | — |
| 伪装图标 | 4×alias + 设置项 + 字符串 + HanimeApplication 引用全删 | manifest 无 alias，`LauncherAlias` 引用清零 |

---

## M2 功能取舍：删伪装，统一三组双轨

| 项 | 内容 | 验收 |
|---|---|---|
| 删伪装图标 | 见 M1 | 同上 |
| 裁剪统一自研 | 删 mucute 依赖 + Android 注入分支 + PlatformScreens.avatarCrop 槽位；三端走共享裁剪页 | 依赖树无 mucute；三端裁剪行为一致 |
| 登录/CF 统一 shared | 删 :app LoginScreen/CloudflareScreen；:app 只保留 WebView 容器能力（如需） | :app 无完整页面实现 |
| Getchu | 冻结：不删不改，回归不断即可 | 现有 pytest/冒烟不断 |

---

## M3 H 帧移除 + GIF/截图新功能（本轮最大块）

### M3-a 删除清单（全删含数据，无迁移——无用户）

- 数据层：HKeyframeEntity/Dao、Database 表定义 + 版本号+1、备份恢复里的 HKeyframe 键（如有）
- 状态层：HKeyframe 相关 ViewModel/State/缓存
- UI：签到页**除外**（dailycheckin 是签到日历，不是 H 帧，别误删）——删的是 H 关键帧标记/成就/报表/卡片/对话框/共享帧集 UI
- 设置项：关键 H 帧开关、共享帧集、采用共享帧、H 关键帧管理入口
- 路由：HKeyframesRoute / SharedHKeyframesRoute / HKeyframeSettingsRoute + entry + fallback
- 播放器：H 关键帧倒计时、选帧菜单、长按打帧相关（含 VideoRouteHostScreen 的 hKeyframes 状态与对话框）
- 搜索残留：`grep HKeyframe` 全仓清零为验收标准（含注释）

### M3-b 片段转 GIF（限制时长，新功能，需先 spike 再排期）

- 形态：播放中点"录 GIF"→ 录 N 秒（上限，如 5 秒 × ≤15fps）→ 转码 → 保存 → 系统分享
- 技术路线（spike 要回答的）：
  1. 取帧：Android（ExoPlayer 帧抓取）/ 桌面（mpv screenshot 连拍）/ iOS（AVPlayer 输出），三端能力对齐表
  2. 编码：拒绝 ffmpeg-kit（包体爆炸）；纯 Kotlin GIF89a 编码 + 中位切分调色板，桌面/JVM 与 Android 共用，iOS 评估 ImageIO/GIF 写入
  3. 上限策略：时长/帧率/分辨率三档封顶，超了直接拒绝并提示（防 OOM/卡死）
- 验收：三端各录一段可播放 GIF；失败路径（超限/无权限/编码失败）有提示无崩溃

### M3-c 截图 + 系统分享

- 能力矩阵：expect/actual（Android FileProvider 分享 / 桌面落盘+打开所在位置或系统分享 / iOS UIActivityViewController）
- 与 M3-b 复用同一套取帧，只做单帧
- 验收：三端截图→分享面板全链路可用

---

## M4 结构收尾（本轮待开工，含 M1-R/M3-a-R 扫尾）

| 项 | 内容 | 验收 |
|---|---|---|
| 空壳删除 | 约30层空目录（app/src/main/java全树） + jvmMain dao空壳 `rm -rf` | `find -type d -empty`（除构建产物）清零，git status无空目录 |
| LICENSE+文件头 | 见 M1-R | 同上 |
| 死代码清理 | toPrettyCountdownRemindString / DEFAULT_COUNTDOWN_SECONDS / 6处 will_remind_before字符串 / build.gradle.kts:95孤儿注释 | grep 0命中 |
| `:app` 瘦身 | HCacheManager/HFileManager（167+96行）下沉shared；DownloadSettingsRoute等可移植页下沉；终态只留Activity/Shell/Worker/Application/系统能力 | :app 无 feature/data可下沉逻辑，27→约20文件 |
| infra更名 | HDns→HanimeDns、HProxySelector→HanimeProxySelector、HCacheManager→HanimeCacheManager、HFileManager→HanimeFileManager（仅4类，HANIME常量不动） | 旧名前缀grep 0命中（除NOTICE归因） |
| 包内整理+架构立规 | 抽 SettingsRepository契约到 core/domain（解 core→data/ui→data/site→data反向），feature互引17处后续守规矩；data/network拦截器链注释更新 | 静态 import 校验待人工0 + 四目标编译；新约束写入 ARCHITECTURE.md |
| 旧文档归档 | KMP_MIGRATION_PLAN等6份→docs/history，根目录只留README/目标架构/去fork化规划/Windows搭建 | 根目录md 4份 |
| reference | 保留不动（已gitignored） | — |
| 模块拆分 | 本轮不做；阈值维持>100文件且零依赖才拆 | — |

---

## M5 体验打磨（6 方向，顺序为依赖序，一次一方向，不发版只推main）

| 序 | 方向 | 首批目标（细化开子计划前置） | 备注 |
|---|---|---|---|
| 1 | 账号多端 | 登录态统一、备份恢复可靠、登出/多设备一致 | 地基，其他方向依赖它 |
| 2 | 启动性能 | **先埋点立基线再优化**：冷启动耗时（Application→首帧）量化→压首屏；DataStore/Coil/引擎初始化错峰 | 先立基线（现在无数据），两步走 |
| 3 | 播放体验 | 手势/倍速记忆/跳过片头尾/错误换源/超分开关可达性 | 主战场 |
| 4 | 搜索发现 | 沿用已落地的筛选栏；补排序语义、空态、历史管理 | 站在已交付上继续 |
| 5 | 订阅追番 | 更新检测、提醒、新集标出 | 依赖账号 |
| 6 | 下载离线 | 队列可靠、断点续传、存储管理、本地与在线一致 | 涉及系统能力最多，放最后 |

补充冻结：

- **后端**：仍做 Kotlin+Ktor 解析代理服务（D4/D5保留），站点改版无需发版；M6契约即为其前置
- **弹幕**：不做通用弹幕引擎，仅接外部源（轻量）；废案 Dandanplay 仅作接口参考
- **模块拆分**：阈值维持>100文件且零依赖才拆，本轮不拆
- **发布**：每M完成只推main，不发Pre-release，攒到M6再议

每个方向开工前单独出子计划（目标+验收+不碰清单），不在本文档展开。

---

## M6 Parser 重写 + MIT 评估（下阶段，先立契约）

- Step 0（M4同步）：立 `site/SiteId.kt` `SiteConfig.kt` 不透明契约（学废案§3.1），`site/hanime1/SiteCatalog.kt`落地，不碰Parser实现；让site/commonMain禁java/okhttp约束有落点，Parser重写时即插即用
- Step 1：`site/hanime1/Parser.kt`（1194→目标拆分为 Parser/HtmlExtract/JsonExtract 三文件）+ `site/getchu` 按契约重写为纯函数解析器 + 单测（覆盖首页/搜索/详情三场景）
- 完成后做法务复核：重写比例是否足以换 MIT/Apache；未达标则继续保持 GPLv3
- 在此之前：**对外口径一律 GPLv3**，关于页/仓库 LICENSE 不动

---

## 风险与阻塞（2026-09-13 刷新）

| 风险 | 等级 | 对策 |
|---|---|---|
| 新仓库 URL | ✅ 已解决（`Yuki-alice/LoveHan1me`），远端 HEAD已切回main，__probe已删 | — |
| M1-R/M3-a-R扫尾 | 低 | 低风险批处理，每项静态校验+编译 |
| M4空壳/H前缀 | 低 | rm -rf + 改名脚本，按E3事故教训验路径与D/??对账 |
| 架构立规（Settings契约） | 中 | 影响面广（site/core/ui均依赖data），分2步：先抽契约后迁引用，每步编译 |
| M3-b/c iOS 编译 | 中 | **门禁已放宽**（§0.1）：不再以"三端各录一段可播放 GIF"为闭环前置，iOS 编译/真机验证列为后续项。风险仍在 `AVPlayerItemVideoOutput` 的 5 处符号形态（已在代码内逐条标注 + 备用改法），需 macOS runner 首次编译时暴露 |
| GIF 时长档位 | 低 | **恢复三选 2s/3s/5s + 体积提示**（§0.1 否决固定档）；超预算档位由 `GifCapturePolicy` 提前过滤，防 OOM 另由 48 MiB 硬闸保证 |
| 文件头归属 | 低 | 已修正（§0.1）：`@project` 全仓统一 LoveHan1me，裸 `@author` 标注为上游原作者并指向 NOTICE；后续新增文件按此格式写 |
| M4:app瘦身下沉 | 中 | HCacheManager下沉牵连SAF/WorkManager，需保证桌面/iOS无回归 |
| 体验6方向战线 | 中 | 按M5依赖序一次一方向，子计划评审后再动；性能先埋点 |
| MIT评估 | 低 | 以parser/site实质重写+单测为硬标准，不過续GPL |

---

## 附：与既有文档的关系

- 《目标架构设计.md》E1/E2/E3 已落地部分不再重复；其"换 io.github 包名"提案被本次决策 #2 否决（以本文为准）
- 《E3_包结构重组方案.md》事故教训（批量脚本必验路径、D/?? 对账）适用于 M3-a/M4 的删除操作
- `tools/find_orphan_strings.py` 在 M1/M2 字符串清理时启用
- AI 生成的《主题配色调研与重构方案.md》仅供参考（已冻结部分以本文为准）
