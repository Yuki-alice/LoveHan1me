package lovehan1me.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lovehan1me.ui.viewmodel.mylist.FavSubViewModel
import lovehan1me.ui.viewmodel.mylist.LocalFavSubViewModel
import lovehan1me.ui.viewmodel.mylist.LocalWatchLaterSubViewModel
import lovehan1me.ui.viewmodel.mylist.WatchLaterSubViewModel

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/07/04 004 22:46
 */
class MyListViewModel : ViewModel() {

    val watchLater = WatchLaterSubViewModel(viewModelScope)
    val fav = FavSubViewModel(viewModelScope)
    val localWatchLater = LocalWatchLaterSubViewModel(viewModelScope)
    val localFav = LocalFavSubViewModel(viewModelScope)
}
