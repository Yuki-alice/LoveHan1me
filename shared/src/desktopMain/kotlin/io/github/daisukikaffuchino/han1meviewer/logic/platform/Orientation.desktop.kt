package io.github.daisukikaffuchino.han1meviewer.logic.platform

import androidx.compose.runtime.Composable

// P6d-4F：桌面窗口为宽屏形态，横版 banner 布局更合适；逐帧窗口宽高比判定随 P7
actual @Composable
fun isLandscapeOrientation(): Boolean = true
