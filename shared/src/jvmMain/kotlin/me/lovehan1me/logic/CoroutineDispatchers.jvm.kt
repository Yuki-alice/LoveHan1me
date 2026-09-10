package me.lovehan1me.logic

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
