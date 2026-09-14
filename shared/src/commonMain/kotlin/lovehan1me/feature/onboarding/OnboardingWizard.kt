package lovehan1me.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.always_off
import lovehan1me.always_on
import lovehan1me.back
import lovehan1me.core.domain.model.AppLanguage
import lovehan1me.core.domain.model.ThemeMode
import lovehan1me.core.platform.applyAppLanguage
import lovehan1me.core.util.isDebugBuild
import lovehan1me.data.SettingsRepository
import lovehan1me.follow_system
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
import lovehan1me.ui.component.HapticTextButton as TextButton
import lovehan1me.usage_notice_accept_countdown
import lovehan1me.usage_notice_content
import lovehan1me.usage_notice_decline
import lovehan1me.usage_notice_title
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

/**
 * 首次启动向导：欢迎 → 使用须知 → 基础设置（语言 + 主题）。
 *
 * 替代此前的"三段门控"（使用须知对话框 → 来源确认问卷 → 非法来源警告）：
 * 来源确认整套删除——问用户从哪下载的、答错再罚抄仓库链接，
 * 本质是安全话剧，还把正常用户挡在门外。
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LinearProgressIndicator(
                progress = { (step + 1) / STEP_COUNT.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (step) {
                    STEP_WELCOME -> WelcomeStep()
                    STEP_NOTICE -> NoticeStep()
                    else -> SettingsStep()
                }
            }
            WizardActions(
                step = step,
                onBack = { if (step > STEP_WELCOME) step-- },
                onNext = { if (step < STEP_SETTINGS) step++ },
                onFinish = {
                    scope.launch {
                        SettingsRepository.setUsageNoticeAccepted(true)
                        onFinished()
                    }
                },
                onExit = onExit,
            )
        }
    }
}

@Composable
private fun WizardActions(
    step: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step == STEP_WELCOME) {
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onNext) {
                Text(stringResource(Res.string.onboarding_start))
            }
        } else if (step == STEP_NOTICE) {
            // 须知页的操作键在内容区（同意带倒计时 / 不同意退出），底栏只留返回。
            TextButton(onClick = onBack) {
                Text(stringResource(Res.string.back))
            }
            Spacer(modifier = Modifier.weight(1f))
            NoticeAcceptButtons(onExit = onExit, onNext = onNext)
        } else {
            TextButton(onClick = onBack) {
                Text(stringResource(Res.string.back))
            }
            Button(onClick = onFinish) {
                Text(stringResource(Res.string.onboarding_finish))
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Spacer(modifier = Modifier.height(48.dp))
    Text(
        text = stringResource(Res.string.onboarding_welcome_title),
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(Res.string.onboarding_welcome_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(32.dp))
    Column(
        modifier = Modifier.widthIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeatureBullet(stringResource(Res.string.onboarding_feature_player))
        FeatureBullet(stringResource(Res.string.onboarding_feature_offline))
        FeatureBullet(stringResource(Res.string.onboarding_feature_sync))
    }
}

@Composable
private fun FeatureBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NoticeStep() {
    Text(
        text = stringResource(Res.string.usage_notice_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(Res.string.usage_notice_content),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
    )
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

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onExit) {
            Text(stringResource(Res.string.usage_notice_decline))
        }
        Button(
            enabled = remainingSeconds == 0,
            onClick = onNext,
        ) {
            Text(
                text = if (remainingSeconds == 0) {
                    stringResource(Res.string.onboarding_next)
                } else {
                    stringResource(Res.string.usage_notice_accept_countdown, remainingSeconds)
                },
            )
        }
    }
}

@Composable
private fun SettingsStep() {
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(SettingsRepository.current.appLanguage) }
    var themeMode by remember { mutableStateOf(SettingsRepository.current.themeMode) }

    Text(
        text = stringResource(Res.string.onboarding_settings_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(Res.string.onboarding_settings_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(24.dp))

    Column(
        modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.onboarding_settings_language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val followSystemLabel = stringResource(Res.string.follow_system)
            val languageOptions = listOf(
                AppLanguage.SYSTEM to followSystemLabel,
                AppLanguage.ENGLISH to stringResource(Res.string.onboarding_lang_english),
                AppLanguage.CHINESE_SIMPLIFIED to stringResource(Res.string.onboarding_lang_simplified),
                AppLanguage.CHINESE_TRADITIONAL to stringResource(Res.string.onboarding_lang_traditional),
            )
            languageOptions.forEach { (value, label) ->
                OptionRow(
                    label = label,
                    selected = language == value,
                    onSelect = {
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.onboarding_settings_theme),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val themeOptions = listOf(
                ThemeMode.System to stringResource(Res.string.follow_system),
                ThemeMode.Light to stringResource(Res.string.always_off),
                ThemeMode.Dark to stringResource(Res.string.always_on),
            )
            themeOptions.forEach { (value, label) ->
                OptionRow(
                    label = label,
                    selected = themeMode == value,
                    onSelect = {
                        if (themeMode != value) {
                            themeMode = value
                            scope.launch { SettingsRepository.setThemeMode(value) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
