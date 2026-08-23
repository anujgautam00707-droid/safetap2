package com.safetap.app.ui.screens.contacts

import androidx.lifecycle.ViewModel
import com.safetap.app.di.AppContainer
import com.safetap.app.domain.sos.services.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrustedContactsUiState(
    val contacts: List<String> = emptyList(),
    val isSmsPermissionGranted: Boolean = false,
    val showSmsRationale: Boolean = false,
    val showSmsSettingsRecovery: Boolean = false
)

class TrustedContactsViewModel(
    private val permissionChecker: PermissionChecker = AppContainer.permissionChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TrustedContactsUiState(
            isSmsPermissionGranted = permissionChecker.hasSmsPermission()
        )
    )
    val uiState: StateFlow<TrustedContactsUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionState()
    }

    fun refreshPermissionState() {
        val granted = permissionChecker.hasSmsPermission()
        _uiState.value = _uiState.value.copy(
            isSmsPermissionGranted = granted
        )
    }

    fun onAddContactClicked() {
        refreshPermissionState()
        if (!_uiState.value.isSmsPermissionGranted) {
            _uiState.value = _uiState.value.copy(showSmsRationale = true)
        }
    }

    fun onSmsPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSmsPermissionGranted = granted,
            showSmsRationale = false,
            showSmsSettingsRecovery = !granted
        )
    }

    fun dismissSmsRationale() {
        _uiState.value = _uiState.value.copy(showSmsRationale = false)
    }
}

