package lovehan1me.core.domain.state

/**
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2022/06/08 008 22:20
 */
sealed class WebsiteState<out T> {
    data class Success<out T>(val info: T) : WebsiteState<T>()
    data object Loading : WebsiteState<Nothing>()
    data class Error(val throwable: Throwable) : WebsiteState<Nothing>()
}
