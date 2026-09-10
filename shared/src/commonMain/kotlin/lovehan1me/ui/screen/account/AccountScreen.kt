package lovehan1me.ui.screen.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.Res
import lovehan1me.password_not_match
import lovehan1me.username
import lovehan1me.updating
import lovehan1me.update_profile
import lovehan1me.old_password
import lovehan1me.new_password
import lovehan1me.my_account
import lovehan1me.modify_success
import lovehan1me.modify_failed
import lovehan1me.logout
import lovehan1me.load_failed_retry
import lovehan1me.forgot_password
import lovehan1me.email
import lovehan1me.edit_profile
import lovehan1me.confirm_new_password
import lovehan1me.changing
import lovehan1me.change_password
import lovehan1me.change_avatar
import lovehan1me.account_stats_summary
import lovehan1me.ic_visibility_off
import lovehan1me.ic_visibility
import lovehan1me.ic_person
import lovehan1me.ic_mail
import lovehan1me.ic_lock
import lovehan1me.ic_info
import lovehan1me.ic_exit_to_app
import lovehan1me.ic_edit
import lovehan1me.h_chan_default_avatar
import lovehan1me.logic.model.UserAccount
import lovehan1me.logic.model.UserAccountAction
import lovehan1me.logic.model.UserAccountSubmittingState
import lovehan1me.logic.state.WebsiteState
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.PageContent
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.ui.component.content.ErrorContent
import lovehan1me.ui.screen.rememberRandomLoadingHint
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.viewmodel.UserAccountViewModel
import lovehan1me.core.util.SonnerToast
import lovehan1me.ui.component.HapticButton as Button
import lovehan1me.ui.component.HapticTextButton as TextButton

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountScreen(
    viewModel: UserAccountViewModel,
    onBack: () -> Unit,
    onOpenAvatarCrop: (String) -> Unit,
    /** 头像图片选择入口；null 表示平台不支持（UI 隐藏上传入口）。 */
    onPickAvatarImage: (() -> Unit)?,
    pendingAvatarCropResult: String?,
    onAvatarCropResultConsumed: () -> Unit,
    onRefreshHome: () -> Unit,
    onLogout: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val state by viewModel.accountState.collectAsStateWithLifecycle()
    val submittingState by viewModel.submittingState.collectAsStateWithLifecycle()
    val modifyFailed = stringResource(Res.string.modify_failed)
    val modifySuccess = stringResource(Res.string.modify_success)
    LaunchedEffect(Unit) {
        viewModel.loadAccount()
    }

    LaunchedEffect(pendingAvatarCropResult) {
        val filePath = pendingAvatarCropResult ?: return@LaunchedEffect
        // P6c：VM 下沉后 updateAvatar(bytes, name)，File 在调用方拆解（读取失败则消费掉结果并忽略）
        val bytes = readFileBytes(filePath)
        if (bytes != null) {
            viewModel.updateAvatar(bytes, filePath.substringAfterLast('/'))
        }
        onAvatarCropResultConsumed()
    }

    LaunchedEffect(viewModel) {

        viewModel.actionFlow.collect { event ->
            when (val eventState = event.state) {
                is WebsiteState.Error -> {
                    SonnerToast.error(eventState.throwable.message ?: modifyFailed)
                }

                is WebsiteState.Success -> {
                    when (event.action) {
                        UserAccountAction.ProfileUpdated,
                        UserAccountAction.AvatarUpdated -> onRefreshHome()

                        UserAccountAction.PasswordUpdated -> Unit
                    }
                    val message = when (event.action) {
                        UserAccountAction.ProfileUpdated -> modifySuccess
                        UserAccountAction.PasswordUpdated -> modifySuccess
                        UserAccountAction.AvatarUpdated -> modifySuccess
                    }
                    SonnerToast.error(message)
                }

                WebsiteState.Loading -> Unit
            }
        }
    }

    HanimeScaffold(
        title = stringResource(Res.string.my_account),
        onBack = onBack,
    ) { paddingValues ->
        val loadingHint = rememberRandomLoadingHint()
        PageContent(
            isLoading = state is WebsiteState.Loading,
            isError = state is WebsiteState.Error,
            isEmpty = state !is WebsiteState.Success,
            onRetry = { viewModel.loadAccount(forceReload = true) },
            loadingMessage = loadingHint,
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorContent(
                        title = stringResource(Res.string.load_failed_retry),
                        onRetry = { viewModel.loadAccount(forceReload = true) },
                    )
                }
            },
        ) {
            val account = (state as? WebsiteState.Success)?.info ?: return@PageContent
            AccountContent(
                account = account,
                submittingState = submittingState,
                contentPadding = paddingValues,
                onUpdateProfile = viewModel::updateProfile,
                onUpdatePassword = viewModel::updatePassword,
                onPickAvatar = { onPickAvatarImage?.invoke() },
                onLogout = onLogout,
                onOpenPasswordReset = {
                    uriHandler.openUri("${lovehan1me.HANIME_BASE_URL}password/reset")
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountContent(
    account: UserAccount,
    submittingState: UserAccountSubmittingState,
    contentPadding: PaddingValues,
    onUpdateProfile: (String, String) -> Unit,
    onUpdatePassword: (String, String, String) -> Unit,
    onPickAvatar: () -> Unit,
    onLogout: () -> Unit,
    onOpenPasswordReset: () -> Unit,
) {
    val hapticFeedback = rememberHapticFeedback()
    val scrollState = rememberScrollState()

    var name by rememberSaveable(account.username) { mutableStateOf(account.username) }
    var email by rememberSaveable(account.email) { mutableStateOf(account.email) }

    var oldPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var newPasswordConfirm by rememberSaveable { mutableStateOf("") }

    var oldPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var newPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    val isUpdatingProfile = submittingState == UserAccountSubmittingState.UpdatingProfile
    val isUpdatingPassword = submittingState == UserAccountSubmittingState.UpdatingPassword
    val isUpdatingAvatar = submittingState == UserAccountSubmittingState.UpdatingAvatar
    // P6d-3-C3：回调内固定串预解析
    val passwordNotMatchText = stringResource(Res.string.password_not_match)
    val isSubmitting = submittingState != UserAccountSubmittingState.Idle

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(top = HanimeDefaults.Spacing.itemVertical),
        verticalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.itemVertical),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val defaultPlaceholder = painterResource(Res.drawable.h_chan_default_avatar)
                    HanimeAsyncImage(
                        model = account.avatarUrl,
                        contentDescription = account.username,
                        modifier = Modifier
                            .size(108.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentScale = ContentScale.Crop,
                        placeholder = defaultPlaceholder,
                        error = defaultPlaceholder,
                        fallback = defaultPlaceholder,
                    )

                    SmallFloatingActionButton(
                        onClick = {
                            hapticFeedback()
                            onPickAvatar()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                    ) {
                        if (isUpdatingAvatar) {
                            LoadingIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Icon(
                                painter = painterResource(Res.drawable.ic_edit),
                                contentDescription = stringResource(Res.string.change_avatar),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = account.username,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = "@${account.userId}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(Res.string.account_stats_summary,
                        account.subscriberCount,
                        account.videoCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val joinedLabel = account.joinedLabel
                if (!joinedLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = joinedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.edit_profile),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.username)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_person),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(Res.string.email)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_mail),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                Button(
                    onClick = { onUpdateProfile(name.trim(), email.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting && name.isNotBlank() && email.isNotBlank(),
                ) {
                    if (isUpdatingProfile) {
                        LoadingIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 8.dp)
                        )
                        Text(stringResource(Res.string.updating))
                    } else {
                        Text(stringResource(Res.string.update_profile))
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.change_password),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text(stringResource(Res.string.old_password)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_lock),
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { oldPasswordVisible = !oldPasswordVisible }) {
                            Icon(
                                painter = if (oldPasswordVisible) painterResource(Res.drawable.ic_visibility) else painterResource(
                                    Res.drawable.ic_visibility_off
                                ),
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (oldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(Res.string.new_password)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_lock),
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                painter = if (oldPasswordVisible) painterResource(Res.drawable.ic_visibility) else painterResource(
                                    Res.drawable.ic_visibility_off
                                ),
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                OutlinedTextField(
                    value = newPasswordConfirm,
                    onValueChange = { newPasswordConfirm = it },
                    label = { Text(stringResource(Res.string.confirm_new_password)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_lock),
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                painter = if (oldPasswordVisible) painterResource(Res.drawable.ic_visibility) else painterResource(
                                    Res.drawable.ic_visibility_off
                                ),
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                TextButton(
                    onClick = onOpenPasswordReset,
                    modifier = Modifier.align(Alignment.Start),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_info),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.forgot_password),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        if (newPassword != newPasswordConfirm) {
                            SonnerToast.warning(passwordNotMatchText)
                        } else {
                            onUpdatePassword(oldPassword, newPassword, newPasswordConfirm)
                            oldPassword = ""
                            newPassword = ""
                            newPasswordConfirm = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting && oldPassword.isNotBlank() && newPassword.isNotBlank() && newPasswordConfirm.isNotBlank(),
                ) {
                    if (isUpdatingPassword) {
                        LoadingIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 8.dp)
                        )
                        Text(stringResource(Res.string.changing))
                    } else {
                        Text(stringResource(Res.string.change_password))
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.errorContainer),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_exit_to_app),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(Res.string.logout), fontWeight = FontWeight.Medium)
        }
    }
}
