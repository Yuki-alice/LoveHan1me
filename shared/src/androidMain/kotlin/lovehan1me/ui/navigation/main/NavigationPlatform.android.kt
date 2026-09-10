package lovehan1me.ui.navigation.main


/**
 * P6d-4F：Android 侧由 :app 启动时注册（MainActivity.mainBackStack），
 * 与 PlatformStores 的 provider 注册制同型。
 */
@Volatile private var artistSearchNavigator: ((String) -> Unit)? = null

fun registerArtistSearchNavigator(navigator: (query: String) -> Unit) {
    artistSearchNavigator = navigator
}

actual fun navigateToArtistSearch(query: String) {
    artistSearchNavigator?.invoke(query)
}
