package io.github.daisukikaffuchino.han1meviewer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.NetworkRepo
import io.github.daisukikaffuchino.han1meviewer.logic.exception.NotLoggedInException
import io.github.daisukikaffuchino.han1meviewer.logic.model.UserAccount
import io.github.daisukikaffuchino.han1meviewer.logic.model.UserAccountAction
import io.github.daisukikaffuchino.han1meviewer.logic.model.UserAccountActionEvent
import io.github.daisukikaffuchino.han1meviewer.logic.model.UserAccountSubmittingState
import io.github.daisukikaffuchino.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserAccountViewModel : ViewModel() {

    private val _accountState = MutableStateFlow<WebsiteState<UserAccount>>(WebsiteState.Loading)
    val accountState = _accountState.asStateFlow()

    private val _actionFlow = MutableSharedFlow<UserAccountActionEvent>()
    val actionFlow = _actionFlow.asSharedFlow()

    private val _submittingState = MutableStateFlow(UserAccountSubmittingState.Idle)
    val submittingState = _submittingState.asStateFlow()

    fun loadAccount(forceReload: Boolean = false) {
        if (!forceReload && _accountState.value is WebsiteState.Success) return
        val userId = SettingsRepository.savedUserId
        if (userId.isBlank()) {
            _accountState.value = WebsiteState.Error(
                NotLoggedInException()
            )
            return
        }
        viewModelScope.launch {
            _accountState.value = WebsiteState.Loading
            NetworkRepo.getUserAccountPage(userId).collect { state ->
                _accountState.value = state
            }
        }
    }

    fun updateProfile(name: String, email: String) {
        val account = (_accountState.value as? WebsiteState.Success)?.info ?: return
        if (_submittingState.value != UserAccountSubmittingState.Idle) return
        viewModelScope.launch {
            _submittingState.value = UserAccountSubmittingState.UpdatingProfile
            _actionFlow.emit(UserAccountActionEvent(UserAccountAction.ProfileUpdated, WebsiteState.Loading))
            NetworkRepo.updateUserAccountProfile(
                userId = account.userId,
                csrfToken = account.csrfToken,
                name = name,
                email = email,
            ).collect { state ->
                _actionFlow.emit(UserAccountActionEvent(UserAccountAction.ProfileUpdated, state))
                if (state is WebsiteState.Success) {
                    loadAccount(forceReload = true)
                }
                if (state !is WebsiteState.Loading) {
                    _submittingState.value = UserAccountSubmittingState.Idle
                }
            }
        }
    }

    fun updatePassword(oldPassword: String, newPassword: String, newPasswordConfirm: String) {
        val account = (_accountState.value as? WebsiteState.Success)?.info ?: return
        if (_submittingState.value != UserAccountSubmittingState.Idle) return
        viewModelScope.launch {
            _submittingState.value = UserAccountSubmittingState.UpdatingPassword
            _actionFlow.emit(UserAccountActionEvent(UserAccountAction.PasswordUpdated, WebsiteState.Loading))
            NetworkRepo.updateUserAccountPassword(
                userId = account.userId,
                csrfToken = account.csrfToken,
                oldPassword = oldPassword,
                newPassword = newPassword,
                newPasswordConfirm = newPasswordConfirm,
            ).collect { state ->
                _actionFlow.emit(UserAccountActionEvent(UserAccountAction.PasswordUpdated, state))
                if (state !is WebsiteState.Loading) {
                    _submittingState.value = UserAccountSubmittingState.Idle
                }
            }
        }
    }

    // P6c：VM 下沉 commonMain，File 参数改 (bytes, name)，:app 调用方拆解
    fun updateAvatar(avatarBytes: ByteArray, avatarName: String) {
        val account = (_accountState.value as? WebsiteState.Success)?.info ?: return
        if (_submittingState.value != UserAccountSubmittingState.Idle) return
        viewModelScope.launch {
            _submittingState.value = UserAccountSubmittingState.UpdatingAvatar
            _actionFlow.emit(UserAccountActionEvent(UserAccountAction.AvatarUpdated, WebsiteState.Loading))
            NetworkRepo.updateUserAccountAvatar(
                userId = account.userId,
                csrfToken = account.csrfToken,
                avatarBytes = avatarBytes,
                avatarName = avatarName,
            ).collect { state ->
                _actionFlow.emit(UserAccountActionEvent(UserAccountAction.AvatarUpdated, state))
                if (state is WebsiteState.Success) {
                    loadAccount(forceReload = true)
                }
                if (state !is WebsiteState.Loading) {
                    _submittingState.value = UserAccountSubmittingState.Idle
                }
            }
        }
    }
}
