package lovehan1me.video.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/**
 * 播放器控件可见性的唯一仲裁者。
 *
 * 此前可见性由一个布尔量承载，而它被单击、倒计时、手势结束、锁定态多处直接赋值；
 * 只要有一条路径漏判"还有人在交互"，控件就会在弹窗/拖动期间被收走。这里把输入拆成
 * 三份互不覆盖的来源，可见性只由它们**派生**，任何一方都不再直接改写可见性本身：
 *
 * - **令牌**（[requestAlwaysOn] / [cancelAlwaysOn]）：谁在交互谁登记，令牌非空期间恒显示。
 *   登记会一并清掉"已到点"标记，撤销令牌后从零重新计时。
 * - **用户意图**（[toggleByTap] / [showByUserInteraction] / [hideByUserInteraction]）。
 * - **倒计时到点**（[onAutoHideElapsed]）：唯一能把控件藏起来的非用户路径。
 *
 * 瞬态输入（手势进行中、指针悬停）不进本类：它们由各自的手势回调自清零，
 * 转成令牌反而有"结束事件丢失就永远常亮"的泄漏风险，故只作为计时器的启动条件。
 */
class PlayerControllerState {

    private val alwaysOnRequests = mutableStateListOf<Any>()

    private val userVisible = mutableStateOf(true)

    private val autoHideFired = mutableStateOf(false)

    /** 令牌集合非空；读它会在令牌增减时触发重组。 */
    val alwaysOnActive: State<Boolean> = derivedStateOf { alwaysOnRequests.isNotEmpty() }

    /** [alwaysOnActive] 的非 State 读法，供事件回调（非组合上下文）判断。 */
    val hasAlwaysOnRequest: Boolean get() = alwaysOnRequests.isNotEmpty()

    /** 控件此刻该不该显示。锁定态与外部总开关由调用方再合并一次。 */
    val controlsVisible: State<Boolean> = derivedStateOf {
        (userVisible.value || alwaysOnRequests.isNotEmpty()) && !autoHideFired.value
    }

    val isControlsVisible: Boolean get() = controlsVisible.value

    fun requestAlwaysOn(key: Any) {
        if (!alwaysOnRequests.contains(key)) alwaysOnRequests.add(key)
        autoHideFired.value = false
    }

    fun cancelAlwaysOn(key: Any) {
        alwaysOnRequests.remove(key)
    }

    fun toggleByTap() {
        userVisible.value = !isControlsVisible
        autoHideFired.value = false
    }

    fun showByUserInteraction() {
        userVisible.value = true
        autoHideFired.value = false
    }

    fun hideByUserInteraction() {
        userVisible.value = false
        autoHideFired.value = false
    }

    fun onAutoHideElapsed() {
        autoHideFired.value = true
    }

    /**
     * 现在能否起倒计时表。
     *
     * `autoHideFired` 参与判定：到点隐藏后本函数若不看它，条件仍为真，
     * 就会陷入"隐藏 → 重新计时 → 再隐藏"的循环。
     */
    fun canStartAutoHideTimer(transientActive: Boolean): Boolean =
        isControlsVisible && !hasAlwaysOnRequest && !transientActive && !autoHideFired.value
}