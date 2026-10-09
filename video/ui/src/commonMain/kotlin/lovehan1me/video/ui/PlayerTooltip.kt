package lovehan1me.video.ui

import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lovehan1me.video.player.ui.support.LocalPlatform
import lovehan1me.video.player.ui.support.isDesktop

/**
 * 图标按钮的桌面悬停提示（P1 #7 接线）。
 *
 * 只在桌面平台包 `TooltipBox` —— 移动端不包：不改变触屏交互基线（长按不弹提示），
 * 纯触屏设备也因此完全不受影响（评审口径即「桌面 IconButton 加 tooltip」）。
 *
 * 提示气泡走 M3 默认键位（inverseSurface 底 + inverseOnSurface 字），在播放器强制
 * 深色皮肤下自动呈现"浅底深字"，压在任意视频画面上都清晰可辨。
 *
 * @param state 提示状态；默认自持。暴露它是给渲染测试一个可控入口
 *   （`rememberTooltipState(initialIsVisible = true)` 可直接静态出图）。
 */
@Composable
fun PlayerTooltipBox(
    text: String,
    modifier: Modifier = Modifier,
    state: TooltipState = rememberTooltipState(),
    content: @Composable () -> Unit,
) {
    if (!LocalPlatform.current.isDesktop()) {
        content()
        return
    }
    TooltipBox(
        // 显式锚定在按钮上方（8dp 间距）——与项目既有 SpeedSliderPopup 同一写法；
        // 旧名 rememberPlainTooltipPositionProvider 已废弃。
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Above,
            spacingBetweenTooltipAndAnchor = 8.dp,
        ),
        tooltip = { PlainTooltip { Text(text) } },
        state = state,
        modifier = modifier,
        content = content,
    )
}
