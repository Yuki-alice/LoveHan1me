package me.lovehan1me.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.lovehan1me.ui.viewmodel.mylist.FavSubViewModel
import me.lovehan1me.ui.viewmodel.mylist.LocalFavSubViewModel
import me.lovehan1me.ui.viewmodel.mylist.LocalWatchLaterSubViewModel
import me.lovehan1me.ui.viewmodel.mylist.WatchLaterSubViewModel

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
