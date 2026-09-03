package io.github.daisukikaffuchino.han1meviewer

/**
 * 平台标识。平台差异统一收敛到 expect/actual，业务与 UI 层不感知具体平台。
 */
expect fun platformName(): String
