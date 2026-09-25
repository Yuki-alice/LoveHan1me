# ROADMAP

> **单一真相源**（`docs/specs/2026-09-20-项目重新定位梳理-design.md` §5）。
> 只维护 Gate 进度与 DoD 勾选，不写长文。规划细节一律回 design 文档，任务提示词回 `docs/specs/`。
>
> 最近更新：**2026-09-24（P6 收尾）** —— Gate 3 P1-P6 全部落地（`:player` 独立 + 回归护栏 +
> 控件手术 + 超分 + mediamp 0.5.0 换底 + **P6 砍掉 Android mpv 内核并删净旧引擎**）；
> 剩 DoD 里需要真机的那几项手测。

## 当前焦点

**Gate 3「播放独立与超分」代码侧闭环：P1-P6 全部落地，DoD 只剩真机项（见该节）。**
Gate 2 的两个方向（源站覆盖、CF 攻坚）已完成，剩 comic 延后项与 DoD 收尾。

---

## Gate 1「收口」—— 让代码库只说一种语言

状态：**✅ 已闭（2026-09-20）**

- [x] G1-1A `:app` 纯逻辑下沉 shared/androidMain
- [x] G1-1B 入口瘦身 + Worker 下沉（`:app` 剩 5 个壳）
- [x] G1-2 compileSdk 统一单 37（appTargetSdk 降 36）
- [x] G1-3 死编号清理（`TODO P5/P7`、`M6 契约`）
- [x] G1-4 脚手架删除（P3aVerificationScreen / CookieSmoke）
- [x] G1-5 站点热切换 + 双源弹幕边缘收尾

**DoD**：`:app` 只剩纯入口 ✅ ｜ CI 不再需要双 SDK 特判 ✅ ｜ grep 不到 P5/P6/P7/M6 编号 ✅（P6d 历史 90 处按决策保留）｜ 自用一周无切站/弹幕 bug ✅

---

## Gate 2「功能纵深」—— 竞争力主战场

状态：**🟡 收尾中**（播放器方向已于 2026-09-23 移出，见 Gate 3）

- [x] **源站覆盖** · 作者页 `/user/{id}`（G2-1b-1）
- [x] **源站覆盖** · 系列清单 `/playlist`（G2-1b-2）
- [x] **源站覆盖** · 订阅作者直连 artistId（G2-1b-3）
- [ ] **源站覆盖** · comic 子站（**已决策延后单列**，设计归档在 `docs（不做参考）/漫画Tab设计.md`）
- [x] **CF 攻坚** · 免梯直连本地 ECH 网关（`echgate/`，超时间盒预期）
- [x] **CF 攻坚** · 代理兜底修正 + ECH 改写收敛 + 全客户端接入

**DoD**
- [x] 作者页 / 系列清单 三平台可用
- [ ] comic 三平台可用（**延后单列，不阻塞本 Gate**）
- [x] CF 自动过盾有实测结论（ECH 网关已落地）
- [x] 播放器功能清单对齐 animeko 对照表（缺失项移交 Gate 3）

---

## Gate 3「播放独立与超分」—— 内核独立成模块，再谈打磨

状态：**🟢 代码侧闭环**（2026-09-24 由"打磨攻坚"重排为 v2，方案A：`:player` 独立模块；
同日 P6 收尾完成，剩真机 DoD）

来源：`docs/specs/2026-09-24-Gate3-播放独立与超分-tasks.md`（v2，取代 09-23 版 v1）
依据：mediamp 按 v0.5.0 tag 重验 + animeko 超分读完 + scope 收敛（截图/GIF/预览帧/章节/音轨不做）

- [x] **P1 `:player` 模块** —— `feature/player` 全量 + 纯弹幕引擎 + MpvShaders 超分胶水独立成模块；
      引擎经构造注入拿配置，不再直读 `SettingsRepository`/`EchGate`；S 仅 `api` 消费。
      （2026-09-24 落地：双端编译 + 双模块测试全绿，见规划 §1.4 偏差记录）
- [x] **P2 回归护栏** —— 白屏（顶栏/底栏/设置行/弹幕落墨断言）+ 闪烁（稳帧重组计数）
      进 `:player`/`:shared` 测试源集并入 CI（2026-09-24：404 项全绿）
- [x] **P3 控件手术** —— 横向手势 seek 预览/提交分离（正确性）+ 可见性请求方集合
      + 手势仲裁与命中区审计（维持现状，结论落码）；过期注释清理（2026-09-24）
- [x] **P4 超分** —— 分辨率门控 + dscale 换 bilinear + 预建空图免重建 + Exo 真 CNN 链
      （PERF=Restore S，QUALITY=Restore M+Upscale M+自研 scaler）；
      真机帧时间 QA 待补（2026-09-24 代码落地，测试 410 项全绿）
- [x] **P5 mediamp 0.5.0 迁移** —— Android→exo 后端、iOS→avkit 后端、桌面不动；Surface 跟换；
      System 引擎删
      （2026-09-24 落地：三端编译 + 全量测试全绿）
- [x] **P6 收尾** —— 删旧引擎/占位/过期注释，DoD 验收
      （2026-09-24 落地：**产品决策砍掉 Android mpv 内核**，`MpvPlaybackEngine` / `ExoPlaybackEngine` /
      `IosAVPlaybackEngine`(+其测试) / `AnimeShaders`+`cacert.pem` 全删；设置页「内核选择」行与
      `playerKernelSelection` 能力位一并删除；过期注释清 10 处；三端编译 + 410 项测试全绿。
      详见规划文档 §6）

**DoD**
- [x] `:player` 三端独立编译，引擎零直读设置/网关（本机实测 desktop/android/iOS 三目标全绿；
      CI 双 runner 覆盖 Windows + macOS）
- [ ] 缓冲/isBuffering 三端真值（**代码已换成 mediamp 三轴真值**，待真机确认观感）
- [ ] `supportedAspectModes()` 与真机实测一致（代码三档全给；**待真机矩阵**）
- [ ] 超分诚实 + 1080p+ 不放大 + 帧时间实测数据（代码齐；**帧时间实测缺设备与流，未做**）
- [x] 回归测试进 CI（`:player` 42 + `:shared` 368 = 410 项，本地重跑 0 失败）
- [x] Android mpv 内核去留**已定：砍掉**（2026-09-24 产品决策，见 P6）

**P6 附带的行为变化（不是 bug，是决策的必然结果，已落码注释）**
- Android 默认档（mediamp-exo）与 iOS 的**截图/GIF 入口消失**：原唯一实现方是 Exo/mpv 两引擎，
  两者都已删；`supportsFrameCapture()` 默认 false ⇒ UI 自动不出入口。抓帧链路按规划**有意保留**
  （`GifCaptureDialog` / `ScreenshotCapturer` / `AndroidFrameCapture` / `IosFrameCapture`），
  将来接回来只差在 mediamp 引擎上实现 `grabFrameArgb`。
- 设置页**「播放内核」选择行消失**（三平台一致）：只剩一个真引擎，留着就是假开关。
  `AppSettings.playerKernel` 与 DataStore 键保留（存储兼容），值已成惰性。

---

## Gate 4「齐整」—— 三平台功能对齐（原 Gate 3）

状态：**⬜ 未启动**

- [ ] 平台能力补位：iOS 触感/相册写入/ImageLoader/下载收尾；桌面下载收尾、调试构建对话框
      （代码里已预埋 13 处 `Gate4-平台能力` 注释）
- [ ] Gate 2/3 新功能三平台查漏
- [ ] 性能基线：live test 加门槛/隔离策略；列表滑动/搜索响应建基准
- [ ] 下载链路三平台拉齐（`VideoCacheStore` 仍 no-op；`PlatformStores` stale 注释同步更新）

**DoD**
- [ ] 全仓无占位型 no-op（平台本无语义的 by-design no-op 除外，需注释标明）
- [ ] 三平台功能清单逐项打勾
- [ ] 播放器连续自用两周无崩溃/白屏

---

## Gate 5「发布」—— GitHub 开仓（原 Gate 4）

状态：**⬜ 未启动**

- [ ] README（定位/截图/三平台构建说明）+ 旧 `docs（不做参考）` 目录处置 + 断链修复
- [ ] Release 流水线：tag → APK(arm64) + 桌面安装包 + iOS 源码说明
- [ ] 敏感信息终检（local.properties、镜像 URL、cookie 路径、AppUpdateChecker 硬编码 update URL）
- [ ] 合规终检：NOTICE、aboutlibraries 第三方许可、上游许可声明
- [ ] 版本命名定夺（26.3.2 日历式 vs 语义化）
- [ ] 仓库卫生：issues 模板 + .gitignore 复查

**DoD**：陌生人 clone 后按 README 三平台都能构建出可用产物。

---

## 文档地图

`docs/` 下全是 Markdown，没有构建产物。找东西从这里开始：

| 路径 | 定位 | 什么时候读 |
|---|---|---|
| **`docs/ROADMAP.md`** | 本文件 —— **单一真相源**，只有 Gate 进度与 DoD 勾选 | 任何时候先读它 |
| `docs/specs/2026-09-20-项目重新定位梳理-design.md` | 项目定位与五道 Gate 路线图定稿 | 需要知道"为什么这么排" |
| `docs/specs/2026-09-20-Gate1-tasks.md` | Gate 1 子任务提示词 + 关闭记录 | 回溯 Gate 1 做了什么 |
| `docs/specs/2026-09-24-Gate3-播放独立与超分-tasks.md` | **Gate 3 任务规划 v2**（方案A：`:player` 独立 + 超分 + mediamp 0.5.0 迁移） | 开工 Gate 3 |
| `docs/specs/2026-09-23-Gate3-播放模块打磨攻坚-tasks.md` | Gate 3 规划 v1（**已取代**，仅引擎事实核验方法可参考） | 归档 |
| `docs/specs/2026-09-24-G3-0-mediamp迁移工作量评估.md` | 换底 mediamp 的**许可分层 + 分场景工作量 + 风险** | 拍 G3-0 决策 |
| `docs/specs/2026-09-20-G2-播放器对照表.md` | 播放器能力清单（animeko × 本仓） | 判断"某能力有没有" |
| `docs/specs/2026-09-20-G2-1a-探站结论.md` | 作者页/系列清单的路由与选择器 | 动站点解析 |
| `docs/specs/2026-09-21-网络与CF直连优化分析.md` | 免梯直连（ECH 网关）方案分析 | 动网络层 |
| `docs/播放页-对比animeko差距清点与重构裁定.md` | 播放模块差距清点与四层裁定 | 播放模块决策源头 |
| `docs/播放页-对比animeko差距清点与重构裁定-审阅.md` | 上文的**逐条代码复核**（纠了 3 处错） | **读上文前先读它** |
| `docs/播放页-对齐与命中区规范.md` | 间距与命中区数值规范（⚠️ 顶部有状态更正） | 调控件几何 |
| `docs/播放页-三端布局对比与控件优化方案.md` | 三端像素实测与优化项 | 同上 |
| `docs/2026-09-24-Codex-ComputerUse-测试流程与提示词.md` | **视觉级自动化测试 playbook**（安全红线 + 场景清单 P0-P4 + 可直接粘贴的提示词） | 交给 computer-use agent 跑 UI 回归前 |
| `docs/2026-09-24-全功能测试矩阵与流程.md` | **全功能测试矩阵**（38 路由全量：启动/首页/搜索/详情/播放器/弹幕/我的/下载/账号/网络/设置 14 页…） | 要"把所有功能都测一遍"时，先读它 |
| `docs（不做参考）/` | **归档**：旧 P/M 体系文档、Kazumi/animeko 对照、漫画 Tab 设计等 | 仅作史料，**不要当依据** |

> ⚠️ **文档互相矛盾时的优先级**：`ROADMAP` > `design` > `specs/*-tasks` > 专题分析文档。
> 已发现两处历史矛盾，均已在文首标注：`播放页-对齐与命中区规范.md`（声称已移植 animeko 源码，
> 实际未落地）、`2026-09-20-G2-播放器对照表.md`（帧预览"不做"与 Gate 3 重新立项冲突）。

## 编号约定

- 旧 P/M 体系彻底废弃；新任务一律挂 Gate 编号。
- **历史已完成标记不回改**：`G2-3b`（画面比例/画面调节）、`P6d-*` 等记录的是"在哪个阶段做完的"，保留。
- **指向未来的编号必须同步**：2026-09-23 因 Gate 重排，13 处 `Gate3-平台能力` 已改为 `Gate4-平台能力`
  （`grep -rn "Gate3" --include="*.kt" shared app desktopApp` 应为零命中）。
- 判定法：这个编号说的是**已完成的过去**还是**未开工的未来**？前者留，后者改。
