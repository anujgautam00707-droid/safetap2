package com.safetap.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.safetap.app.data.auth.AuthRepository
import com.safetap.app.di.AppContainer
import com.safetap.app.domain.sos.services.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val userEmail: String = "",
    val hasLocationPermission: Boolean = false,
    val hasFineLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val hasSmsPermission: Boolean = false,
    val hasCallPhonePermission: Boolean = false,
    val isSigningOut: Boolean = false,
    val showSignOutDialog: Boolean = false
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val permissionChecker: PermissionChecker = AppContainer.permissionChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            userEmail = authRepository.currentUser?.email.orEmpty(),
            hasLocationPermission = permissionChecker.hasLocationPermission(),
            hasFineLocationPermission = permissionChecker.hasFineLocationPermission(),
            hasNotificationPermission = permissionChecker.hasNotificationPermission(),
            hasSmsPermission = permissionChecker.hasSmsPermission(),
            hasCallPhonePermission = permissionChecker.hasCallPhonePermission()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        val email = authRepository.currentUser?.email.orEmpty()
        _uiState.update {
            it.copy(
                userEmail = email,
                hasLocationPermission = permissionChecker.hasLocationPermission(),
                hasFineLocationPermission = permissionChecker.hasFineLocationPermission(),
                hasNotificationPermission = permissionChecker.hasNotificationPermission(),
                hasSmsPermission = permissionChecker.hasSmsPermission(),
                hasCallPhonePermission = permissionChecker.hasCallPhonePermission()
            )
        }
    }

    fun onShowSignOutDialog(show: Boolean) {
        _uiState.update { it.copy(showSignOutDialog = show) }
    }

    fun onSignOut(onLoggedOut: () -> Unit) {
        _uiState.update { it.copy(isSigningOut = true, showSignOutDialog = false) }
        authRepository.signOut()
        onLoggedOut()
    }
}

