/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

@file:OptIn(ExperimentalContracts::class)

package lovehan1me.video.player.ui.support

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * 运行平台。播放器控件只在"桌面还是移动"和"Windows / macOS / Linux 哪一家"两处分岔，
 * 所以只列这五种，不带 CPU 架构。
 */
sealed class Platform(val name: String) {
    final override fun toString(): String = name

    sealed class Mobile(name: String) : Platform(name)

    data object Android : Mobile("Android")

    data object Ios : Mobile("iOS")

    sealed class Desktop(name: String) : Platform(name)

    data object Windows : Desktop("Windows")

    data object MacOS : Desktop("macOS")

    data object Linux : Desktop("Linux")
}

private val cachedPlatform: Platform by lazy(LazyThreadSafetyMode.NONE) { currentPlatformImpl() }

fun currentPlatform(): Platform = cachedPlatform

internal expect fun currentPlatformImpl(): Platform

inline fun Platform.isDesktop(): Boolean {
    contract { returns(true) implies (this@isDesktop is Platform.Desktop) }
    return this is Platform.Desktop
}

inline fun Platform.isMobile(): Boolean {
    contract { returns(true) implies (this@isMobile is Platform.Mobile) }
    return this is Platform.Mobile
}

inline fun Platform.isAndroid(): Boolean {
    contract { returns(true) implies (this@isAndroid is Platform.Android) }
    return this is Platform.Android
}

inline fun Platform.isIos(): Boolean {
    contract { returns(true) implies (this@isIos is Platform.Ios) }
    return this is Platform.Ios
}

inline fun Platform.isMacOS(): Boolean {
    contract { returns(true) implies (this@isMacOS is Platform.MacOS) }
    return this is Platform.MacOS
}

inline fun Platform.isWindows(): Boolean {
    contract { returns(true) implies (this@isWindows is Platform.Windows) }
    return this is Platform.Windows
}

inline fun Platform.isLinux(): Boolean {
    contract { returns(true) implies (this@isLinux is Platform.Linux) }
    return this is Platform.Linux
}

/** 桌面才有滚轮，移动端滚动容器上的滚轮处理路径要绕开。 */
inline fun Platform.hasScrollingBug() = isDesktop()

@Stable
val LocalPlatform = staticCompositionLocalOf { currentPlatform() }
