package io.github.daisukikaffuchino.utils

import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabaseContext

internal actual fun readComposeFileSync(path: String): ByteArray? = runCatching {
    Han1meDatabaseContext.appContext.assets
        .open("$CMP_COMPOSE_RESOURCE_DIR$path")
        .use { it.readBytes() }
}.getOrNull()
