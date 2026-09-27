/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.support

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * 悬停计数。必须计数而不是布尔：嵌套的两个可悬停区域会各自发 Enter/Exit，
 * 布尔版本里内层 Exit 会把外层的悬停状态一起清掉。
 */
fun Modifier.hoverable(
    onHover: () -> Unit,
    onUnhover: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val onHoverUpdated by rememberUpdatedState(onHover)
    val onUnhoverUpdated by rememberUpdatedState(onUnhover)
    LaunchedEffect(true) {
        val hoverInteractions = mutableListOf<HoverInteraction.Enter>()
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> hoverInteractions.add(interaction)
                is HoverInteraction.Exit -> hoverInteractions.remove(interaction.enter)
            }
            if (hoverInteractions.isNotEmpty()) {
                onHoverUpdated()
            } else {
                onUnhoverUpdated()
            }
        }
    }
    hoverable(interactionSource)
}

typealias ComposeKey = Key

/** 按键按下时吞掉事件，抬起时执行 [onEnter]，避免同一个键被上层再处理一次。 */
fun Modifier.onKey(
    key: Key,
    onEnter: () -> Unit,
): Modifier = onPreviewKeyEvent { keyEvent ->
    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == key) {
        true
    } else if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == key) {
        onEnter()
        true
    } else {
        false
    }
}
