package lovehan1me.app.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.Res
import lovehan1me.app.sharedViewModel
import lovehan1me.core.domain.state.PageState
import lovehan1me.data.SettingsRepository
import lovehan1me.data.database.entity.CheckInType
import lovehan1me.feature.home.CheckInCalendarViewModel
import lovehan1me.feature.home.dailycheckin.formatHm
import lovehan1me.feature.home.dailycheckin.plusDays
import lovehan1me.feature.home.dailycheckin.today
import lovehan1me.feature.home.homepage.HomePageViewModel
import lovehan1me.feature.mine.MineEntry
import lovehan1me.feature.mine.MineScreen
import lovehan1me.my_account
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * 「我的」一级页面的路由装配（P3）。
 *
 * 职责边界：**取状态 + 算派生量 + 把 6 个入口映射成 UI 行**，视图本体在
 * [lovehan1me.feature.mine.MineScreen]（纯展示，不碰仓储）。
 *
 * ---
 *
 * ### 关于「签到卡是否依赖登录」—— 与设计稿的一处有意偏离
 *
 * 设计稿（§1.2 / §11 决策 1）写的是：未登录时签到按钮降级为「登录后签到」并跳登录页。
 * 那条推理来自**原站签到是服务端功能、前提是登录**。
 *
 * 但本项目落地后并不是这样：签到是完全**本地**的功能 —— Room 本地库
 * （`CheckInRecordEntity` / `CheckInDao`）+ [CheckInCalendarViewModel]，记录存在设备上，
 * 与 `SettingsRepository.loginStateFlow` 没有任何耦合。
 *
 * 因此这里**不按登录态降级**：未登录用户照常可用签到（否则等于凭空给一个本地功能
 * 加一道无意义的登录墙，登录也不会多出任何能力）。若后续把签到接回服务端，请在
 * 此处补回降级逻辑。
 *
 * ### 签到副标题的「连续天数」怎么算
 *
 * 后端没有 streak 字段，从 `uiState.records`（`Map<LocalDate, Int>`）现算：当天已记录
 * 则从今天回溯，否则从昨天回溯，直到遇到没记录的那天。放在 `remember(records)` 里，
 * 只在记录变化时重算。
 */
@Composable
fun MineRouteScreen(
    homeViewModel: HomePageViewModel,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenLogin: () -> Unit,
    /** 未登录时点击「订阅」这类需要登录的入口：提示登录 + 跳登录页。 */
    onLockedSection: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onNavigateToSection: (MineSection) -> Unit,
) {
    val isLoggedIn by SettingsRepository.loginStateFlow.collectAsStateWithLifecycle()
    val checkInEnabled by SettingsRepository.checkInEnabledFlow.collectAsStateWithLifecycle()

    // 与 SharedHomeRouteScreen 同构：开关关掉时不建 VM，避免白起一个 Room 查询。
    val checkInViewModel: CheckInCalendarViewModel? =
        if (checkInEnabled) sharedViewModel(::CheckInCalendarViewModel) else null
    val checkInUiState = if (checkInViewModel != null) {
        checkInViewModel.uiState.collectAsStateWithLifecycle().value
    } else {
        null
    }

    // 头像 / 用户名来自首页 VM（与旧抽屉头同源）；未登录时不透传，避免残留上次会话的头像。
    val homeState by homeViewModel.homePageFlow.collectAsStateWithLifecycle()
    val pageInfo = (homeState as? PageState.Success)?.info?.page
    val avatarUrl = if (isLoggedIn) pageInfo?.avatarUrl else null
    val username = if (isLoggedIn) pageInfo?.username else null
    val accountLoading = isLoggedIn && homeState is PageState.Loading

    val todayDate: LocalDate = remember { today() }
    val records: Map<LocalDate, Int> = checkInUiState?.records ?: emptyMap()
    val checkInDoneToday = (records[todayDate] ?: 0) > 0
    val checkInStreakDays = remember(records, todayDate) {
        var cursor = if ((records[todayDate] ?: 0) > 0) todayDate else todayDate.plusDays(-1)
        var streak = 0
        while ((records[cursor] ?: 0) > 0) {
            streak++
            cursor = cursor.plusDays(-1)
        }
        streak
    }

    MineScreen(
        title = stringResource(Res.string.my_account),
        isLoggedIn = isLoggedIn,
        accountLoading = accountLoading,
        avatarUrl = avatarUrl,
        username = username,
        checkInVisible = checkInEnabled,
        checkInDoneToday = checkInDoneToday,
        checkInStreakDays = checkInStreakDays,
        checkInTodayCount = records[todayDate] ?: 0,
        entries = MineSection.entries.map { section ->
            MineEntry(
                iconRes = section.iconRes,
                titleRes = section.titleRes,
                // 置灰只是提示；点击仍回调，由下面分流到「提示登录」或正常导航。
                enabled = !section.requiresLogin || isLoggedIn,
                onClick = {
                    if (section.requiresLogin && !isLoggedIn) onLockedSection()
                    else onNavigateToSection(section)
                },
            )
        },
        onOpenSettings = onOpenSettings,
        onOpenAccount = onOpenAccount,
        onOpenLogin = onOpenLogin,
        onOpenCheckIn = onOpenCheckIn,
        onCheckIn = {
            checkInViewModel?.addRecord(
                date = todayDate,
                time = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .time
                    .formatHm(),
                type = CheckInType.MASTURBATION.storeName,
                feeling = "",
            )
        },
    )
}
