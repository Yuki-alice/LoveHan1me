package lovehan1me.app.navigation.main

import lovehan1me.Res
import lovehan1me.discover
import lovehan1me.home_page
import lovehan1me.ic_explore
import lovehan1me.ic_home
import lovehan1me.ic_person
import lovehan1me.my_account
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * 一级目的地 —— 由 9 项收敛为 **3 项：首页 / 发现 / 我的**。
 *
 * 替代旧的 `MainDrawerDestination`（9 项）。收敛依据是内容消费类应用只有三种意图：
 *
 * | 意图 | 用户心理 | 目的地 |
 * |---|---|---|
 * | 发现内容 | 「有什么好看的」 | [Home]（推荐流 / 更新 / 榜单） |
 * | 主动检索 | 「我要找某一部」 | [Discover]（搜索 + 浏览态） |
 * | 我的内容 | 「我看过 / 收藏的」 | [Mine]（收藏、历史、订阅、下载…） |
 *
 * 去向说明：
 *  - **设置**退出一级导航，改为独立全屏路由（「我的」顶栏齿轮 / Rail 底部进入）；
 *  - **每日签到**不做导航，落成「我的」页首卡；
 *  - 收藏 / 稍后看 / 播放列表 / 订阅 / 观看历史 / 下载 → 「我的」的 L2（`MineSection`）。
 *
 * ⚠️ [route] 即 [TopLevelBackStack] 的**顶层键**。首页直接复用既有的 [HomeRoute]，
 * 避免改动它作为初始键与 `popTo(HomeRoute)` 的全部调用点；发现 / 我的用各自的
 * 无参 object 键。
 *
 * @property route 顶层栈键（[TopLevelBackStack.addTopLevel] 的入参）
 */
enum class MainTab(
    val route: HanimeScreen,
    val iconRes: DrawableResource,
    val titleRes: StringResource,
) {
    Home(
        route = HomeRoute,
        iconRes = Res.drawable.ic_home,
        titleRes = Res.string.home_page,
    ),
    Discover(
        route = DiscoverTab,
        iconRes = Res.drawable.ic_explore,
        titleRes = Res.string.discover,
    ),
    Mine(
        route = MineTab,
        iconRes = Res.drawable.ic_person,
        titleRes = Res.string.my_account,
    ),
    ;

    companion object {
        /** 兜底目的地：栈意外为空 / 深链找不到归属时回首页。 */
        val Fallback: MainTab = Home

        fun fromRoute(route: HanimeScreen?): MainTab? =
            entries.firstOrNull { it.route == route }
    }
}

/**
 * 切换一级目的地。
 *
 * 与旧的 `navigateDrawerDestination` 的差异：**没有任何 tab 需要登录前置**。
 * 旧实现把「订阅」作为一级项并要求登录，「我的」页改版后订阅降为 L2、且未登录时
 * 「我的」本身仍要可用（签到卡 + 登录卡就是给未登录用户看的），因此登录拦截下沉到
 * 各 L2 入口自己处理（未登录时该行标灰 + 提示）。
 */
fun TopLevelBackStack<HanimeScreen>.navigateMainTab(tab: MainTab) {
    addTopLevel(tab.route)
}
