package lovehan1me.video.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 播放器控件的触感回调。
 *
 * 本模块不做平台触感实现：真实实现要读 `SettingsRepository.hapticFeedbackEnabled`，
 * 且 iOS 要 `AudioToolbox`、Android 要 `LocalView` —— 都属编排层知识，由 `:shared`
 * 注入；不注入时是 no-op。
 */
val LocalPlayerHaptic = staticCompositionLocalOf<() -> Unit> { {} }