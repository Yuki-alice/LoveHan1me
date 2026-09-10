package lovehan1me.core.platform

actual fun platformName(): String =
    "Desktop JVM ${System.getProperty("java.version")} / ${System.getProperty("os.name")}"
