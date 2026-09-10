package lovehan1me.core.platform

/**
 * 随机 UUID（P4b：LocalListRepository.createPlaylist 生成本地清单 code 用）。
 *  - jvmMain（android+desktop）：java.util.UUID
 *  - iosMain：NSUUID
 */
internal expect fun randomUUIDString(): String
