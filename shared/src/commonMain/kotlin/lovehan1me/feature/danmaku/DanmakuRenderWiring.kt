package lovehan1me.feature.danmaku

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.data.SettingsRepository

// 设置 → 弹幕观感的接线（Gate3-P1 后留守 :shared 的部分）。
// 纯件（DanmakuRenderOptions 数据类 + DanmakuLayer 绘制件）已进 :player，
// 这里只剩"读设置流"这两小段——它们依赖 SettingsRepository，不能进内核模块。

fun AppSettings.danmakuRenderOptions(): DanmakuRenderOptions = DanmakuRenderOptions(
    fontSizeSp = danmakuFontSizeSp,
    opacityPercent = danmakuOpacityPercent,
    displayAreaPercent = danmakuDisplayAreaPercent,
    speedPercent = danmakuSpeedPercent,
)

// 跟着设置走的弹幕观感。改滑杆要立刻反映到正在播放的画面上，所以这里订阅的是流
// 而不是启动时的一次性快照。
@Composable
fun rememberDanmakuRenderOptions(): DanmakuRenderOptions {
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    return remember(settings) { settings.danmakuRenderOptions() }
}
