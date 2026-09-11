package lovehan1me.feature.account

import lovehan1me.core.platform.readBytesAtPath

// 阶段一⑧：iOS 裁剪管线（Skia 解码/裁剪/落盘）已就绪，读产物走 NSData。
// 图片*选择器*（ photograph 库 / UIDocumentPicker）仍待接入——接入后此链路即可用。
internal actual fun readFileBytes(path: String): ByteArray? = readBytesAtPath(path)
