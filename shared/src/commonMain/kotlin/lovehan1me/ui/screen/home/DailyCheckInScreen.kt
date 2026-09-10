package lovehan1me.ui.screen.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.Res
import lovehan1me.suck_back_done
import lovehan1me.suck_back_title
import lovehan1me.suck_back_message
import lovehan1me.suck_back_dismiss
import lovehan1me.suck_back_confirm
import lovehan1me.forgot_title
import lovehan1me.forgot_message
import lovehan1me.forgot_dismiss
import lovehan1me.forgot_confirm
import lovehan1me.checkin_report
import lovehan1me.check_in_feature_name
import lovehan1me.cancel
import lovehan1me.calendar_dialog_title
import lovehan1me.calendar_dialog_message
import lovehan1me.calendar_dialog_confirm
import lovehan1me.ic_event_note
import lovehan1me.no_calendar_app
import lovehan1me.utils.SonnerToast
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.ui.screen.home.dailycheckin.CheckInDialog
import lovehan1me.ui.screen.home.dailycheckin.ContributionReportDialog
import lovehan1me.ui.screen.home.dailycheckin.DailyCheckInContent
import lovehan1me.ui.screen.home.dailycheckin.DailyCheckInEvent
import lovehan1me.ui.screen.home.dailycheckin.DailyCheckInUiState
import lovehan1me.ui.screen.home.dailycheckin.addToSystemCalendar
import lovehan1me.ui.screen.home.dailycheckin.formatMd
import lovehan1me.ui.screen.home.dailycheckin.monthsBetween
import lovehan1me.ui.screen.home.dailycheckin.today
import lovehan1me.ui.viewmodel.CheckInCalendarViewModel
import lovehan1me.ui.viewmodel.sharedViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate
import lovehan1me.ui.screen.home.dailycheckin.YearMonth
import kotlin.time.Duration.Companion.milliseconds

/**
 * 打卡日历页面 Screen 层。
 *
 * 作为 V-S-C 架构的胶水层：订阅 ViewModel 状态生成 [DailyCheckInUiState]，
 * 将 [DailyCheckInEvent] 映射到 ViewModel 操作和导航。
 *
 * M2：自 `:app` 下沉。去掉 `activity: Activity` 参数及全屏 window-mode 特效
 *（`updateReportWindowMode`，Android-only；桌面弹窗本就独立窗口）；
 * "添加到系统日历"改跨平台 [addToSystemCalendar]。
 *
 * @param onBack 返回回调
 * @param viewModel 打卡日历 ViewModel
 */
@Composable
fun DailyCheckInScreen(
    onBack: () -> Unit,
    viewModel: CheckInCalendarViewModel = sharedViewModel(::CheckInCalendarViewModel),
) {
    var showReport by rememberSaveable { mutableStateOf(false) }
    var isReportFullscreen by rememberSaveable { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val yearRecords by viewModel.yearRecords.collectAsStateWithLifecycle()
    val yearStats by viewModel.yearStats.collectAsStateWithLifecycle()

    val today = remember { today() }

    var forgotDialogDate by remember { mutableStateOf<LocalDate?>(null) }
    var suckBackDialogDate by remember { mutableStateOf<LocalDate?>(null) }
    var calendarDialogDate by remember { mutableStateOf<LocalDate?>(null) }
    var checkInDialogDate by remember { mutableStateOf<LocalDate?>(null) }
    var showEasterEgg by remember { mutableStateOf("") }
    var eggVisible by remember { mutableStateOf(false) }

    var reportSelectedYear by remember { mutableIntStateOf(today.year) }
    var reportViewMode by remember { mutableStateOf("year") }
    var reportSelectedMonth by remember { mutableIntStateOf(today.monthNumber) }

    val anchorMonth = remember { YearMonth.now() }
    val initialPage = Int.MAX_VALUE / 2
    val pagerState = rememberPagerState(initialPage = initialPage) { Int.MAX_VALUE }

    LaunchedEffect(uiState.currentMonth) {
        val monthsDiff = monthsBetween(anchorMonth, uiState.currentMonth)
        val targetPage = initialPage + monthsDiff
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val pageMonth = anchorMonth.plusMonths((page - initialPage).toLong())
                if (pageMonth != uiState.currentMonth) {
                    if (pageMonth > uiState.currentMonth) viewModel.nextMonth()
                    else viewModel.previousMonth()
                }
            }
    }

    LaunchedEffect(showEasterEgg) {
        if (showEasterEgg.isNotEmpty()) {
            eggVisible = true
            kotlinx.coroutines.delay(1500.milliseconds)
            eggVisible = false
        }
    }

    val scope = rememberCoroutineScope()
    // P6d-3-C3：回调内固定串预解析
    val suckBackDoneText = stringResource(Res.string.suck_back_done)

    val handleEvent: (DailyCheckInEvent) -> Unit = { event ->
        when (event) {
            is DailyCheckInEvent.OnDateClick -> {
                when {
                    event.date > today -> {
                        calendarDialogDate = event.date
                    }

                    event.date < today && (uiState.records[event.date] ?: 0) == 0 -> {
                        forgotDialogDate = event.date
                    }

                    else -> {
                        checkInDialogDate = event.date
                    }
                }
            }

            is DailyCheckInEvent.OnDateLongClick -> {
                val count = uiState.records[event.date] ?: 0
                if (count > 0 && event.date < today) {
                    suckBackDialogDate = event.date
                } else if (count > 0) {
                    viewModel.clearCheckIn(event.date)
                }
            }

            DailyCheckInEvent.OnPreviousMonth -> viewModel.previousMonth()
            DailyCheckInEvent.OnNextMonth -> viewModel.nextMonth()
            DailyCheckInEvent.OnTodayCheckIn -> {
                checkInDialogDate = today
            }

            DailyCheckInEvent.OnTodayClear -> viewModel.clearCheckIn(today)
            DailyCheckInEvent.OnShowReport -> {
                showReport = true
            }
        }
    }

    val scrollBehavior = pinnedScrollBehavior(rememberTopAppBarState())
    HanimeScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        title = stringResource(Res.string.check_in_feature_name),
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = {
            TextButton(
                onClick = { showReport = true }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_event_note),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.checkin_report))
            }
        },
    ) { innerPadding ->
        DailyCheckInContent(
            paddingValues = innerPadding,
            uiState = uiState,
            onEvent = handleEvent,
            showEasterEgg = showEasterEgg,
            eggVisible = eggVisible,
            pagerState = pagerState,
            anchorMonth = anchorMonth,
            initialPage = initialPage,
        )
    }

    ConfirmDialog(
        visible = forgotDialogDate != null,
        title = stringResource(Res.string.forgot_title),
        message = forgotDialogDate?.let {
            stringResource(Res.string.forgot_message,
                it.formatMd()
            )
        } ?: "",
        confirmText = stringResource(Res.string.forgot_confirm),
        dismissText = stringResource(Res.string.forgot_dismiss),
        onConfirm = {
            forgotDialogDate?.let { checkInDialogDate = it }
            forgotDialogDate = null
        },
        onDismiss = { forgotDialogDate = null },
    )

    ConfirmDialog(
        visible = calendarDialogDate != null,
        title = stringResource(Res.string.calendar_dialog_title),
        message = calendarDialogDate?.let {
            stringResource(Res.string.calendar_dialog_message,
                it.formatMd()
            )
        } ?: "",
        confirmText = stringResource(Res.string.calendar_dialog_confirm),
        dismissText = stringResource(Res.string.cancel),
        onConfirm = {
            calendarDialogDate?.let { date ->
                scope.launch {
                    if (!addToSystemCalendar(date)) {
                        SonnerToast.warning(getString(Res.string.no_calendar_app))
                    }
                }
            }
            calendarDialogDate = null
        },
        onDismiss = { calendarDialogDate = null },
    )

    ConfirmDialog(
        visible = suckBackDialogDate != null,
        title = stringResource(Res.string.suck_back_title),
        message = suckBackDialogDate?.let {
            stringResource(Res.string.suck_back_message,
                it.formatMd(),
                uiState.records[it] ?: 0
            )
        } ?: "",
        confirmText = stringResource(Res.string.suck_back_confirm),
        dismissText = stringResource(Res.string.suck_back_dismiss),
        onConfirm = {
            suckBackDialogDate?.let {
                viewModel.clearCheckIn(it)
                SonnerToast.success(suckBackDoneText)
            }
            suckBackDialogDate = null
        },
        onDismiss = { suckBackDialogDate = null },
    )

    checkInDialogDate?.let { date ->
        CheckInDialog(
            date = date,
            onLoadRecords = { d, cb -> viewModel.getRecordsByDate(d, cb) },
            onGetCountByDate = { d, cb -> viewModel.getCountByDate(d, cb) },
            onAddRecord = { d, time, type, feeling ->
                viewModel.addRecord(d, time, type, feeling)
            },
            onDeleteRecord = { record, onDone -> viewModel.deleteRecord(record, onDone) },
            onEasterEgg = { msg -> showEasterEgg = msg },
            onDismiss = { checkInDialogDate = null },
        )
    }

    if (showReport) {
        ContributionReportDialog(
            selectedYear = reportSelectedYear,
            viewMode = reportViewMode,
            selectedMonth = reportSelectedMonth,
            yearRecords = yearRecords,
            yearStats = yearStats,
            onYearChange = { reportSelectedYear = it },
            onViewModeChange = { reportViewMode = it },
            onMonthChange = { reportSelectedMonth = it },
            onDismiss = {
                showReport = false
                isReportFullscreen = false
            },
            isFullscreen = isReportFullscreen,
            onToggleFullscreen = { isReportFullscreen = !isReportFullscreen },
            onLoadYearRecords = { viewModel.loadYearRecords(it) },
        )
    }
}
