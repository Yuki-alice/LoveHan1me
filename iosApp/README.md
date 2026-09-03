# iosApp — iOS 壳工程

本目录只承载 iOS 原生壳，全部 UI 与业务来自 `:shared`（Compose Multiplatform）。

## 快速开始

```bash
# 1. 安装工程生成器（首次）
brew install xcodegen

# 2. 生成 Xcode 工程
cd iosApp && xcodegen generate

# 3. 打开并运行
open iosApp.xcodeproj   # 选中 iosApp target，跑模拟器
```

首次构建会先执行 Gradle 任务 `:shared:embedAndSignAppleFrameworkForXcode`
编译出 `ComposeApp.framework`（需要在 macOS + Xcode 环境，已满足）。

## 结构

| 文件 | 作用 |
|---|---|
| `iosApp/iOSApp.swift` | SwiftUI App 入口 |
| `iosApp/ContentView.swift` | `UIViewControllerRepresentable` 包装共享的 `MainViewController()` |
| `project.yml` | xcodegen 声明式工程配置 |

## 共享入口链路

```
iOSApp.swift → ContentView.swift → MainViewControllerKt.MainViewController()
                                        ↓ (ComposeApp.framework)
                            shared/src/iosMain/.../MainViewController.kt → App()
```

## 说明

- 共享框架名固定为 **ComposeApp**（见 `shared/build.gradle.kts` 中 `baseName = "ComposeApp"`）。
- 真机（iosArm64）与模拟器（iosSimulatorArm64）各自产出一份框架，Gradle 会按目标落盘。
- 若改了共享模块名或 baseName，需要同步更新 `project.yml` 中的 `OTHER_LDFLAGS` 与脚本任务名。
- iOS 端注意：自定义 DNS / DoH、WorkManager 下载、Glance 小组件、Biometric 在 iOS 无对应实现，
  需按规划文档 P8 做降级或替代。
