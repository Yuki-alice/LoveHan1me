@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package lovehan1me.feature.player

import platform.Foundation.NSNotificationCenter

/**
 * C1b：全屏状态的 Kotlin→Swift 桥（与 `EchGatePortReporter` /
 * `NetworkChangeBridge` 同型：单向通知，SwiftUI 侧订阅）。
 *
 * 为什么不用共享状态轮询：全屏是瞬时手势动作，通知即达、无需留档；
 * SwiftUI 经 `onReceive` 直接改 `@State`，与 `ContentView` 的修饰符同帧生效。
 */
object IosFullscreenBridge {

    const val NOTIFICATION_NAME = "lovehan1me.fullscreen.changed"

    const val KEY_FULLSCREEN = "fullscreen"

    const val KEY_FORCE_LANDSCAPE = "forceLandscape"

    fun publish(fullscreen: Boolean, forceLandscape: Boolean) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            NOTIFICATION_NAME,
            `object` = null,
            userInfo = mapOf(
                KEY_FULLSCREEN to fullscreen,
                KEY_FORCE_LANDSCAPE to forceLandscape,
            ),
        )
    }
}
