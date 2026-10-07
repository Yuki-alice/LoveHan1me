package lovehan1me.app.navigation.main

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 自研回退栈语义守卫（纯状态机，离线可跑）。
 *
 * 钉的是 KDoc 与历史 bug 承认过的四条语义：切 tab 弹回根、`launchSingleTop`
 * 去重、`replaceTop` 退化、popTo 切片。98 行零测试、有历史 bug（界面钉死在
 * 搜索页且不可逆），属于 quality-audit 建议配额里的 4 个。
 */
class TopLevelBackStackTest {

    @Test
    fun `push_pop_回到根`() {
        val stack = TopLevelBackStack("home")
        assertEquals("home", stack.currentKey)

        stack.add("detail")
        assertEquals("detail", stack.currentKey)

        assertTrue(stack.removeLast(), "非根栈 removeLast 应返回 true")
        assertEquals("home", stack.currentKey)
        assertFalse(stack.removeLast(), "根栈 removeLast 应返回 false")
    }

    @Test
    fun `切tab弹回该tab根_重复点当前tab回到根`() {
        val stack = TopLevelBackStack("home")
        stack.add("search")
        assertEquals("search", stack.currentKey)

        // 切到发现 tab 再切回来：残留的二级路由必须被弹掉。
        stack.addTopLevel("discover")
        assertEquals("discover", stack.currentKey)
        stack.addTopLevel("home")
        assertEquals("home", stack.currentKey, "切 tab 必须弹回该 tab 根，不能残留二级路由")

        // 重复点当前 tab 同样回到根。
        stack.add("detail")
        stack.addTopLevel("home")
        assertEquals("home", stack.currentKey)
    }

    @Test
    fun `launchSingleTop去重_普通add照压`() {
        val stack = TopLevelBackStack("home")
        stack.add("detail", launchSingleTop = true)
        stack.add("detail", launchSingleTop = true)
        assertEquals(listOf("home", "detail"), stack.backStack.toList(), "同键 singleTop 不应重复压栈")

        stack.add("detail")
        assertEquals(listOf("home", "detail", "detail"), stack.backStack.toList(), "普通 add 应照压")
    }

    @Test
    fun `replaceTop多层替换_只剩根时退化为压栈`() {
        val stack = TopLevelBackStack("settings")
        // 栈里只剩根：退化为压栈（从设置首页进第一个分类，返回要回到列表）。
        stack.replaceTop("category")
        assertEquals("category", stack.currentKey)
        assertTrue(stack.removeLast())
        assertEquals("settings", stack.currentKey)

        // 多层时：替换栈顶而不是再压一层。
        stack.add("a")
        stack.add("b")
        stack.replaceTop("c")
        assertEquals("c", stack.currentKey)
        assertEquals(listOf("settings", "a", "c"), stack.backStack.toList(), "replaceTop 不应增加栈深")
    }

    @Test
    fun `popTo切片到目标_含与不含目标`() {
        val stack = TopLevelBackStack("home")
        stack.add("a")
        stack.add("b")

        assertTrue(stack.popTo("a", inclusive = false))
        assertEquals("a", stack.currentKey)

        stack.add("b")
        assertTrue(stack.popTo("a", inclusive = true))
        assertEquals("home", stack.currentKey, "inclusive 应连目标一起弹掉")

        assertFalse(stack.popTo("missing"), "不存在的键应返回 false")
    }

    /**
     * 不变式（固定种子 fuzz）。
     *
     * 这一层是导航内核，两类回归会让**面向用户的界面直接崩或钉死**：
     *  1. `backStack` 变空 —— `currentKey = backStack.last()` 抛 `NoSuchElementException`，
     *     同时 NavDisplay 拿到一个非法栈；
     *  2. `currentKey` 不再等于 `topLevelKey` 那个栈的栈顶 —— 界面停在不该显示的页面
     *     （历史上"切 tab 后钉死在搜索页且不可逆"就是这一类）。
     *
     * 手写用例只走得到单条路径，交叉路径才是历史 bug 的来源；这里用固定种子的伪随机
     * 操作序列覆盖五种操作的组合，每步都断言不变式。种子固定 → 可复现、不 flaky。
     */
    @Test
    fun `随机操作序列下不变式恒成立`() {
        val keys = listOf("home", "discover", "mine", "detail", "settings", "category")
        val stack = TopLevelBackStack("home")
        var seed = 20261007L

        fun nextInt(bound: Int): Int {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33).toInt() and Int.MAX_VALUE) % bound
        }

        repeat(20_000) { step ->
            val where = "step=$step"
            when (nextInt(5)) {
                0 -> {
                    val key = keys[nextInt(keys.size)]
                    stack.add(key, launchSingleTop = nextInt(2) == 0)
                    assertEquals(key, stack.currentKey, "$where: add 后栈顶必须是该键")
                }

                1 -> {
                    val key = keys[nextInt(keys.size)]
                    stack.addTopLevel(key)
                    assertEquals(key, stack.currentKey, "$where: 切 tab 后必须落在该 tab 根")
                }

                2 -> {
                    val key = keys[nextInt(keys.size)]
                    stack.replaceTop(key)
                    assertEquals(key, stack.currentKey, "$where: replaceTop 后栈顶必须是该键")
                }

                3 -> stack.removeLast()

                else -> stack.popTo(keys[nextInt(keys.size)], inclusive = nextInt(2) == 0)
            }

            assertTrue(
                stack.backStack.isNotEmpty(),
                "$where: backStack 绝不能为空（currentKey 取 last()，NavDisplay 也要求非空）",
            )
            assertEquals(
                stack.currentKey,
                stack.backStack.last(),
                "$where: currentKey 必须是 backStack 末尾",
            )
        }
    }
}
