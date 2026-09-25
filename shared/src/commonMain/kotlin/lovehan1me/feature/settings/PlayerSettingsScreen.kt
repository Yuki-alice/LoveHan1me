package lovehan1me.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.cancel
import lovehan1me.confirm
import lovehan1me.danmaku_settings_app_id
import lovehan1me.danmaku_settings_app_secret
import lovehan1me.danmaku_settings_credentials_hint
import lovehan1me.danmaku_settings_comment_enabled
import lovehan1me.danmaku_settings_enabled
import lovehan1me.danmaku_settings_group
import lovehan1me.danmaku_settings_proxy
import lovehan1me.danmaku_settings_proxy_hint
import lovehan1me.danmaku_settings_proxy_invalid
import lovehan1me.danmaku_settings_secret_set
import lovehan1me.danmaku_font_size
import lovehan1me.danmaku_font_size_value
import lovehan1me.danmaku_opacity
import lovehan1me.danmaku_display_area
import lovehan1me.danmaku_speed
import lovehan1me.danmaku_percent_value
import lovehan1me.auto_play_next_title
import lovehan1me.auto_play_next_summary
import lovehan1me.ic_skip
import lovehan1me.data.danmaku.asValidBaseUrl
import lovehan1me.mpv_settings_disabled_summary
import lovehan1me.mpv_advanced_settings
import lovehan1me.long_press_speed_summary
import lovehan1me.long_press_speed_multiplier
import lovehan1me.default_playback_speed
import lovehan1me.player_settings_controls
import lovehan1me.picture_adjust_reset
import lovehan1me.picture_adjust_summary
import lovehan1me.picture_brightness
import lovehan1me.picture_contrast
import lovehan1me.picture_saturation
import lovehan1me.ic_comment
import lovehan1me.ic_fullscreen
import lovehan1me.ic_h_text
import lovehan1me.ic_light_mode
import lovehan1me.ic_lightbulb
import lovehan1me.ic_palette
import lovehan1me.ic_lock
import lovehan1me.ic_person
import lovehan1me.ic_player_setting
import lovehan1me.ic_router
import lovehan1me.ic_speed
import lovehan1me.ic_touch_long
import lovehan1me.ic_visibility
import lovehan1me.ic_visibility_off
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import lovehan1me.feature.danmaku.DANMAKU_DISPLAY_AREA_RANGE
import lovehan1me.feature.danmaku.DANMAKU_FONT_SIZE_RANGE
import lovehan1me.feature.danmaku.DANMAKU_OPACITY_RANGE
import lovehan1me.feature.danmaku.DANMAKU_SPEED_RANGE
import lovehan1me.ui.component.ChoiceDialog
import lovehan1me.ui.component.SettingNavigationItem
import lovehan1me.ui.component.SettingsPlainBox
import lovehan1me.ui.component.SettingSliderItem
import lovehan1me.ui.component.SettingSwitchItem
import lovehan1me.ui.component.segmentedGroup
import lovehan1me.ui.component.segmentedSection
import lovehan1me.ui.component.lazy.LazyColumn

data class PlayerSettingsUiState(
    /** 本平台是否可能让「MPV 高级设置」生效（Android/iOS 无 mpv，整项不展示）。 */
    val showMpvSettings: Boolean,
    /** 展示 MPV 入口时它是否可点。 */
    val mpvSettingsEnabled: Boolean,
    val mpvSettingsSummary: String,
    val playerSpeed: String,
    val playerSpeedLabel: String,
    val longPressSpeedTimes: String,
    val longPressSpeedTimesLabel: String,
    /** 系列自动连播（播完播下一集，仅系列有效）。 */
    val autoPlayNext: Boolean,
    // ── G2-3b：画面调节（仅 mpv 内核真的生效）────────────────────
    /**
     * 是否列出「画面调节」。
     *
     * 判据是**平台能力**（有没有 mpv），与设置页其它 mpv 项同源：设置页没有引擎实例，
     * 不该为了渲染三行滑杆去初始化播放器。真实是否生效由引擎的
     * `supportsPictureAdjust()` 在播放时决定 —— 那正是"不做假开关"的另一半。
     */
    val showPictureAdjust: Boolean,
    /** 亮度 / 对比度 / 饱和度：-100 ~ 100，0 = 原始（与 mpv 属性同单位）。 */
    val pictureBrightness: Int,
    val pictureContrast: Int,
    val pictureSaturation: Int,
    // ── 弹幕（弹弹play）────────────────────────────────────────────
    /** 播放器内的弹幕总开关。与"有没有配置数据源"是两件事，见 `DanmakuStatusBar`。 */
    val danmakuEnabled: Boolean,
    /** 站内评论投影为主源：默认开，关掉后回到纯弹弹链路。 */
    val danmakuCommentEnabled: Boolean,
    val danmakuProxyBase: String,
    val danmakuAppId: String,
    /**
     * 明文只用于**回填编辑框**：行上显示的是"已设置"，从不把密钥画到列表里。
     * 它不进备份导出（`DataStoreManager.AUTH_KEYS`），也不会被写进本仓库的任何默认值。
     */
    val danmakuAppSecret: String,
    /**
     * 观感四项存的是**用户能核对的原值**（sp 与百分比），折算规则只有一处实现：
     * 见 `DanmakuRenderOptions`。区间同样取自那边（`DANMAKU_*_RANGE`），
     * 这样"滑杆能拖到的值"与"绘制层接受的值"不可能对不上。
     */
    val danmakuFontSizeSp: Int,
    val danmakuOpacityPercent: Int,
    val danmakuDisplayAreaPercent: Int,
    val danmakuSpeedPercent: Int,
)

private enum class PlayerChoiceDialog {
    Speed,
    LongPressSpeed,
}

/** G2-3b：画面调节滑杆区间（-100~100，0 = 原始），与 mpv 的属性范围同义。 */
private val PICTURE_ADJUST_RANGE = -100..100

/** 弹幕分组里三个可编辑的文本项（同一个对话框组件，按此切换标题/掩码）。 */
private enum class DanmakuTextField { Proxy, AppId, AppSecret }

@Composable
fun PlayerSettingsScreen(
    state: PlayerSettingsUiState,
    speedOptions: List<Pair<String, String>>,
    longPressSpeedOptions: List<Pair<String, String>>,
    onPlayerSpeedChange: (String) -> Unit,
    onLongPressSpeedChange: (String) -> Unit,
    onAutoPlayNextChange: (Boolean) -> Unit = {},
    onOpenMpvSettings: () -> Unit,
    // G2-3b：画面调节
    onPictureBrightnessChange: (Int) -> Unit = {},
    onPictureContrastChange: (Int) -> Unit = {},
    onPictureSaturationChange: (Int) -> Unit = {},
    onPictureAdjustReset: () -> Unit = {},
    onDanmakuEnabledChange: (Boolean) -> Unit,
    onDanmakuCommentEnabledChange: (Boolean) -> Unit,
    onDanmakuProxyChange: (String) -> Unit,
    onDanmakuAppIdChange: (String) -> Unit,
    onDanmakuAppSecretChange: (String) -> Unit,
    onDanmakuFontSizeChange: (Int) -> Unit,
    onDanmakuOpacityChange: (Int) -> Unit,
    onDanmakuDisplayAreaChange: (Int) -> Unit,
    onDanmakuSpeedChange: (Int) -> Unit,
) {
    var activeDialog by rememberSaveable { mutableStateOf<PlayerChoiceDialog?>(null) }
    var activeTextField by rememberSaveable { mutableStateOf<DanmakuTextField?>(null) }

    ChoiceDialog(
        visible = activeDialog == PlayerChoiceDialog.Speed,
        title = stringResource(Res.string.default_playback_speed),
        options = speedOptions,
        selectedValue = state.playerSpeed,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onPlayerSpeedChange(it)
        },
    )

    ChoiceDialog(
        visible = activeDialog == PlayerChoiceDialog.LongPressSpeed,
        title = stringResource(Res.string.long_press_speed_multiplier),
        options = longPressSpeedOptions,
        selectedValue = state.longPressSpeedTimes,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onLongPressSpeedChange(it)
        },
    )

    when (activeTextField) {
        // 代理地址必须过**与数据源同一把尺**（`asValidBaseUrl`）：
        // 那边对不合法的地址是"记一条日志然后按未配置处理"，
        // 这里若允许保存，用户看到的就是"填了却没生效"——最难自查的一类 bug。
        DanmakuTextField.Proxy -> DanmakuTextSettingDialog(
            title = stringResource(Res.string.danmaku_settings_proxy),
            initialValue = state.danmakuProxyBase,
            hint = stringResource(Res.string.danmaku_settings_proxy_hint),
            invalidMessage = stringResource(Res.string.danmaku_settings_proxy_invalid),
            password = false,
            validate = { it.isBlank() || it.asValidBaseUrl() != null },
            onDismiss = { activeTextField = null },
            onConfirm = {
                onDanmakuProxyChange(it)
                activeTextField = null
            },
        )

        DanmakuTextField.AppId -> DanmakuTextSettingDialog(
            title = stringResource(Res.string.danmaku_settings_app_id),
            initialValue = state.danmakuAppId,
            hint = stringResource(Res.string.danmaku_settings_credentials_hint),
            invalidMessage = "",
            password = false,
            validate = { true },
            onDismiss = { activeTextField = null },
            onConfirm = {
                onDanmakuAppIdChange(it)
                activeTextField = null
            },
        )

        DanmakuTextField.AppSecret -> DanmakuTextSettingDialog(
            title = stringResource(Res.string.danmaku_settings_app_secret),
            initialValue = state.danmakuAppSecret,
            hint = stringResource(Res.string.danmaku_settings_credentials_hint),
            invalidMessage = "",
            password = true,
            validate = { true },
            onDismiss = { activeTextField = null },
            onConfirm = {
                onDanmakuAppSecretChange(it)
                activeTextField = null
            },
        )

        null -> Unit
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        enableItemAnimation = false,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segmentedSection(titleRes = Res.string.player_settings_controls) {
            segmentedGroup {
                // Gate3-P6：内核选择行已删 —— Android 砍掉 mpv 内核后三端都只剩一个真引擎，
                // kernel 参数在工厂里是惰性值（`SettingsPlatformCapabilities` 已无该判据）。
                // MPV 高级设置：Android/iOS 无 mpv，整项不展示；桌面引擎就是 mpv。
                if (state.showMpvSettings) {
                    SettingNavigationItem(
                        title = stringResource(Res.string.mpv_advanced_settings),
                        summary = state.mpvSettingsSummary,
                        iconRes = Res.drawable.ic_player_setting,
                        onClick = onOpenMpvSettings,
                        enabled = state.mpvSettingsEnabled,
                        valueText = null,
                    )
                }
                SettingNavigationItem(
                    title = stringResource(Res.string.default_playback_speed),
                    valueText = state.playerSpeedLabel,
                    iconRes = Res.drawable.ic_speed,
                    onClick = { activeDialog = PlayerChoiceDialog.Speed },
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.long_press_speed_multiplier),
                    summary = stringResource(Res.string.long_press_speed_summary,
                        state.longPressSpeedTimesLabel,
                    ),
                    valueText = state.longPressSpeedTimesLabel,
                    iconRes = Res.drawable.ic_touch_long,
                    onClick = { activeDialog = PlayerChoiceDialog.LongPressSpeed },
                )
                SettingSwitchItem(
                    title = stringResource(Res.string.auto_play_next_title),
                    summary = stringResource(Res.string.auto_play_next_summary),
                    checked = state.autoPlayNext,
                    iconRes = Res.drawable.ic_skip,
                    onCheckedChange = onAutoPlayNextChange,
                )
                // ── G2-3b：画面调节（亮度 / 对比度 / 饱和度）──────────
                // 区间与 mpv 的属性范围一致（-100~100，0 = 原始），步进 5 是"肉眼
                // 分得出差别"的最小粒度 —— 再细就只有数字在动。
                if (state.showPictureAdjust) {
                    SettingSliderItem(
                        title = stringResource(Res.string.picture_brightness),
                        summary = stringResource(Res.string.picture_adjust_summary),
                        value = state.pictureBrightness.coerceIn(PICTURE_ADJUST_RANGE),
                        valueRange = PICTURE_ADJUST_RANGE,
                        step = 5,
                        iconRes = Res.drawable.ic_light_mode,
                        onValueChange = onPictureBrightnessChange,
                    )
                    SettingSliderItem(
                        title = stringResource(Res.string.picture_contrast),
                        value = state.pictureContrast.coerceIn(PICTURE_ADJUST_RANGE),
                        valueRange = PICTURE_ADJUST_RANGE,
                        step = 5,
                        iconRes = Res.drawable.ic_lightbulb,
                        onValueChange = onPictureContrastChange,
                    )
                    SettingSliderItem(
                        title = stringResource(Res.string.picture_saturation),
                        value = state.pictureSaturation.coerceIn(PICTURE_ADJUST_RANGE),
                        valueRange = PICTURE_ADJUST_RANGE,
                        step = 5,
                        iconRes = Res.drawable.ic_palette,
                        onValueChange = onPictureSaturationChange,
                    )
                    SettingNavigationItem(
                        title = stringResource(Res.string.picture_adjust_reset),
                        iconRes = Res.drawable.ic_player_setting,
                        valueText = null,
                        onClick = onPictureAdjustReset,
                    )
                }
            }
        }

        // ── 弹幕 ─────────────────────────────────────────────────────
        // 三项都是「可选」：不填也能装，只是播放器里那条状态条会一直说"未配置"。
        // 项目**不内置**任何代理地址与密钥（弹弹play 官方政策禁止共享密钥）。
        segmentedSection(
            titleRes = Res.string.danmaku_settings_group,
        ) {
            segmentedGroup {
                SettingSwitchItem(
                    title = stringResource(Res.string.danmaku_settings_enabled),
                    checked = state.danmakuEnabled,
                    iconRes = Res.drawable.ic_comment,
                    onCheckedChange = onDanmakuEnabledChange,
                )
                SettingSwitchItem(
                    title = stringResource(Res.string.danmaku_settings_comment_enabled),
                    checked = state.danmakuCommentEnabled,
                    iconRes = Res.drawable.ic_comment,
                    onCheckedChange = onDanmakuCommentEnabledChange,
                )
                // 观感四项：区间常量来自 DanmakuRenderOptions，**不在这里重抄一遍**。
                // 滑杆本身不显示数值，所以把当前值放 summary —— 拖动时唯一能核对的东西就是它。
                SettingSliderItem(
                    title = stringResource(Res.string.danmaku_font_size),
                    summary = stringResource(
                        Res.string.danmaku_font_size_value,
                        state.danmakuFontSizeSp.coerceIn(DANMAKU_FONT_SIZE_RANGE),
                    ),
                    value = state.danmakuFontSizeSp.coerceIn(DANMAKU_FONT_SIZE_RANGE),
                    valueRange = DANMAKU_FONT_SIZE_RANGE,
                    iconRes = Res.drawable.ic_h_text,
                    onValueChange = onDanmakuFontSizeChange,
                )
                SettingSliderItem(
                    title = stringResource(Res.string.danmaku_opacity),
                    summary = stringResource(
                        Res.string.danmaku_percent_value,
                        state.danmakuOpacityPercent.coerceIn(DANMAKU_OPACITY_RANGE),
                    ),
                    value = state.danmakuOpacityPercent.coerceIn(DANMAKU_OPACITY_RANGE),
                    valueRange = DANMAKU_OPACITY_RANGE,
                    step = 5,
                    iconRes = Res.drawable.ic_palette,
                    onValueChange = onDanmakuOpacityChange,
                )
                SettingSliderItem(
                    title = stringResource(Res.string.danmaku_display_area),
                    summary = stringResource(
                        Res.string.danmaku_percent_value,
                        state.danmakuDisplayAreaPercent.coerceIn(DANMAKU_DISPLAY_AREA_RANGE),
                    ),
                    value = state.danmakuDisplayAreaPercent.coerceIn(DANMAKU_DISPLAY_AREA_RANGE),
                    valueRange = DANMAKU_DISPLAY_AREA_RANGE,
                    step = 5,
                    iconRes = Res.drawable.ic_fullscreen,
                    onValueChange = onDanmakuDisplayAreaChange,
                )
                SettingSliderItem(
                    title = stringResource(Res.string.danmaku_speed),
                    summary = stringResource(
                        Res.string.danmaku_percent_value,
                        state.danmakuSpeedPercent.coerceIn(DANMAKU_SPEED_RANGE),
                    ),
                    value = state.danmakuSpeedPercent.coerceIn(DANMAKU_SPEED_RANGE),
                    valueRange = DANMAKU_SPEED_RANGE,
                    step = 10,
                    iconRes = Res.drawable.ic_speed,
                    onValueChange = onDanmakuSpeedChange,
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.danmaku_settings_proxy),
                    summary = stringResource(Res.string.danmaku_settings_proxy_hint),
                    valueText = state.danmakuProxyBase,
                    iconRes = Res.drawable.ic_router,
                    onClick = { activeTextField = DanmakuTextField.Proxy },
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.danmaku_settings_app_id),
                    valueText = state.danmakuAppId,
                    iconRes = Res.drawable.ic_person,
                    onClick = { activeTextField = DanmakuTextField.AppId },
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.danmaku_settings_app_secret),
                    summary = stringResource(Res.string.danmaku_settings_credentials_hint),
                    // 密钥只在这里显示"有没有设过"，真值从不进列表
                    valueText = if (state.danmakuAppSecret.isBlank()) {
                        null
                    } else {
                        stringResource(Res.string.danmaku_settings_secret_set)
                    },
                    iconRes = Res.drawable.ic_lock,
                    onClick = { activeTextField = DanmakuTextField.AppSecret },
                )
            }
        }
    }
}

/**
 * 弹幕分组三个文本项共用的编辑框（代理地址 / AppID / AppSecret）。
 *
 * 密钥默认掩码 + 一个显隐开关：30 多个字符的密钥打错了是看不出来的，
 * 只能靠"显示明文"这一条退路核对；而默认掩码挡住的是旁人扫一眼。
 */
@Composable
private fun DanmakuTextSettingDialog(
    title: String,
    initialValue: String,
    hint: String,
    invalidMessage: String,
    password: Boolean,
    validate: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    var revealed by remember { mutableStateOf(false) }
    val valid = validate(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(title) },
                    singleLine = true,
                    isError = !valid,
                    visualTransformation = if (password && !revealed) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when {
                            password -> KeyboardType.Password
                            else -> KeyboardType.Uri
                        },
                    ),
                    trailingIcon = {
                        if (password) {
                            IconButton(onClick = { revealed = !revealed }) {
                                Icon(
                                    painter = painterResource(
                                        if (revealed) Res.drawable.ic_visibility_off
                                        else Res.drawable.ic_visibility
                                    ),
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (valid) hint else invalidMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = valid) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
