<div align="center">

# LoveHan1me

**跨平台第三方客户端 · 一套代码，五端运行**

</div>

🔞 **R18 警告：未满 18 岁禁止下载和使用。**

LoveHan1me 是一个使用 Kotlin Multiplatform 从零重写的跨平台客户端，用于浏览、搜索、播放和管理第三方站点的公开视频页面内容。当前项目以 Compose Multiplatform、Navigation 3、ViewModel、StateFlow、Ktor、kSoup、Room、DataStore、mediamp 为主要技术栈，围绕视频浏览、详情播放、搜索、弹幕、用户列表、下载管理、评论、订阅、设置和隐私保护等功能组织。

本应用没有任何官方网站，**GitHub Release 是唯一下载及更新渠道**。

> [!IMPORTANT]
> 本应用与上游站点无任何关联，且自身**不存放、不托管、不分发任何内容**。全部数据实时来自第三方站点的公开页面。详见 [FAQ · 资源来源是什么？](#资源来源是什么)。

## 📱 应用截图

> [!NOTE]
> 截图待补充。计划放在 `image/screenshots/`，按「首页 / 播放页 / 播放设置」的表格排布，手机端宽度 240、桌面端宽度 480。

## ✨ 主要功能

### 播放

- 播放内核按平台适配（Android → Media3，桌面 → mpv，iOS → AVKit），播放器界面三端统一
- Anime4K 超分、画中画、进度条帧预览、倍速、画面比例、记忆续播、自动下一集
- 完整手势：左亮度 / 右音量 / 横滑进度 / 双击暂停 / 长按倍速 / 手势锁，桌面支持键盘快捷键

### 弹幕

- 引擎与渲染分离，播放位置帧平滑 + 位图光栅缓存，长弹幕场景不掉帧
- 双来源：弹弹play 时间轴弹幕 + 站内评论投影，可分别开关；屏蔽词与正则过滤、字号速度透明度调节

### 下载与数据

- 三端原生下载：并发闸门、断点续传、暂停恢复、分组管理、通知进度
- 整机备份：设置、账户态、下载记录、弹幕数据一次性导出导入；登录态独立存储，不随系统备份漫游

### 网络与反封锁

- 内建 ECH 网关（Go 实现，三端各带原生库）隐藏 SNI；DoH、HTTP / SOCKS 代理、自定义镜像站与 hosts
- Cloudflare 人机验证处理（内置验证窗 + 手动 Cookie 兜底），按可用性自动降级

### 界面与个性化

- 动态取色（materialkolor 真三端现场色算）、多套调色风格、深浅色主题
- 简体中文 / 繁體中文 / English 三套界面语言，含简繁转换与标签本地化；应用锁与安全模式

## 📥 下载

| 平台 | 要求 |
|---|---|
| Android | Android 10+；release 仅打包 `arm64-v8a` |
| iOS | iOS 15+ |
| Windows / macOS / Linux | 需 64 位系统 |

本仓库**尚无正式发布版本**，可自行从源码构建：

```bash
./gradlew :app:assembleRelease                              # Android
./gradlew :desktopApp:packageDistributionForCurrentOS      # 桌面（Msi / Dmg / Deb）
cd iosApp && xcodegen generate && open iosApp.xcodeproj     # iOS
```

需 JDK 21 与 Android SDK Platform 37；iOS 需 macOS + Xcode。日常开发用 `:app:assembleDebug`、`:desktopApp:run`。

## 🧰 技术总览

如果你是开发者，欢迎提交 PR 参与开发。以下几点可给你一个技术上的大概了解：

- [Kotlin 多平台](https://kotlinlang.org/docs/multiplatform.html)架构，使用 [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) 构建全部 UI；
- 播放层基于 [mediamp](https://github.com/open-ani/mediamp)，分平台适配 Media3 / mpv / AVKit，支持 Anime4K 超分；
- 自研弹幕引擎，轨道分配、重叠规避与帧平滑渲染三端一致；
- 网络层为模块化出站策略：内建 ECH 网关、DoH、多级代理与 Cloudflare 挑战处理，按可用性自动降级；
- Room KMP 持久化 + DataStore 偏好存储，支持整机备份导入导出；
- 模块按契约 / 引擎 / UI / 渲染面四层切分，分层约束由测试机械化守护。

## ❓ FAQ

### 资源来源是什么？

**全部内容都来自网络，本应用本身不存储任何数据。** 客户端只对第三方站点的公开页面做请求、解析与展示，不提供任何内容检索服务。因此若你所在地区无法访问相关站点，本应用同样无法访问。

### 需要登录吗？

不需要。浏览、搜索、播放均可匿名使用。登录后额外解锁收藏、订阅、播放列表、云端观影历史与评论互动。登录通过内置 WebView 完成，凭据仅保存在本机。

### 弹幕来源是什么？

两路：**弹弹play**（时间轴精确的关联弹幕，需自备凭据）与**站内评论投影**（把站内评论按时间排布后投射为弹幕，作为兜底主源）。弹幕引擎负责轨道分配与重叠规避。

## ⚠️ 免责声明

> [!WARNING]
> **本项目仅供学习与技术研究，且涉及成人向内容。请在使用前完整阅读本节。**

- 开发者与上游站点及其内容提供方**无任何隶属、合作或授权关系**
- 本应用**不存储、不托管、不分发任何内容**，全部数据来自第三方站点的公开页面
- 本应用涉及**成人向（R18）内容**，仅限**已满 18 周岁的成年用户**在符合当地法律法规的前提下使用
- 请勿在任何公开平台宣传、推广、搬运或引流本项目；请勿用于任何商业用途
- 用户对自身使用行为及后果独立承担全部责任；开发者不对因使用本软件产生的任何后果负责
- 若权利人认为本项目存在侵权，请通过 [Issues](https://github.com/Yuki-alice/LoveHan1me/issues) 联系

## 📄 许可证

- 本项目作为包含 AGPLv3 派生代码的整体，按 **GNU AGPLv3** 发布。之所以是 AGPLv3 而非 GPLv3：仓库包含移植自 [animeko](https://github.com/open-ani/animeko) 的源码，AGPLv3 第 13 条附加条款适用。
- 项目包含移植自 [animeko](https://github.com/open-ani/animeko)（AGPLv3）的播放器 UI 层代码、[Anime4K](https://github.com/bloc97/Anime4K)（MIT）的着色器资源，以及 [Han1meViewer](https://github.com/daisukiKaffuChino/Han1meViewer)（GPLv3）的客户端实现来源。完整的上游归属、继承自上游的归属声明与许可条款见 [NOTICE](NOTICE)。
- 第三方依赖的许可信息可在应用内「开源许可」页面查看。

## 🤝 贡献说明

- 提交前请确认可以通过 `:app:compileDebugKotlin`，并跑一遍 `:shared:desktopTest`
- 改动 `:video:*` 分层相关代码时，请确认 `ModuleLayeringTest` 仍然通过
- 代码风格遵循根目录 [`.editorconfig`](.editorconfig)：4 空格缩进、行宽 120、LF、UTF-8
- 提交信息建议使用 `feat|perf|fix|chore|test|docs(scope): 标题 —— 要点`

---

<div align="center">

**本项目为爱好者作品，与上游站点无任何关联。请不要在任何公开平台宣传本软件。**

</div>
