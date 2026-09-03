package io.github.daisukikaffuchino.han1meviewer

actual fun platformName(): String =
    "Desktop JVM ${System.getProperty("java.version")} / ${System.getProperty("os.name")}"
