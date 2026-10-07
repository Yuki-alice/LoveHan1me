package lovehan1me.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.core.domain.model.AppLanguage
import lovehan1me.core.domain.model.ThemeMode
import lovehan1me.core.platform.applyAppLanguage
import lovehan1me.core.util.isDebugBuild
import lovehan1me.data.SettingsRepository
import lovehan1me.follow_system
import lovehan1me.ic_download
import lovehan1me.ic_launcher_h_chan_monochrome
import lovehan1me.ic_play_circle
import lovehan1me.ic_refresh
import lovehan1me.onboarding_feature_offline
import lovehan1me.onboarding_feature_player
import lovehan1me.onboarding_feature_sync
import lovehan1me.onboarding_finish
import lovehan1me.onboarding_lang_english
import lovehan1me.onboarding_lang_simplified
import lovehan1me.onboarding_lang_traditional
import lovehan1me.onboarding_next
import lovehan1me.onboarding_settings_language
import lovehan1me.onboarding_settings_subtitle
import lovehan1me.onboarding_settings_theme
import lovehan1me.onboarding_settings_title
import lovehan1me.onboarding_start
import lovehan1me.onboarding_welcome_subtitle
import lovehan1me.onboarding_welcome_title
import lovehan1me.theme_mode_auto
import lovehan1me.theme_mode_dark
import lovehan1me.theme_mode_light
import lovehan1me.ui.component.CardContainerSurface
import lovehan1me.ui.component.FilledTonalButton
import lovehan1me.ui.component.HapticButton
import lovehan1me.ui.component.SettingChoiceItem
import lovehan1me.ui.component.SettingsSegmentedGroup
import lovehan1me.ui.component.appbar.HanimeTopAppBar
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.contentFade
import lovehan1me.usage_notice_accept_countdown
import lovehan1me.usage_notice_content
import lovehan1me.usage_notice_decline
import lovehan1me.usage_notice_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

/**
 * 首次启动向导：欢迎 → 使用须知 → 基础设置（语言 + 主题）。
 *
 * 替代此前的"三段门控"（使用须知对话框 → 来源确认问卷 → 非法来源警告）：
 * 来源确认整套删除——问用户从哪下载的、答错再罚抄仓库链接，
 * 本质是安全话剧，还把正常用户挡在门外。
 *
 * 布局语言参照 animeko 的 onboarding（`ui-onboarding`）：**顶栏承载标题与返回**，
 * 正文限制在可读行宽内水平居中、短内容时垂直居中，主操作是内容末尾的**全宽按钮**。
 * 与旧版的差别：返回键从底栏移到顶栏；三步之间用 [contentFade] 过渡；单色圆点
 * 换成带图标的卡片，选中项复用设置页的 tonal 卡片。
 *
 * 约定：
 * - 只有全新用户（`usageNoticeAccepted == false`）走这里，老用户直接进应用；
 * - 中途杀进程重进从欢迎页重来，不记中间态；
 * - 语言/主题选了即写即生效（与设置页同一套写入），"完成"只负责收尾放行；
 * - 须知页拒绝 = 退出应用（与旧行为一致）；保留 20s（debug 5s）阅读倒计时。
 */
private const val STEP_WELCOME = 0
private const val STEP_NOTICE = 1
private const val STEP_SETTINGS = 2
private const val STEP_COUNT = 3

@Composable
fun OnboardingWizard(
    onFinished: () -> Unit,
    onExit: () -> Unit,
) {
    var step by remember { mutableIntStateOf(STEP_WELCOME) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // 换步后回到顶部，避免把上一步的滚动位置带到下一步。
    LaunchedEffect(step) { scrollState.scrollTo(0) }

    Scaffold(
        containerColor = HanimeDefaults.Colors.pageSurface,
        topBar = {
            Column {
                HanimeTopAppBar(
                    title = stepTitle(step),
                    onBack = if (step > STEP_WELCOME) {
                        { step-- }
                    } else {
                        null
                    },
                )
                LinearProgressIndicator(
                    progress = { (step + 1) / STEP_COUNT.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 可滚动内容区：主操作常驻下方，长内容只在这两者之间滚动，
            // 按钮不会再被滚出可视区（小屏上设置/须知页曾需要先滚到底才点得到）。
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val verticalPadding = HanimeDefaults.Spacing.huge
                // 内容不足一屏时也要撑满可视区，才能靠 Arrangement.Center 垂直居中；
                // 超出时撑高、由外层滚动接管。
                val minContentHeight = (maxHeight - verticalPadding * 2).coerceAtLeast(0.dp)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = HanimeDefaults.Widths.formMax)
                            .heightIn(min = minContentHeight)
                            .padding(
                                horizontal = HanimeDefaults.Spacing.extraExtraLarge,
                                vertical = verticalPadding,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // transitionSpec 不是 @Composable 上下文，转场要在组合期先算好。
                        val stepTransition = contentFade()
                        AnimatedContent(
                            targetState = step,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = { stepTransition },
                            contentAlignment = Alignment.Center,
                            label = "onboarding-step",
                        ) { current ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                when (current) {
                                    STEP_WELCOME -> WelcomeStep()
                                    STEP_NOTICE -> NoticeStep()
                                    else -> SettingsStep()
                                }
                            }
                        }
                    }
                }
            }
            OnboardingActionBar(
                step = step,
                onNext = { step++ },
                onExit = onExit,
                onFinish = {
                    scope.launch {
                        SettingsRepository.setUsageNoticeAccepted(true)
                        onFinished()
                    }
                },
            )
        }
    }
}

/**
 * 常驻操作栏。
 *
 * 修正「主按钮放在内容末尾」的做法：设置页 / 须知页内容在小屏上超过一屏时，
 * 末尾按钮会被滚出可视区。这里把它固定在内容区下方，只让内容滚动。
 */
@Composable
private fun OnboardingActionBar(
    step: Int,
    onNext: () -> Unit,
    onExit: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = HanimeDefaults.Widths.formMax)
                .padding(
                    horizontal = HanimeDefaults.Spacing.extraExtraLarge,
                    vertical = HanimeDefaults.Spacing.extraLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
        ) {
            when (step) {
                STEP_NOTICE -> NoticeAcceptButtons(onExit = onExit, onNext = onNext)

                STEP_SETTINGS -> HapticButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.onboarding_finish))
                }

                else -> HapticButton(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.onboarding_start))
                }
            }
        }
    }
}

@Composable
private fun stepTitle(step: Int): String = when (step) {
    STEP_WELCOME -> stringResource(Res.string.onboarding_welcome_title)
    STEP_NOTICE -> stringResource(Res.string.usage_notice_title)
    else -> stringResource(Res.string.onboarding_settings_title)
}

@Composable
private fun WelcomeStep() {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_launcher_h_chan_monochrome),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(56.dp),
        )
    }
    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.extraExtraLarge))
    Text(
        text = stringResource(Res.string.onboarding_welcome_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.huge))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.large),
    ) {
        FeatureCard(
            icon = Res.drawable.ic_play_circle,
            text = stringResource(Res.string.onboarding_feature_player),
        )
        FeatureCard(
            icon = Res.drawable.ic_download,
            text = stringResource(Res.string.onboarding_feature_offline),
        )
        FeatureCard(
            icon = Res.drawable.ic_refresh,
            text = stringResource(Res.string.onboarding_feature_sync),
        )
    }
}

@Composable
private fun FeatureCard(
    icon: DrawableResource,
    text: String,
) {
    CardContainerSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = HanimeDefaults.Spacing.itemHorizontal,
                    vertical = HanimeDefaults.Spacing.itemVertical,
                ),
            horizontalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.extraLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun NoticeStep() {
    CardContainerSurface(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.usage_notice_content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = HanimeDefaults.Spacing.itemHorizontal,
                    vertical = HanimeDefaults.Spacing.itemVertical,
                ),
        )
    }
}

@Composable
private fun NoticeAcceptButtons(
    onExit: () -> Unit,
    onNext: () -> Unit,
) {
    val requiredSeconds = if (isDebugBuild()) 5 else 20
    var remainingSeconds by remember { mutableIntStateOf(requiredSeconds) }
    var isResumed by remember { mutableStateOf(true) }
    var resetVersion by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    remainingSeconds = requiredSeconds
                    isResumed = true
                    resetVersion++
                }

                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    remainingSeconds = requiredSeconds
                    isResumed = false
                    resetVersion++
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isResumed, resetVersion) {
        while (isResumed && remainingSeconds > 0) {
            delay(1_000L.milliseconds)
            remainingSeconds--
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
    ) {
        HapticButton(
            onClick = onNext,
            enabled = remainingSeconds == 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (remainingSeconds == 0) {
                    stringResource(Res.string.onboarding_next)
                } else {
                    stringResource(Res.string.usage_notice_accept_countdown, remainingSeconds)
                },
            )
        }
        FilledTonalButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.usage_notice_decline))
        }
    }
}

@Composable
private fun SettingsStep() {
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(SettingsRepository.current.appLanguage) }
    var themeMode by remember { mutableStateOf(SettingsRepository.current.themeMode) }

    Text(
        text = stringResource(Res.string.onboarding_settings_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.huge))

    SectionLabel(stringResource(Res.string.onboarding_settings_language))
    SettingsSegmentedGroup(modifier = Modifier.fillMaxWidth()) {
        val followSystemLabel = stringResource(Res.string.follow_system)
        val languageOptions = listOf(
            AppLanguage.SYSTEM to followSystemLabel,
            AppLanguage.ENGLISH to stringResource(Res.string.onboarding_lang_english),
            AppLanguage.CHINESE_SIMPLIFIED to stringResource(Res.string.onboarding_lang_simplified),
            AppLanguage.CHINESE_TRADITIONAL to stringResource(Res.string.onboarding_lang_traditional),
        )
        languageOptions.forEach { (value, label) ->
            SettingChoiceItem(
                title = label,
                selected = language == value,
                onClick = {
                    if (language != value) {
                        language = value
                        scope.launch {
                            SettingsRepository.setLanguage(value)
                            applyAppLanguage(value)
                        }
                    }
                },
            )
        }
    }

    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.extraLarge))
    SectionLabel(stringResource(Res.string.onboarding_settings_theme))
    SettingsSegmentedGroup(modifier = Modifier.fillMaxWidth()) {
        // 文案与顺序跟设置页保持一致：浅色 / 深色 / 自动。
        val themeOptions = listOf(
            ThemeMode.Light to stringResource(Res.string.theme_mode_light),
            ThemeMode.Dark to stringResource(Res.string.theme_mode_dark),
            ThemeMode.System to stringResource(Res.string.theme_mode_auto),
        )
        themeOptions.forEach { (value, label) ->
            SettingChoiceItem(
                title = label,
                selected = themeMode == value,
                onClick = {
                    if (themeMode != value) {
                        themeMode = value
                        scope.launch { SettingsRepository.setThemeMode(value) }
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = HanimeDefaults.Spacing.large,
                bottom = HanimeDefaults.Spacing.medium,
                start = HanimeDefaults.Spacing.contentVertical,
            ),
    )
}
