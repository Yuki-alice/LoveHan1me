package lovehan1me.core.util

import lovehan1me.core.platform.readAssetBytes
import java.io.File

/**
 * jvmMain（android + desktop 共享）：shader 落盘。
 *
 * 统一从 `composeResources/files/shaders/` 读（经 [readAssetBytes]），写到
 * [mpvShaderTargetDir]（各端自己给目录）。返回**按挂载顺序排列的绝对路径列表**：
 * mpv 的 `change-list` 一次只吃一个路径，列表属性用逗号分隔，
 * 拼成 `:`/`;` 串会被当成单个不存在的路径。
 *
 * 已存在且大小一致的文件跳过，避免每次切档都全量拷贝（VL 两个文件合计约 290 KB）。
 */
actual suspend fun materializeMpvShaders(level: Int): List<String>? {
    val names = MpvShaders.fileNames(level)
    if (names.isEmpty()) return emptyList()
    val dir = mpvShaderTargetDir() ?: return null
    return runCatching {
        val target = File(dir)
        if (!target.exists() && !target.mkdirs()) return null
        names.map { name ->
            val bytes = readAssetBytes("shaders/$name") ?: return null
            val file = File(target, name)
            if (!file.exists() || file.length() != bytes.size.toLong()) {
                file.writeBytes(bytes)
            }
            file.absolutePath
        }
    }.getOrNull()
}
