package lovehan1me.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lovehan1me.feature.library.FavSubViewModel
import lovehan1me.feature.library.LocalFavSubViewModel
import lovehan1me.feature.library.LocalWatchLaterSubViewModel
import lovehan1me.feature.library.WatchLaterSubViewModel

/**
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2022/07/04 004 22:46
 */
class MyListViewModel : ViewModel() {

    val watchLater = WatchLaterSubViewModel(viewModelScope)
    val fav = FavSubViewModel(viewModelScope)
    val localWatchLater = LocalWatchLaterSubViewModel(viewModelScope)
    val localFav = LocalFavSubViewModel(viewModelScope)
}
