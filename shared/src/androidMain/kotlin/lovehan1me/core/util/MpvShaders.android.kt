package lovehan1me.core.util

import lovehan1me.data.database.dao.Han1meDatabaseContext
import java.io.File

internal actual suspend fun mpvShaderTargetDir(): String? =
    File(Han1meDatabaseContext.appContext.filesDir, "shaders").absolutePath
