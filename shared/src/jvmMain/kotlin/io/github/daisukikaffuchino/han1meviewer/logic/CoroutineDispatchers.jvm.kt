package io.github.daisukikaffuchino.han1meviewer.logic

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
