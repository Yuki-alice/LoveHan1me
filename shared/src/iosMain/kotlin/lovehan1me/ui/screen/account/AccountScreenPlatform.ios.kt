package lovehan1me.ui.screen.account

// iOS 不产生裁剪结果（onPickAvatarImage 为 null），此路径不可达。
// 若未来接 iOS 文件选择，改用 NSData 读文件。
internal actual fun readFileBytes(path: String): ByteArray? = null
