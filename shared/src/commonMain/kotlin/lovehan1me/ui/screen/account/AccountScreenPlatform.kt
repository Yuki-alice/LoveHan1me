package lovehan1me.ui.screen.account

/**
 * M5-4：读取头像裁剪产物。
 * 实际上该路径仅 Android 触发（裁剪产物来自 `:app` cropper；桌面/iOS 的
 * onPickAvatarImage 为 null，不会产生 pendingAvatarCropResult），
 * expect 只为让 commonMain 编译通过。
 */
internal expect fun readFileBytes(path: String): ByteArray?
