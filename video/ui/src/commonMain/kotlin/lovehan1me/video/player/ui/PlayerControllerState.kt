/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import lovehan1me.video.player.ui.support.ComposeKey
import lovehan1me.video.player.ui.support.TestOnly
import lovehan1me.video.player.ui.support.hoverable
import lovehan1me.video.player.ui.support.onKey

/**
 * @param initialVisibility 变更不会更新
 */
@Composable
fun rememberVideoControllerState(
    initialVisibility: ControllerVisibility = PlayerControllerState.DEFAULT_INITIAL_VISIBILITY,
): PlayerControllerState {
    return remember {
        PlayerControllerState(initialVisibility)
    }
}

enum class PlayerFocusTarget {
    PLAYER,
    TEXT_INPUT,
}

/** 协调焦点归属；[preferredTarget] 是意图，不是当前真正获得焦点的 Compose 节点。 */
@Stable
class PlayerFocusState {
    var preferredTarget: PlayerFocusTarget by mutableStateOf(PlayerFocusTarget.PLAYER)
        private set

    internal val requester = FocusRequester()
    private var textInputOwner: Any? = null

    fun preferPlayer() {
        textInputOwner = null
        preferredTarget = PlayerFocusTarget.PLAYER
    }

    internal fun preferTextInput(owner: Any) {
        textInputOwner = owner
        preferredTarget = PlayerFocusTarget.TEXT_INPUT
    }

    internal fun releaseTextInput(owner: Any) {
        if (textInputOwner === owner) {
            preferPlayer()
        }
    }

    internal fun requestPlayerFocus() {
        preferPlayer()
        requester.requestFocus()
    }

    internal fun restorePlayerFocusIfPreferred() {
        if (preferredTarget == PlayerFocusTarget.PLAYER) {
            requester.requestFocus()
        }
    }
}

/** 挂上播放器的焦点请求器；策略或 [reapplyKey] 变化时把焦点要回来。 */
@Composable
internal fun Modifier.playerFocusHost(
    state: PlayerFocusState,
    reapplyKey: Any?,
): Modifier {
    val preferredTarget = state.preferredTarget

    LaunchedEffect(state, preferredTarget, reapplyKey) {
        state.restorePlayerFocusIfPreferred()
    }
    return focusRequester(state.requester)
}

/**
 * 弹幕发送框这类文本输入用：拿到焦点就声明"输入框持有焦点"，Esc 交还给播放器，
 * 否则快捷键会在打字时被误触发。
 */
fun Modifier.playerTextInputFocus(
    state: PlayerFocusState,
    onEscape: (() -> Unit)? = null,
): Modifier = composed {
    val owner = remember { Any() }
    DisposableEffect(state, owner) {
        onDispose {
            state.releaseTextInput(owner)
        }
    }

    onFocusChanged {
        if (it.hasFocus) {
            state.preferTextInput(owner)
        }
    }.onKey(ComposeKey.Escape) {
        if (onEscape == null) {
            state.requestPlayerFocus()
        } else {
            onEscape()
        }
    }
}

/**
 * 控制器各元素的显示状态。
 *
 * 不是一个布尔而是六个槽位，因为"控制器隐藏"并不等于"什么都不显示"：
 * 横滑 seek 时要留一条独立进度条，直接拖进度条时必须保住正在吃触摸事件的那一条，
 * 锁控件时要露出解锁按钮。把这些塞进一个布尔会重新造出抢显隐的老问题。
 */
@Immutable
data class ControllerVisibility(
    val topBar: Boolean,
    val bottomBar: Boolean,
    val floatingBottomEnd: Boolean,
    val rhsBar: Boolean,
    val gestureLock: Boolean,
    val detachedSlider: Boolean,
) {
    companion object {
        @Stable
        val Visible = ControllerVisibility(
            topBar = true,
            bottomBar = true,
            floatingBottomEnd = false,
            rhsBar = true,
            gestureLock = true,
            detachedSlider = false,
        )

        @Stable
        val Invisible = ControllerVisibility(
            topBar = false,
            bottomBar = false,
            floatingBottomEnd = true,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = false,
        )

        /**
         * 控制器原本隐藏时的 seek 指示状态：只展示固定在播放器底部的独立进度条。
         */
        @Stable
        val DetachedSliderOnly = ControllerVisibility(
            topBar = false,
            bottomBar = false,
            floatingBottomEnd = false,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = true,
        )

        /**
         * 已有底栏交互时的 seek 状态：保留底栏原进度条及其布局，其他控制器元素仅在视觉上隐藏。
         * 直接拖动进度条必须使用此状态，避免替换正在接收触摸事件的组件。
         */
        @Stable
        val InlineSliderOnly = ControllerVisibility(
            topBar = false,
            bottomBar = true,
            floatingBottomEnd = false,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = false,
        )
    }
}

/**
 * 控件显隐的唯一仲裁者：谁请求、谁撤销都记在账上，由 [visibility] 一次算出，
 * 而不是各方直接改同一个布尔 —— 后者会让"正在拖动进度条"被 5 秒自动隐藏打断。
 */
@Stable
class PlayerControllerState(
    initialVisibility: ControllerVisibility = DEFAULT_INITIAL_VISIBILITY,
) {
    companion object {
        val DEFAULT_INITIAL_VISIBILITY = ControllerVisibility.Invisible
    }

    val focusState = PlayerFocusState()

    private var fullVisible by mutableStateOf(initialVisibility == ControllerVisibility.Visible)
    private val hasProgressBarRequester by derivedStateOf { progressBarRequesters.isNotEmpty() }
    private val hasInlineProgressSliderRequester by derivedStateOf {
        inlineProgressSliderRequesters.isNotEmpty()
    }

    /** 当前 UI 应当显示的状态 */
    val visibility: ControllerVisibility by derivedStateOf {
        // 由 hasProgressBarRequester、alwaysOn、fullVisible 算出正确的 ControllerVisibility
        if (hasInlineProgressSliderRequester) return@derivedStateOf ControllerVisibility.InlineSliderOnly
        if (alwaysOn) return@derivedStateOf ControllerVisibility.Visible
        if (fullVisible) return@derivedStateOf ControllerVisibility.Visible
        if (hasProgressBarRequester) return@derivedStateOf ControllerVisibility.DetachedSliderOnly
        ControllerVisibility.Invisible
    }

    /**
     * 切换显示或隐藏整个控制器。
     *
     * 此操作的优先级低于 [setRequestProgressBar]：此时若有人请求显示进度条，
     * `toggleEntireVisible(false)` 会延迟到那个人撤销请求之后才生效；
     * 若没有人请求，则立即生效。
     *
     * @param visible 为 `true` 时显示整个控制器
     */
    fun toggleFullVisible(visible: Boolean? = null) {
        fullVisible = visible ?: !fullVisible
    }

    val setFullVisible: (visible: Boolean) -> Unit = {
        fullVisible = it
    }

    private val alwaysOnRequests = SnapshotStateList<Any>()

    /** 总是显示，也就是不要在 5 秒后自动隐藏。 */
    val alwaysOn: Boolean by derivedStateOf {
        alwaysOnRequests.isNotEmpty()
    }

    /** 请求控制器总是显示。 */
    fun setRequestAlwaysOn(requester: Any, isAlwaysOn: Boolean) {
        if (isAlwaysOn) {
            if (requester in alwaysOnRequests) return
            alwaysOnRequests.add(requester)
        } else {
            alwaysOnRequests.remove(requester)
        }
    }

    private val progressBarRequesters = SnapshotStateList<Any>()

    private val inlineProgressSliderRequesters = SnapshotStateList<Any>()

    /**
     * 请求只显示底部控制栏内已有的 inline progress slider。
     *
     * 该进度条属于 bottom bar；进入此模式后仍保留整个 bottom bar 的布局，
     * 只是将进度条以外的控制器元素隐藏。因此正在接收触摸事件的进度条不会被替换。
     * 适用于直接拖动进度条，或控制器可见时开始的屏幕横滑。
     *
     * [setRequestProgressBar] 请求的是 bottom bar 之外的另一个 detached progress slider，
     * 用于控制器隐藏时的屏幕横滑，不能代替正在接收触摸事件的 inline progress slider。
     *
     * @param requester 请求方；取消时必须把同一实例传给 [cancelRequestInlineProgressSlider]。
     */
    fun setRequestInlineProgressSlider(requester: Any) {
        if (requester in inlineProgressSliderRequesters) return
        inlineProgressSliderRequesters.add(requester)
    }

    fun cancelRequestInlineProgressSlider(requester: Any) {
        inlineProgressSliderRequesters.remove(requester)
    }

    /**
     * 请求在控制器隐藏时显示独立的 detached progress slider。
     *
     * 该进度条位于 bottom bar 之外，是专门用于指示屏幕横滑 seek 的另一个组件；
     * 它不会保留或复用 bottom bar 内的 inline progress slider。适用于控制器隐藏时开始的屏幕横滑。
     *
     * 如果控制器当前完整显示，则完整控制器优先；控制器隐藏后，只要本请求仍存在，
     * 就会显示 detached progress slider，而不是让进度指示一并消失。
     *
     * @param requester 请求方；取消时必须把同一实例传给 [cancelRequestProgressBarVisible]。
     */
    fun setRequestProgressBar(requester: Any) {
        if (requester in progressBarRequesters) return
        progressBarRequesters.add(requester)
    }

    /** 取消显示进度条。 */
    fun cancelRequestProgressBarVisible(requester: Any) {
        progressBarRequesters.remove(requester)
    }

    @TestOnly
    fun getAlwaysOnRequesters(): List<Any> {
        return alwaysOnRequests
    }
}

interface AlwaysOnRequester {
    fun request()

    fun cancelRequest()
}

@Composable
fun rememberAlwaysOnRequester(
    controllerState: PlayerControllerState,
    debugName: String,
): AlwaysOnRequester {
    val requester = remember(controllerState, debugName) {
        object : AlwaysOnRequester {
            override fun request() {
                controllerState.setRequestAlwaysOn(this, true)
            }

            override fun cancelRequest() {
                controllerState.setRequestAlwaysOn(this, false)
            }

            override fun toString(): String {
                return "AlwaysOnRequester($debugName)"
            }
        }
    }
    DisposableEffect(requester) {
        onDispose {
            requester.cancelRequest()
        }
    }
    return requester
}

fun Modifier.hoverToRequestAlwaysOn(
    requester: AlwaysOnRequester,
): Modifier = hoverable(
    onHover = {
        requester.request()
    },
    onUnhover = {
        requester.cancelRequest()
    },
)
