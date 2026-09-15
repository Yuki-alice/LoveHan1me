package lovehan1me.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.Res
import lovehan1me.cancel
import lovehan1me.confirm
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.SonnerToast
import lovehan1me.delete
import lovehan1me.edit
import lovehan1me.feature.player.PlatformVideoSurface
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.feature.player.PlayerDefaults
import lovehan1me.feature.player.posterBlur
import lovehan1me.gif_capture
import lovehan1me.here_is_empty
import lovehan1me.ic_arrow_back_ios
import lovehan1me.ic_fast_forward
import lovehan1me.ic_fast_rewind
import lovehan1me.ic_fullscreen
import lovehan1me.ic_home
import lovehan1me.ic_light_mode
import lovehan1me.ic_lock
import lovehan1me.ic_pause
import lovehan1me.ic_play_arrow
import lovehan1me.ic_refresh
import lovehan1me.ic_unlock
import lovehan1me.ic_volume_up
import lovehan1me.playback_finished
import lovehan1me.player_anime4k_label
import lovehan1me.player_auto_quality
import lovehan1me.player_gesture_brightness
import lovehan1me.player_gesture_progress
import lovehan1me.player_gesture_volume
import lovehan1me.player_play_from_beginning
import lovehan1me.player_progress_percent
import lovehan1me.player_speed_format
import lovehan1me.player_time_format
import lovehan1me.replay
import lovehan1me.retry
import lovehan1me.screenshot
import lovehan1me.super_resolution_off
import lovehan1me.super_resolution_performance
import lovehan1me.super_resolution_quality
import lovehan1me.sure_to_delete
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.FilledTonalButton
import lovehan1me.ui.component.FilledTonalIconButton
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.transition.sharedCoverElement
import lovehan1me.video_loading_failed
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 播放器中央控件：大号播放/暂停键、播放中自动隐藏时的小暂停键、锁定按钮。
 *
 * 从 `VideoPlayerUi` 主函数提取；三块都只读主函数状态（零改写），
 * 锁定按钮的 `align(CenterEnd)` 需要 BoxScope 接收者。
 */
@Composable
internal fun BoxScope.PlayerCenterControls(
    isLocked: Boolean,
    activeSidePanel: PlayerSidePanel?,
    gestureType: GestureIndicatorType?,
    isPlaybackEnded: Boolean,
    showLoading: Boolean,
    isPlaying: Boolean,
    effectiveShowControls: Boolean,
    showUnlockButton: Boolean,
    playerUiVisible: Boolean,
    onPlayClick: () -> Unit,
    onLockClick: () -> Unit,
    /**
     * Kazumi 哔哩哔哩风：中央没有大播放键（整块中央是透明手势层，暂停只走底栏）。
     * 只在宽屏/全屏由调用方打开，窄屏竖屏保持 false → 原分支逐像素不变。
     * 锁定按钮不受影响（Kazumi 右侧锁照样有）。
     */
    bilibiliStyle: Boolean = false,
) {
    // B 站风下中央大键与 minimal 小暂停键都不画（与上面的大键互斥的 minimal 键同理）。
    val showCenterPlayControls = !bilibiliStyle
/**
 * 中间播放/暂停按钮
 */
AnimatedVisibility(
    visible =
        showCenterPlayControls &&
                !isLocked &&
                activeSidePanel == null &&
                gestureType == null &&
                !isPlaybackEnded &&
                !showLoading &&
                (!isPlaying || effectiveShowControls),
    enter = fadeIn(),
    exit = fadeOut(),
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (!isPlaying) {
            FilledTonalIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(HanimeDefaults.PlayerSizes.centerButton)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_play_arrow),
                    contentDescription = null,
                    modifier = Modifier.size(HanimeDefaults.PlayerSizes.centerIcon)
                )
            }
        } else {
            // Playing: small pause button when controls are visible
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(HanimeDefaults.PlayerSizes.centerButton)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_pause),
                    contentDescription = null,
                    tint = HanimeDefaults.Overlay.onScrim,
                    modifier = Modifier.size(HanimeDefaults.PlayerSizes.centerIcon)
                )
            }
        }
    }
}

/**
 * 最小控件（Minimal controls）：**播放中且控件已自动隐藏**时，中央给一枚小号暂停键，
 * 点它**直接暂停**，而不是把整排控件叫回来（Media3 minimal controls 的行为）。
 *
 * 与上面的大键互斥：大键要求 `!isPlaying || effectiveShowControls`，
 * 本键要求 `isPlaying && !effectiveShowControls` —— 两者不可能同时为真。
 * 锁屏态不出现（PiP 在壳层被折算成 `isLocked = true`，见 VideoShellContent 的两处调用，
 * 所以 PiP 也一并排除）。
 *
 * 只有 `onPlayClick` 一个动作、点击后只切换播放状态：事务脚本只有一步，
 * 因此**绝不可能**出现"点了没反应"（PiP 态同理：不显示就不会被误点）。
 */
AnimatedVisibility(
    visible =
        showCenterPlayControls &&
                isPlaying &&
                !effectiveShowControls &&
                !isLocked &&
                activeSidePanel == null,
    enter = fadeIn(),
    exit = fadeOut(),
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier
                // 透明无底：命中区 48dp（M3 硬指标）/ 视觉 XS(32dp)，向上仍报告 XS
                .playerHitTarget(visual = HanimeDefaults.Sizes.controlXS)
                .size(PLAYER_MIN_TOUCH_TARGET)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_pause),
                contentDescription = null,
                tint = HanimeDefaults.Overlay.onScrim,
                modifier = Modifier.size(HanimeDefaults.Sizes.controlXS)
            )
        }
    }
}

/**
 * 锁定按钮 —— 交给具名槽位 [PlayerGestureLockButton]（实现见文件末尾）。
 * 保留这一行调用是为了让三个中央控件的**可见性条件**能在同一屏里读完。
 */
PlayerGestureLockButton(
    isLocked = isLocked,
    showUnlockButton = showUnlockButton,
    playerUiVisible = playerUiVisible,
    onLockClick = onLockClick,
)
}

/**
 * 手势锁槽位（对应 animeko `VideoScaffold` 的 `gestureLock` 具名槽）。
 *
 * **可见性规则**（两侧源码逐个核对后的实情）：
 * - 未锁定：跟 [playerUiVisible] 一起显隐 —— 它就是"控件的一部分"；
 * - 已锁定：**点屏**才亮 [showUnlockButton] 那一下（3s），其余时间收起。
 *
 * 第 2 条与 animeko 完全一致：那边 `ControllerVisibility.Invisible.gestureLock = false`，
 * 而 `withGestureLocked(true)` 只关 topBar / bottomBar / rhsBar / detachedSlider、
 * **不动 gestureLock** —— 所以锁定态的路径是"点屏 → 只剩锁钮可见 → 超时收起"。
 * （曾误记为"animeko 锁定时锁钮常驻"，核对 `PlayerControllerState.kt:126-180` 后已纠正。）
 *
 * 之所以仍抽成独立具名槽位：位置与显隐规则要能独立于"中央大键""最小暂停键"阅读，
 * 免得以后调锁交互时又被大键的可见性条件带着走。
 */
@Composable
internal fun BoxScope.PlayerGestureLockButton(
    isLocked: Boolean,
    showUnlockButton: Boolean,
    playerUiVisible: Boolean,
    onLockClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = if (isLocked) showUnlockButton else playerUiVisible,
        modifier = Modifier.align(Alignment.CenterEnd),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        FilledIconButton(
            onClick = onLockClick,
            modifier = Modifier
                .padding(end = HanimeDefaults.Spacing.extraLarge)
                .size(HanimeDefaults.PlayerSizes.lockButton),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = HanimeDefaults.Overlay.lockButton
            )
        ) {
            Icon(
                painter = if (isLocked)
                    painterResource(Res.drawable.ic_lock)
                else
                    painterResource(Res.drawable.ic_unlock),
                contentDescription = null,
                tint = HanimeDefaults.Overlay.onScrim
            )
        }
    }
}
