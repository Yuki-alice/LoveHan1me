package lovehan1me.core.platform

/**
 * epoch 毫秒时间戳（P4b：LocalListRepository 等 commonMain 用；commonMain 无 java.lang.System）。
 *  - jvmMain（android+desktop）：System.currentTimeMillis()
 *  - iosMain：NSDate.timeIntervalSince1970 * 1000
 */
internal expect fun currentEpochMillis(): Long
