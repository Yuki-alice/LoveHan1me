package lovehan1me.app.navigation.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

class TopLevelBackStack<T : Any>(startKey: T) {
    private val topLevelStacks = linkedMapOf<T, SnapshotStateList<T>>(
        startKey to mutableStateListOf(startKey),
    )

    var topLevelKey: T by mutableStateOf(startKey)
        private set

    val backStack = mutableStateListOf(startKey)

    val currentKey: T
        get() = backStack.last()

    private fun updateBackStack() {
        backStack.clear()
        backStack.addAll(topLevelStacks.values.flatten())
    }

    /**
     * 切入一级 tab，并把该 tab 的栈**弹回根键**（对齐底部导航标准语义）。
     *
     * 原来只把目标栈整体搬上来、不弹栈，导致：只要目标栈顶残留过 L2 路由
     * （典型如首页搜索框 / 分类「更多」向 Home 栈压入 [SearchRoute]，而它渲染的
     * 正是发现页同款界面），切换 tab 后 `backStack.last()` 仍是那个残留路由，
     * 界面钉在搜索页且不可逆。这里切 tab 一律回到该 tab 根，重复点当前 tab
     * 也顺带回到根（栈若只有根键则为 no-op）。
     */
    fun addTopLevel(key: T) {
        val stack = topLevelStacks.remove(key)?.also {
            while (it.size > 1) it.removeAt(it.lastIndex)
        } ?: mutableStateListOf(key)
        topLevelStacks[key] = stack
        topLevelKey = key
        updateBackStack()
    }

    fun add(key: T, launchSingleTop: Boolean = false) {
        if (launchSingleTop && currentKey == key) return
        topLevelStacks.getValue(topLevelKey).add(key)
        updateBackStack()
    }

    /**
     * 用 [key] 替换当前栈顶（栈里只剩根键时退化为压栈）。
     *
     * 用途：设置页在**宽屏双栏下的左栏切换** —— 分类由路由表达（而不是宿主内部状态），
     * 这样"窄屏点进分类页 → 拉宽窗口"时，双栏能按栈顶路由立刻恢复选中态。
     *
     * 为什么不是 `add`：左栏点 N 次分类就压 N 层，返回时要逐层倒放（用户看到右栏在
     * 自己点过的分类间回放），语义错乱。为什么栈只剩根键时退化为压栈：那是"从设置
     * 首页进入第一个分类"，压栈才能让返回回到设置列表。
     */
    fun replaceTop(key: T) {
        val stack = topLevelStacks.getValue(topLevelKey)
        if (stack.size <= 1) stack.add(key) else stack[stack.lastIndex] = key
        updateBackStack()
    }

    fun removeLast(): Boolean {
        if (backStack.size <= 1) return false

        val currentStack = topLevelStacks.getValue(topLevelKey)
        currentStack.removeAt(currentStack.lastIndex)
        if (currentStack.isEmpty()) {
            topLevelStacks.remove(topLevelKey)
            topLevelKey = topLevelStacks.keys.last()
        }
        updateBackStack()
        return true
    }

    fun popTo(key: T, inclusive: Boolean = false): Boolean {
        val targetEntry = topLevelStacks.entries.lastOrNull { (_, stack) -> key in stack }
            ?: return false
        val targetIndex = targetEntry.value.indexOfLast { it == key }
        val targetSize = targetIndex + if (inclusive) 0 else 1
        if (targetSize < 1) return false

        val topLevelKeysToRemove = topLevelStacks.keys
            .dropWhile { it != targetEntry.key }
            .drop(1)
        topLevelKeysToRemove.forEach(topLevelStacks::remove)
        while (targetEntry.value.size > targetSize) {
            targetEntry.value.removeAt(targetEntry.value.lastIndex)
        }
        topLevelKey = targetEntry.key
        updateBackStack()
        return true
    }
}
