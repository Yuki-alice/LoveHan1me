package lovehan1me.logic

/**
 * EUC-JP 解码（P4b：GetchuNetworkRepo 下沉 commonMain 需要）。
 * commonMain 无 java.nio.charset，由各平台提供实现：
 *  - jvmMain（android+desktop）：Charset.forName("EUC-JP")
 *  - iosMain：NSString 的 kCFStringEncodingEUC_JP（0x0628）
 */
internal expect fun ByteArray.decodeEucJp(): String
