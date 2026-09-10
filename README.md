# LoveHan1me

> 跨平台（Android / Desktop / iOS）的 hanime1.me 第三方客户端
> 基于 **Kotlin Multiplatform** + **Compose Multiplatform**，一套代码三端运行

> ⚠️ **开发中**：项目正在积极重构，尚未发布可用版本。

---

## 为什么做这个

hanime1.me 只有网页版。网页端能做的不多：没有离线、没有多端同步、播放器能力有限、搜索与内容组织较弱。

LoveHan1me 想做的是**把"能看"变成"看得爽"**：

- **播放体验优先** —— 手势、快捷键、倍速记忆、画中画、自动跳过、错误自动换源
- **离线观看** —— 下载与缓存，没网也能看
- **三端一致** —— Android / 桌面 / iOS 同一套体验
- **可改动** —— 设计系统与分层清晰，视觉与功能都能持续演进

---

## 平台支持

| 平台 | 说明 |
|---|---|
| Android | 主平台，功能最完整 |
| Desktop | Windows / macOS / Linux（JVM） |
| iOS | 规划中 |

---

## 技术栈

| 领域 | 选型 |
|---|---|
| 语言 / 构建 | Kotlin Multiplatform、Gradle (Kotlin DSL) |
| UI | Compose Multiplatform |
| 网络 | Ktor |
| HTML 解析 | ksoup |
| 持久化 | Room (KMP) + DataStore |
| 图片 | Coil 3 |
| 播放 | 平台原生（Android: Media3/mpv · Desktop: mpv · iOS: AVPlayer） |

---

## 构建

### 环境要求

- JDK 21
- Android SDK（`compileSdk 37`）
- Gradle 由 wrapper 提供，无需单独安装

### 命令

```bash
# 桌面端运行
./gradlew :desktopApp:run

# Android 调试包
./gradlew :app:assembleDebug

# 仅编译共享模块（最快的问题定位方式）
./gradlew :shared:compileKotlinDesktop
```

> Windows 环境的坑与解法见 [`Windows环境搭建说明.md`](Windows环境搭建说明.md)

---

## 项目结构

```
LoveHan1me/
├── shared/          # KMP 共享模块：网络 / 解析 / 数据 / UI / 播放器
├── app/             # Android 壳（平台能力 + 打包）
├── desktopApp/      # Desktop 壳
├── iosApp/          # iOS 壳（Xcode 工程）
└── build-logic/     # Convention plugins（KMP 模块配置统一收口）
```

共享模块的内部分层见 [`目标架构设计.md`](目标架构设计.md)。

---

## 免责声明

- 本项目是**第三方客户端**，**不提供、不托管、不分发任何媒体内容**，所有内容均来自第三方网站。
- 本项目仅供**技术学习与交流**，请勿用于任何商业用途。
- 使用者应自行遵守所在地区的法律法规。
- 本项目与 hanime1.me 官方无任何关联。

---

## 许可与归属

本项目以 **GNU General Public License v3.0** 发布（见 [`LICENSE`](LICENSE)）。
第三方代码归属与完整声明见 [`NOTICE`](NOTICE)。

> 本项目的部分实现参考了开源社区的工作，我们在此致谢。所有上游归属信息均保留在 `NOTICE` 中。
