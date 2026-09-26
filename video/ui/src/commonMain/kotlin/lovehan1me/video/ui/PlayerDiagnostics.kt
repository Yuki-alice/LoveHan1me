package lovehan1me.video.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 播放器控件的诊断出口。
 *
 * 日志与埋点两条通道的实现在编排层（`:shared` 的 `LogUtil` / `PlayerTrace`），
 * 本模块只负责在事件发生处调出口；不注入时静默丢弃。
 */
interface PlayerDiagnostics {
    fun log(tag: String, message: String)

    fun event(name: String, detail: String = "")
}

private object NoOpPlayerDiagnostics : PlayerDiagnostics {
    override fun log(tag: String, message: String) = Unit

    override fun event(name: String, detail: String) = Unit
}

val LocalPlayerDiagnostics = staticCompositionLocalOf<PlayerDiagnostics> { NoOpPlayerDiagnostics }