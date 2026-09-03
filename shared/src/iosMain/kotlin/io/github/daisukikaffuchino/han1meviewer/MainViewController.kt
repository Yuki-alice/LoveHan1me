package io.github.daisukikaffuchino.han1meviewer

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS 端入口：Xcode 工程把返回的 UIViewController 设为 rootViewController。
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
