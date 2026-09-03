package io.github.daisukikaffuchino.han1meviewer.logic

import java.nio.charset.Charset

// jvmMain（android + desktop 共享）：直接走 JVM 的 EUC-JP 解码
internal actual fun ByteArray.decodeEucJp(): String = String(this, Charset.forName("EUC-JP"))
