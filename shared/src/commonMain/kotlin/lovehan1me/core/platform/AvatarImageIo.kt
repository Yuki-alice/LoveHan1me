package lovehan1me.core.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 阶段一⑧：头像裁剪的**像素活**平台入口。
 *
 * 分工：裁剪**交互**在 commonMain（纯 Compose，见
 * [lovehan1me.feature.account.AvatarCropScreen]），平台只负责两件绕不开的事——
 * 解码源图、按给定矩形裁剪并落盘。这样桌面/iOS 不必依赖 Android-only 的
 * 裁剪库（`cn.mucute:compose-avatar-cropper` 只有 `-android` 产物）。
 */

/** 正方形裁剪区，单位为**源图像素**坐标。 */
data class AvatarCropRect(val x: Int, val y: Int, val size: Int)

/**
 * 解码源图（可以是 content:// / file:// / 本地绝对路径）。
 *
 * @param maxPx 长边上限，超过则降采样，避免把几十 MB 的原图塞进显存。
 * @return 解码结果；平台不支持或失败返回 `null`。
 */
expect suspend fun decodeAvatarSource(source: String, maxPx: Int = 1600): ImageBitmap?

/**
 * 按 [rect] 裁剪并保存到本平台可写的位置。
 *
 * @return 产物**绝对路径**；失败返回 `null`（调用方据此提示用户，不要崩溃）。
 */
expect suspend fun cropAndSaveAvatar(
    source: String,
    rect: AvatarCropRect,
    outputPx: Int = 512,
): String?
