/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.support

internal actual fun currentPlatformImpl(): Platform {
    val os = System.getProperty("os.name")?.lowercase() ?: error("无法判定平台: 'os.name' 为 null")
    return when {
        "mac" in os || "os x" in os || "darwin" in os -> Platform.MacOS
        "windows" in os -> Platform.Windows
        else -> Platform.Linux
    }
}
