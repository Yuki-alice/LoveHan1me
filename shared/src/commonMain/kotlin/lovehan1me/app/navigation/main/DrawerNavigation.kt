package lovehan1me.app.navigation.main

/**
 * M2：自 `:app` `MainNavigationActions.kt` 下沉的纯函数部分（同名，`:app` 侧删除；
 * `handleMainIntent(Intent)` 仍留 `:app`）。
 */
private val loginRequiredDrawerItems = setOf(
    MainDrawerDestination.Subscription,
)

fun TopLevelBackStack<HanimeScreen>.navigateDrawerDestination(
    destination: MainDrawerDestination,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
): Boolean {
    if (destination in loginRequiredDrawerItems && !isLoggedIn) {
        onRequireLogin()
        return false
    }

    addTopLevel(destination.route)
    return true
}
