package me.lovehan1me.logic

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

@OptIn(ExperimentalForeignApi::class)
internal actual fun currentEpochMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
