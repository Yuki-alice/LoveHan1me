package lovehan1me.core.util

import lovehan1me.core.platform.readAssetBytes
import java.io.File

/**
 * jvmMain（android + desktop 共享）：shader 落盘。
 *
 * 统一从 `composeResources/files/shaders/` 读（经 [readAssetBytes]），
 * 写到 [mpvShaderTargetDir]（各端自己给目录），再拼成 mpv 的 `:` 分隔绝对路径。
 * 已存在且大小一致的文件跳过，避免每次切档都全量拷贝。
 */
internal actual suspend fun materializeMpvShaders(level: Int): String? {
    val names = MpvShaders.fileNames(level)
    if (names.isEmpty()) return ""
    val dir = mpvShaderTargetDir() ?: return null
    return runCatching {
        val target = File(dir)
        if (!target.exists() && !target.mkdirs()) return null
        // ⚠️ 路径分隔符：mpv 的路径列表在 Windows 上用 `;`、Unix 上用 `:`
        //（`C:\...` 里的冒号会和 Unix 分隔符打架）。File.pathSeparator 正好对应。
        names.mapNotNull { name ->
            val bytes = readAssetBytes("shaders/$name") ?: return null
            val file = File(target, name)
            if (!file.exists() || file.length() != bytes.size.toLong()) {
                file.writeBytes(bytes)
            }
            file.absolutePath
        }.takeIf { it.size == names.size }?.joinToString(separator = File.pathSeparator)
    }.getOrNull()
}
