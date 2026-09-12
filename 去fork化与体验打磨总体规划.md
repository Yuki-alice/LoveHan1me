# 去fork化与体验打磨总体规划

> 制定：2026-09-12 ｜ 状态：**待评审，未开工** ｜ 前置：连续 4+1 轮需求拷打，全部决策已冻结
> 定位：LoveHan1me 从"能跑的 fork"变成"独立项目"的总路线图。只含决策与验收，不含实现细节。

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
| 16 | infra H 前缀 | **改语义化**（HDns/HProxySelector/HCacheManager/HFileManager/HANIME 常量；HKeyframe 随功能删除自然消失） |
| 17 | Parser 重写 | **下阶段**（M6，换 MIT 的必经之路） |
| 18 | 体验方向 | 6 个全要：播放 / 下载离线 / 搜索发现 / 订阅追番 / 账号多端 / 启动性能 |
| 19 | 推进节奏 | **先出完整计划**（本文档），评审后再动 |

---

## M1 身份切换（E1）：从 Han1meViewer 变成 LoveHan1me

**目标**：用户在任何地方看不到 Han1meViewer 和 daisukiKaffuChino（除 NOTICE/GPL 归属区）。

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

## M4 结构收尾

| 项 | 内容 | 验收 |
|---|---|---|
| 空壳删除 | 约20空目录 + dao 空壳 | `find -type d -empty`（除构建产物）清零 |
| `:app` 瘦身 | 搬空可移植页（以 M2 为准），终态纯壳；顶层散件（Constants/HFileManager/HCacheManager）按层归位 | `:app` 无 `feature/` 实现，只剩壳 |
| 包内整理 | data/network 拦截器链落点注释更新（jvmMain 约定保留）；`ui/screen` 等 E3 残留引用复查 | 静态检查 + 三端编译 |
| reference | 保留不动 | — |
| 模块拆分 | 本轮不做；若单模块编译突破痛点再议 | — |

---

## M5 体验打磨（6 方向，顺序建议如下，理由见备注）

> 顺序不是优先级（6 个全要），是**依赖顺序**：账号同步是订阅/多端的基础，启动性能越早量越好。

| 序 | 方向 | 首批目标（细化开子计划） | 备注 |
|---|---|---|---|
| 1 | 账号多端 | 登录态统一、备份恢复可靠、登出/多设备一致 | 地基，其他方向依赖它 |
| 2 | 启动性能 | 冷启动耗时量化→压首屏；DataStore/Coil/引擎初始化错峰 | 先立基线（现在没数据） |
| 3 | 播放体验 | 手势/倍速记忆/跳过片头尾/错误换源/超分开关可达性 | 主战场 |
| 4 | 搜索发现 | 沿用已落地的筛选栏；补排序语义、空态、历史管理 | 站在已交付上继续 |
| 5 | 订阅追番 | 更新检测、提醒、新集标出 | 依赖账号 |
| 6 | 下载离线 | 队列可靠、断点续传、存储管理、本地与在线一致 | 涉及系统能力最多，放最后 |

每个方向开工前单独出子计划（目标+验收+不碰清单），不在本文档展开。

---

## M6 Parser 重写 + MIT 评估（下阶段）

- 范围：`site/hanime1/Parser.kt`（1194 行）+ `site/getchu` 按"站点解析隔离"契约重写（见《目标架构设计.md》§3.1），输出可测的纯函数解析器 + 单测
- 完成后做法务复核：重写比例是否足以换 MIT/Apache；未达标则继续保持 GPLv3
- 在此之前：**对外口径一律 GPLv3**，关于页/仓库 LICENSE 不动

---

## 风险与阻塞

| 风险 | 等级 | 对策 |
|---|---|---|
| 新仓库 URL 未定 | ✅ 已解决（`Yuki-alice/LoveHan1me`） |
| GIF 转码三端取帧/编码能力不明 | 高 | M3-b 先 spike（Android/iOS/桌面各半天），spike 失败则降级为"连拍合成"并重新评审 |
| H 帧删除牵连播放器/备份/数据库版本 | 中 | 按 M3-a 清单逐项删，每项编译验证；数据库版本+1 |
| 体验 6 方向一次铺开导致战线过长 | 中 | 按 M5 顺序一次只开一个方向，子计划评审后再动 |
| MIT 评估标准主观 | 低 | 以"parser/site 是否实质重写 + 单测覆盖"为硬标准，不過就继续 GPL |

---

## 附：与既有文档的关系

- 《目标架构设计.md》E1/E2/E3 已落地部分不再重复；其"换 io.github 包名"提案被本次决策 #2 否决（以本文为准）
- 《E3_包结构重组方案.md》事故教训（批量脚本必验路径、D/?? 对账）适用于 M3-a/M4 的删除操作
- `tools/find_orphan_strings.py` 在 M1/M2 字符串清理时启用
- AI 生成的《主题配色调研与重构方案.md》仅供参考（已冻结部分以本文为准）
