package lovehan1me.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSStringEncoding
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.posix.memcpy

// kCFStringEncodingEUC_JP 的常量值（NSStringEncoding 底层为 ULong）
private const val EUC_JP_ENCODING: ULong = 0x0628u

/**
 * iosMain（iosArm64 + iosSimulatorArm64）：经 NSString 的 EUC-JP 编码解码。
 * 步骤：bytes → NSData → NSString(EUC-JP) → dataUsingEncoding(UTF-8) → ByteArray → decodeToString。
 * 任一步失败回退 UTF-8 解码（原 JVM 语义 String(bytes, charset) 不会抛错，会替换非法序列）。
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun ByteArray.decodeEucJp(): String {
    val nsEncoding: NSStringEncoding = EUC_JP_ENCODING
    val utf8Data = memScoped {
        val data: NSData = NSData.create(
            bytes = allocArrayOf(this@decodeEucJp),
            length = size.toULong(),
        )
        NSString.create(data = data, encoding = nsEncoding)
            ?.dataUsingEncoding(NSUTF8StringEncoding)
    } ?: return decodeToString()

    val out = ByteArray(utf8Data.length.toInt())
    val ptr = utf8Data.bytes ?: return decodeToString()
    if (out.isNotEmpty()) {
        out.usePinned { memcpy(it.addressOf(0), ptr, utf8Data.length) }
    }
    return out.decodeToString()
}
