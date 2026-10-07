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
}
