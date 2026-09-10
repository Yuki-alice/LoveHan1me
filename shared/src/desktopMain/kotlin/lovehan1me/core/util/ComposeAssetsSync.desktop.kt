package lovehan1me.core.util

import java.io.File

// Desktop：dev run 下 CMP 把 composeResources 落在 cwd/compose-resources/<pkg>/；
// 打包形态待 P6d 桌面资源核查（找不到回退 null → 调用方保底原样标签）
internal actual fun readComposeFileSync(path: String): ByteArray? = runCatching {
    File("compose-resources/lovehan1me", path).readBytes()
}.getOrNull()
