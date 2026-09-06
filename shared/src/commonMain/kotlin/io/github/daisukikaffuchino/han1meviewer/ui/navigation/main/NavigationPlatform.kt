package io.github.daisukikaffuchino.han1meviewer.ui.navigation.main

/**
 * P6d-4F：跨平台导航动作（原 :app 经 MainActivity.mainBackStack 直调）。
 * Android：经 CurrentActivityHolder 取 MainActivity 入栈；
 * 桌面/iOS：P6d-5 导航装配时接入统一 backStack，当前 no-op。
 */
expect fun navigateToArtistSearch(query: String)
