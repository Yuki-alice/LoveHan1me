package lovehan1me.core.util

import lovehan1me.data.database.dao.Han1meDatabaseContext

internal actual fun readComposeFileSync(path: String): ByteArray? = runCatching {
    Han1meDatabaseContext.appContext.assets
        .open("$CMP_COMPOSE_RESOURCE_DIR$path")
        .use { it.readBytes() }
}.getOrNull()
