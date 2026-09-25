package lovehan1me.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AudioToolbox.AudioServicesPlaySystemSound

// iOS：系统 peek 触感 1519（Gate4-平台能力补齐）。
// 不用 UIKit 的 UIImpactFeedbackGenerator：它的 UIImpactFeedbackStyle 常量
// 在当前 Kotlin/Native 互操作里不可见（类可解、常量三种写法皆不可解，已实测），
// 而 AudioToolbox 是稳定 C API。1519 = peek（轻触），与 Android 的
// CONTEXT_CLICK 同级语义（短促确认感，不是长振/重击）。
// 开关仍走共用的 SettingsRepository.hapticFeedbackEnabled（HapticFeedback.invoke 内判定）。
@Composable
internal actual fun rememberHapticFeedback(): HapticFeedback {
    return remember {
        HapticFeedback { AudioServicesPlaySystemSound(1519u) }
    }
}
