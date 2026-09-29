package lovehan1me.ui.refresh

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.key.Key

/**
 * 「刷新当前页面」的全局入口到具体页面的中转站。
 *
 * 桌面端没有下拉手势，刷新改由快捷键（F5 / Ctrl+R / Cmd+R）承担。难点是**谁**来刷新：
 * 页面自己监听不到按键（它们拿不到窗口级事件），而全局入口又不知道"当前页"是谁的刷新动作长什么样。
 * 解法是反向登记 —— 可刷新页面在组合时把自己的 `onRefresh` 登记进来，全局入口只认最后登记的那一个。
 *
 * 用**栈**而不是单个值：导航转场期间相邻两条路由会同时处于组合中，后组合者即栈顶。
 * 若只存一个值，栈顶页面退出时会把这唯一的登记清空，而下一个页面仍在屏上却已无人可刷新。
 *
 * 只在主线程访问（组合与窗口按键回调都在主线程），不加锁。
 */
class PageRefreshHub {
    /**
     * 登记项。用独立对象而不是直接存 lambda：同一个 lambda 实例被两处页面复用时要能分别注销，
     * 按值 `remove` 会摘错那一个。
     */
    internal class Registration(val action: () -> Unit)

    private val registrations = mutableListOf<Registration>()

    /** 登记一个刷新动作，返回值传回 [unregister] 注销。 */
    internal fun register(action: () -> Unit): Registration =
        Registration(action).also(registrations::add)

    internal fun unregister(registration: Registration) {
        registrations.remove(registration)
    }

    /** 当前页面的刷新动作；无人登记时为 null。 */
    val currentAction: (() -> Unit)? get() = registrations.lastOrNull()?.action

    /** 派发刷新。返回是否真的派发了（无人登记时返回 false，调用方据此决定要不要消费按键）。 */
    fun refresh(): Boolean {
        val action = currentAction ?: return false
        action()
        return true
    }
}

/**
 * 当前生效的中转站。由 `App` 提供；桌面入口拿的是同一个实例，才能在窗口级按键回调里派发。
 * 默认值兜住预览 / 单页测试里没有 App 的场景。
 */
val LocalPageRefreshHub = compositionLocalOf { PageRefreshHub() }

/**
 * 是否是「刷新当前页面」的快捷键。
 *
 * F5 是 Windows / Linux 的习惯，Cmd+R（macOS）、Ctrl+R（Windows / Linux）是三端一致的习惯，
 * 两种都收。Ctrl 与 Meta 不按平台区分：判定只看用户实际按下了什么，
 * 按平台过滤只会让"我明明按了"变成不响应。
 */
fun isPageRefreshShortcut(
    key: Key,
    isCtrlPressed: Boolean,
    isMetaPressed: Boolean,
): Boolean = key == Key.F5 || (key == Key.R && (isCtrlPressed || isMetaPressed))
