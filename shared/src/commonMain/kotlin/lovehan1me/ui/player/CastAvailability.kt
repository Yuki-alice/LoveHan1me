package lovehan1me.ui.player

/**
 * P6d-4：Google Cast 可用性判定（原 :app PlayerSettingsRoute 内联 GoogleApiAvailability 检查）。
 *
 * Android：Play Services 可用（ConnectionResult.SUCCESS）时为 true；
 * Desktop / iOS：无 Cast 能力，恒 false（设置页据此隐藏/禁用 Cast 项，与原「不可用」路径一致）。
 */
expect fun isCastAvailable(): Boolean
