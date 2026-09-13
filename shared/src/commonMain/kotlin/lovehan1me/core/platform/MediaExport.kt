package lovehan1me.core.platform

/**
 * 把生成的媒体产物（GIF / 截图）**保存到用户可见位置并唤起系统分享**。
 *
 * ## 为什么"保存"和"分享"做成一个动作
 * 路线图 M3-b「保存 → 系统分享」、M3-c「截图 + 系统分享面板」描述的都是**一次用户手势**，
 * 中间不让用户选目录、也不做相册管理。合成一个平台动作后：
 * - 三端各只实现一处，M3-c 可直接复用；
 * - 不需要把"刚保存产物的 URI"在 UI 状态里传递（Android 上那是个 content:// URI，
 *   跨组合传递很容易失效）。
 *
 * ## 为什么不是 @Composable
 * 参照既有 `saveImageToGallery` 的形态做成 `expect suspend fun`：
 * Android 侧靠 `Han1meDatabaseContext.appContext` 与 `CurrentActivityHolder` 自取上下文
 * （项目既有约定见 `core/platform/HomePlatformActions.android.kt`），
 * 于是这个能力不需要 `remember`，从任意协程里都能调。
 */
sealed interface MediaExportOutcome {
    /** 已保存并成功唤起系统分享。 */
    data class Shared(val location: String) : MediaExportOutcome

    /** 已保存，但该平台没有可用的分享面板（或不满足唤起条件）—— 提示用户去 [location] 找。 */
    data class SavedOnly(val location: String) : MediaExportOutcome

    /** 保存失败，[message] 供 UI 展示（**不要**把异常直接抛给 UI）。 */
    data class Failed(val message: String) : MediaExportOutcome
}

/**
 * 保存 [bytes] 为 [fileName] 并（在支持的平台上）唤起系统分享。
 *
 * @param mimeType Android 侧写 MediaStore 与分享 Intent 都要用它（GIF = `image/gif`）
 * @param locationHint 给失败提示用的中文位置描述，例如"相册"——各端的落点不同，由 actual 决定实际值
 */
expect suspend fun exportMediaAndShare(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
): MediaExportOutcome
