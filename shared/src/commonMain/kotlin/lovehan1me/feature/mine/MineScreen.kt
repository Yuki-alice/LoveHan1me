@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package lovehan1me.feature.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.check_in_action
import lovehan1me.check_in_done
import lovehan1me.check_in_feature_name
import lovehan1me.check_in_never
import lovehan1me.check_in_streak
import lovehan1me.check_in_today_count
import lovehan1me.h_chan_default_avatar
import lovehan1me.ic_calendar_month
import lovehan1me.ic_settings
import lovehan1me.ic_switch
import lovehan1me.loading
import lovehan1me.login
import lovehan1me.login_or_register
import lovehan1me.login_sync_hint
import lovehan1me.my_content
import lovehan1me.refresh_page_or_login_expired
import lovehan1me.settings
import lovehan1me.switch_site
import lovehan1me.ui.component.CardContainerSurface
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.SettingNavigationItem
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.theme.HanimeDefaults
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 「我的」页的一行内容入口。
 *
 * 刻意**不**持有路由类型：本文件属于 `feature/`，不该反向依赖 `app/navigation/`。
 * 由路由层（`MineRouteScreen`）把 [lovehan1me.app.navigation.main.MineSection] 的
 * 图标 / 标题 / 点击闭包映射成本类型后传入。
 *
 * @property enabled 仅用于置灰（未登录时的「订阅」）。点击**仍会回调** —— 由调用方
 *   决定是「提示登录并跳登录页」还是正常放行。
 */
data class MineEntry(
    val iconRes: DrawableResource,
    val titleRes: StringResource,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * 「我的」一级页面的纯展示实现（P3）。
 *
 * 结构（自上而下）与设计稿 §1.2 一致：
 * 1. **签到首卡** —— 可关闭（`check_in_visible`），位置在登录卡之上；
 * 2. **登录账户卡** —— 未登录显示「登录 / 注册」+ 登录按钮；已登录显示头像与用户名；
 * 3. **「我的内容」分区标题** + 6 个 [MineEntry] 行（收藏 / 稍后看 / 播放列表 /
 *    订阅 / 观看历史 / 下载）。
 *
 * 顶栏右侧是**设置齿轮** —— 设置退役一级导航后，小屏从此处进入（大屏还可走
 * NavigationRail 底部）。
 *
 * 视觉一律复用项目既有组件（[HanimeScaffold] / [SettingNavigationItem] /
 * [CardContainerSurface]），不照搬原型色值。
 */
@Composable
fun MineScreen(
    title: String,
    isLoggedIn: Boolean,
    accountLoading: Boolean,
    avatarUrl: String?,
    username: String?,
    checkInVisible: Boolean,
    checkInDoneToday: Boolean,
    checkInStreakDays: Int,
    checkInTodayCount: Int,
    entries: List<MineEntry>,
    /** 当前数据源站点名（host，如 `hanime1.me` / `javchu.com`），显示在账号卡副标题。 */
    currentSiteName: String,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onCheckIn: () -> Unit,
    /** 账号卡右侧「切换站点」：一键切到另一个数据源（走 `SiteSwitcher.toggle`）。 */
    onSwitchSite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HanimeScaffold(
        topBarWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        title = title,
        onBack = null,
        modifier = modifier,
        actions = {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = onOpenSettings,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_settings),
                    contentDescription = stringResource(Res.string.settings),
                )
            }
        },
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (checkInVisible) {
                item {
                    CheckInCard(
                        doneToday = checkInDoneToday,
                        streakDays = checkInStreakDays,
                        todayCount = checkInTodayCount,
                        onOpen = onOpenCheckIn,
                        onCheckIn = onCheckIn,
                    )
                }
            }
            item {
                AccountCard(
                    isLoggedIn = isLoggedIn,
                    loading = accountLoading,
                    avatarUrl = avatarUrl,
                    username = username,
                    currentSiteName = currentSiteName,
                    onOpenAccount = onOpenAccount,
                    onOpenLogin = onOpenLogin,
                    onSwitchSite = onSwitchSite,
                )
            }
            item {
                Text(
                    text = stringResource(Res.string.my_content),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 8.dp),
                )
            }
            items(count = entries.size) { index ->
                val entry = entries[index]
                SettingNavigationItem(
                    title = stringResource(entry.titleRes),
                    iconRes = entry.iconRes,
                    enabled = entry.enabled,
                    shapes = HanimeDefaults.cardShapes(),
                    onClick = entry.onClick,
                )
            }
        }
    }
}

/**
 * 签到首卡。
 *
 * 卡片本体点击 → 进签到日历页（`DailyCheckInRoute`）；右侧按钮 → 就地记录一次
 * （走 `CheckInCalendarViewModel.addRecord`）。
 *
 * ⚠️ **不依赖登录态**：本项目的签到是纯本地功能（Room 本地库 + `CheckInCalendarViewModel`），
 * 与站点账号无关，因此未登录时按钮照常可用（详见 `MineRouteScreen` 的说明）。
 */
@Composable
private fun CheckInCard(
    doneToday: Boolean,
    streakDays: Int,
    todayCount: Int,
    onOpen: () -> Unit,
    onCheckIn: () -> Unit,
) {
    CardContainerSurface(
        onClick = onOpen,
        shapes = HanimeDefaults.cardShapes(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 去圆底：纯图标，点缀色收敛到 tint（与首页顶栏新番入口同理）。
            Icon(
                painter = painterResource(Res.drawable.ic_calendar_month),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.check_in_feature_name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = checkInSummary(streakDays, todayCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onCheckIn) {
                Text(
                    stringResource(
                        if (doneToday) Res.string.check_in_done else Res.string.check_in_action
                    )
                )
            }
        }
    }
}

/**
 * 登录账户卡。
 *
 * 已登录 → 点整卡进账号页（账号页里有登出）；未登录 → 点整卡或按钮都进登录页。
 *
 * **与登录态无关的两个元素**（对齐上游 `MainDrawerHeader.kt:142-183`）：
 * - 副标题恒显示当前数据源站点名，未登录时其下再补一行 [Res.string.login_sync_hint]；
 * - [SwitchSiteButton] 恒显示。
 *
 * ⚠️ 曾误按「仅已登录才给切换按钮」实现 —— 上游那处 `when { isLoading / isLoggedIn /
 * else }` **只管标题文字**，`currentSite` 与切换按钮都在条件分支之外，未登录照常可见。
 * 未登录时切站会触发重新登录，但这正是上游行为，不该由我们收紧。
 */
@Composable
private fun AccountCard(
    isLoggedIn: Boolean,
    loading: Boolean,
    avatarUrl: String?,
    username: String?,
    currentSiteName: String,
    onOpenAccount: () -> Unit,
    onOpenLogin: () -> Unit,
    onSwitchSite: () -> Unit,
) {
    val title = when {
        loading -> stringResource(Res.string.loading)
        isLoggedIn -> username ?: stringResource(Res.string.refresh_page_or_login_expired)
        else -> stringResource(Res.string.login_or_register)
    }
    CardContainerSurface(
        onClick = { if (isLoggedIn) onOpenAccount() else onOpenLogin() },
        shapes = HanimeDefaults.cardShapes(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HanimeAsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(Res.drawable.h_chan_default_avatar),
                fallback = painterResource(Res.drawable.h_chan_default_avatar),
                error = painterResource(Res.drawable.h_chan_default_avatar),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 副标题恒显示站点名，未登录时在其下再补一行登录引导 ——
                // 对齐上游 `MainDrawerHeader.kt:142-148`：`currentSite` **不在**
                // 登录态条件分支里，未登录也照常显示当前数据源。
                Text(
                    text = currentSiteName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isLoggedIn) {
                    Text(
                        text = stringResource(Res.string.login_sync_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 「切换站点」**无条件渲染**，与登录态无关 —— 对齐上游
            // `MainDrawerHeader.kt:150-183`：那里的切换按钮在任何登录态下都可见
            // （上游那处 `when { isLoading / isLoggedIn / else }` 只管标题文字，
            // 站点名与切换按钮都在条件分支之外）。
            //
            // 未登录显示「登录」按钮 + 切换按钮并存：切站确实会触发重新登录，
            // 但这正是用户预期 —— 上游同样允许未登录时切站。
            SwitchSiteButton(onClick = onSwitchSite)
            if (!isLoggedIn) {
                Button(onClick = onOpenLogin) {
                    Text(stringResource(Res.string.login))
                }
            }
        }
    }
}

/**
 * 账号卡右侧的「切换站点」按钮。
 *
 * 用 [IconButton]（项目既有组件）而非 `OutlinedButton`：图标 + 文案竖向堆叠，
 * 在 52dp 头像行高内不抢宽度，也不会像文本按钮那样把用户名挤成省略号。
 * 样式一律走 token（`MaterialTheme.colorScheme` / `typography`），不写死色值。
 */
@Composable
private fun SwitchSiteButton(onClick: () -> Unit) {
    IconButton(
        shapes = IconButtonDefaults.shapes(),
        onClick = onClick,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_switch),
                contentDescription = stringResource(Res.string.switch_site),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(Res.string.switch_site),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** 签到卡副标题：优先「连续 N 天」，再补「今日已记录 M 次」，都没有则给空态文案。 */
@Composable
private fun checkInSummary(streakDays: Int, todayCount: Int): String {
    val streak = if (streakDays > 0) stringResource(Res.string.check_in_streak, streakDays) else null
    val today = if (todayCount > 0) stringResource(Res.string.check_in_today_count, todayCount) else null
    return when {
        streak != null && today != null -> "$streak · $today"
        streak != null -> streak
        today != null -> today
        else -> stringResource(Res.string.check_in_never)
    }
}
