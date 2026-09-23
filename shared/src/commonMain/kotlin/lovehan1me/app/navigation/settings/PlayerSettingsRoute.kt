package lovehan1me.app.navigation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.data.SettingsRepository
import lovehan1me.Res
import lovehan1me.default_
import lovehan1me.d_speed_times
import lovehan1me.mpv_advanced_settings_summary
import lovehan1me.mpv_settings_disabled_summary
import lovehan1me.feature.player.PlayerDefaults
import lovehan1me.feature.player.PlayerKernel
import lovehan1me.feature.settings.PlayerSettingsScreen
import lovehan1me.feature.settings.PlayerSettingsUiState
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.platform.SettingsPlatformCapabilities
import lovehan1me.core.platform.settingsPlatformCapabilities
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 长按速播的可选倍率：2.0~5.0，步进 0.5，共 7 档。
 *
 * 「(默认)」标注**由真实默认值决定**（见 [PlayerSettingsRouteScreen] 里的比较），
 * 不再写死在字面量上：此前默认值从 2.5f 改成 3f 时标签没跟着改，
 * 用户看到的「默认」和实际默认不是同一个值。
 */
internal val LONG_PRESS_SPEED_CHOICES = listOf(2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f)

/**
 * 倍率 → 一位小数字符串（`%.1f` 等价），喂给 `%1$s` 模板。
 *
 * CMP 的 `stringResource` 只替换 `%n$s`/`%n$d`，printf 式 `%.1f` 原样透出
 * （曾导致行值/摘要直接显示 `%.1f倍`），所以格式化必须在 Kotlin 侧完成。
 * commonMain 无 `String.format`，按整数缩放 HALF_UP 后拼串。
 */
internal fun formatSpeedTimes(speed: Float): String {
    val tenths = (speed * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

@Composable
fun PlayerSettingsRouteScreen(
    onNavigateToMpvSettings: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    // P6d-3-C2：builder 在 remember{} 内无法调 stringResource，字符串在外层预解析后传入
    val kernelForSummary = SettingsRepository.switchPlayerKernel
    // 平台能力：桌面/iOS 的 createPlaybackEngine 忽略 kernel 参数，因此「内核选择」
    // 与「MPV 入口」的可见性由本表决定，**不按设置值判断**。旧实现是
    // `mpvSettingsEnabled = (kernel == "MpvPlayer")`，后果是桌面用户选了 ExoPlayer 之后，
    // 正在跑 mpv 的桌面端反而把 MPV 高级设置置灰了（用户被自己的假设置锁在门外）。
    val capabilities = remember { settingsPlatformCapabilities() }
    val longPressDisplayTop = stringResource(
        Res.string.d_speed_times,
        formatSpeedTimes(SettingsRepository.longPressSpeedTime)
    )
    val mpvSummaryTop =
        // ⚠️ 摘要必须与 `mpvSettingsEnabled` 用**同一个判据**：否则桌面（内核选择已被隐藏，
        // 入口恒可点）会显示「仅当播放内核为 MPV 播放器时可配置」——界面自己打自己的脸。
        if (isMpvSettingsAvailable(capabilities, kernelForSummary)) {
            stringResource(Res.string.mpv_advanced_settings_summary)
        } else {
            stringResource(Res.string.mpv_settings_disabled_summary)
        }
    val uiState = remember(settings, longPressDisplayTop, mpvSummaryTop) {
        buildPlayerSettingsUiState(
            capabilities = capabilities,
            longPressDisplay = longPressDisplayTop,
            mpvSettingsSummary = mpvSummaryTop,
        )
    }
    val longPressDefault = AppSettings().longPressSpeedTime
    val defaultTag = stringResource(Res.string.default_)

    PlayerSettingsScreen(
        state = uiState,
        kernelOptions = PlayerKernel.entries.map { it.name to it.name },
        speedOptions = PlayerDefaults.speedLabels.zip(PlayerDefaults.speeds.map { it.toString() }),
        longPressSpeedOptions = LONG_PRESS_SPEED_CHOICES.map { speed ->
            val label = stringResource(Res.string.d_speed_times, formatSpeedTimes(speed))
            // ⚠️ 选项值必须与 `longPressSpeedTimes`（= 存储值的 `toString()`）**逐字符相同**：
            // ChoiceDialog 用 `selectedValue == value` 判选中（ui/component/ChoiceDialog.kt:44）。
            // 上游原先写死 "1"/"2"/"3"/"4"，而 `3f.toString()` 是 "3.0" —— 于是**默认档在对话框里
            // 不会显示为选中**（同属 F 类：界面写的和代码里的不一致）。统一走 `toString()`，
            // 与隔壁 `speedOptions`（`PlayerDefaults.speeds.map { it.toString() }`）同一约定。
            val value = speed.toString()
            (if (speed == longPressDefault) "$label ($defaultTag)" else label) to value
        },
        onKernelChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(playerKernel = lovehan1me.core.domain.model.PlayerKernel.fromValue(it)) } }
        },
        onPlayerSpeedChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(playerSpeed = it.toFloatOrNull() ?: settings.playerSpeed) } }
        },
        onLongPressSpeedChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(longPressSpeedTime = it.toFloatOrNull() ?: settings.longPressSpeedTime) } }
        },
        onAutoPlayNextChange = { enabled ->
            coroutineScope.launch { SettingsRepository.setAutoPlayNext(enabled) }
        },
        onOpenMpvSettings = onNavigateToMpvSettings,
        onPictureBrightnessChange = { value ->
            coroutineScope.launch { SettingsRepository.setPictureBrightness(value.toFloat()) }
        },
        onPictureContrastChange = { value ->
            coroutineScope.launch { SettingsRepository.setPictureContrast(value.toFloat()) }
        },
        onPictureSaturationChange = { value ->
            coroutineScope.launch { SettingsRepository.setPictureSaturation(value.toFloat()) }
        },
        onPictureAdjustReset = {
            coroutineScope.launch { SettingsRepository.resetPictureAdjust() }
        },
        onDanmakuEnabledChange = { enabled ->
            coroutineScope.launch {
                SettingsRepository.update { it.copy(danmakuEnabled = enabled) }
            }
        },
        onDanmakuCommentEnabledChange = { enabled ->
            coroutineScope.launch {
                SettingsRepository.update { it.copy(danmakuCommentEnabled = enabled) }
            }
        },
        onDanmakuProxyChange = { url ->
            coroutineScope.launch {
                SettingsRepository.update { it.copy(danmakuProxyBase = url) }
            }
        },
        onDanmakuAppIdChange = { appId ->
            coroutineScope.launch {
                SettingsRepository.update { it.copy(danmakuAppId = appId) }
            }
        },
        onDanmakuAppSecretChange = { secret ->
            coroutineScope.launch {
                SettingsRepository.update { it.copy(danmakuAppSecret = secret) }
            }
        },
        // 观感四项不在这儿夹取：滑杆本来就拖不出区间，而越界的历史值由
        // `DanmakuRenderOptions` 的派生属性在进引擎前统一夹回（读侧夹一次就够，
        // 写侧再夹会出现"存进去的值和显示的值不是同一个"）。
        onDanmakuFontSizeChange = { value ->
            coroutineScope.launch {
                SettingsRepository.update { it.copy(danmakuFontSizeSp = value) }
            }
        },
        onDanmakuOpacityChange = { value ->
            coroutineScope.launch {
                SettingsRepository.update { it.copy(danmakuOpacityPercent = value) }
            }
        },
        onDanmakuDisplayAreaChange = { value ->
            coroutineScope.launch {
                SettingsRepository.update { it.copy(danmakuDisplayAreaPercent = value) }
            }
        },
        onDanmakuSpeedChange = { value ->
            coroutineScope.launch {
                SettingsRepository.update { it.copy(danmakuSpeedPercent = value) }
            }
        },
    )
}

/**
 * 「MPV 高级设置」是否**可配置** —— 全页唯一判据。
 *
 * 摘要文案（[PlayerSettingsRouteScreen] 里的 `mpvSummaryTop`）与 UiState 的
 * `mpvSettingsEnabled` 必须共用本函数：一处分叉就会出现"入口能点、摘要却写着不可用"
 * ——桌面默认内核是 `ExoPlayer`（内核选择在桌面已被隐藏、入口恒可点），旧实现正是这么错的。
 *
 * 判据本身是**平台能力**而非设置值：有内核选择的平台（Android）才需要"切到 MPV 才可点"；
 * 没有内核选择的平台（桌面）引擎本来就是 mpv，恒可点。
 */
private fun isMpvSettingsAvailable(
    capabilities: SettingsPlatformCapabilities,
    kernel: String,
): Boolean = !capabilities.playerKernelSelection || kernel == PlayerKernel.MpvPlayer.name

private fun buildPlayerSettingsUiState(
    capabilities: SettingsPlatformCapabilities,
    longPressDisplay: String,
    mpvSettingsSummary: String,
): PlayerSettingsUiState {
    val kernel = SettingsRepository.switchPlayerKernel
    // 弹幕四项同源于一个快照：分四次读 `settings.value` 会读到跨写入的中间态
    val danmaku = SettingsRepository.current
    val currentSpeed = SettingsRepository.playerSpeed
    val currentLongPressSpeed = SettingsRepository.longPressSpeedTime
    val speedLabels = PlayerDefaults.speedLabels
    val speedDisplay = speedLabels.getOrElse(
        PlayerDefaults.speeds.indexOfFirst { it == currentSpeed }.takeIf { it >= 0 }
            ?: PlayerDefaults.DEFAULT_SPEED_INDEX
    ) { speedLabels[PlayerDefaults.DEFAULT_SPEED_INDEX] }
    return PlayerSettingsUiState(
        kernel = kernel,
        kernelDisplay = kernel,
        showKernelSelection = capabilities.playerKernelSelection,
        showMpvSettings = capabilities.mpvAdvancedSettings,
        // 有内核选择时（Android）必须先切到 MPV 才可点；
        // 无内核选择时（桌面）引擎本来就是 mpv，恒可点。
        mpvSettingsEnabled = isMpvSettingsAvailable(capabilities, kernel),
        mpvSettingsSummary = mpvSettingsSummary,
        playerSpeed = currentSpeed.toString(),
        playerSpeedLabel = speedDisplay,
        longPressSpeedTimes = currentLongPressSpeed.toString(),
        longPressSpeedTimesLabel = longPressDisplay,
        autoPlayNext = SettingsRepository.autoPlayNext,
        // G2-3b：画面调节 —— 按平台能力（有没有 mpv）决定是否列出，并如实标注"仅 mpv 生效"
        showPictureAdjust = capabilities.mpvAdvancedSettings,
        pictureBrightness = SettingsRepository.pictureAdjust.brightness.toInt(),
        pictureContrast = SettingsRepository.pictureAdjust.contrast.toInt(),
        pictureSaturation = SettingsRepository.pictureAdjust.saturation.toInt(),
        danmakuEnabled = danmaku.danmakuEnabled,
        danmakuCommentEnabled = danmaku.danmakuCommentEnabled,
        danmakuProxyBase = danmaku.danmakuProxyBase,
        danmakuAppId = danmaku.danmakuAppId,
        danmakuAppSecret = danmaku.danmakuAppSecret,
        danmakuFontSizeSp = danmaku.danmakuFontSizeSp,
        danmakuOpacityPercent = danmaku.danmakuOpacityPercent,
        danmakuDisplayAreaPercent = danmaku.danmakuDisplayAreaPercent,
        danmakuSpeedPercent = danmaku.danmakuSpeedPercent,
    )
}
