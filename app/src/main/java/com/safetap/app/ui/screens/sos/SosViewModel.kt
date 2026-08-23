package com.safetap.app.ui.screens.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safetap.app.data.auth.AuthRepository
import com.safetap.app.di.AppContainer
import com.safetap.app.domain.sos.SosCoordinator
import com.safetap.app.domain.sos.model.SosError
import com.safetap.app.domain.sos.services.PermissionChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SosViewModel(
    private val sosCoordinator: SosCoordinator,
    private val authRepository: AuthRepository? = null,
    private val permissionChecker: PermissionChecker = AppContainer.permissionChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow<SosUiState>(SosUiState.Idle)
    val uiState: StateFlow<SosUiState> = _uiState.asStateFlow()

    private val _batteryPercentage = MutableStateFlow<Int?>(null)
    val batteryPercentage: StateFlow<Int?> = _batteryPercentage.asStateFlow()

    private val _isLocationGranted = MutableStateFlow(permissionChecker.hasLocationPermission())
    val isLocationGranted: StateFlow<Boolean> = _isLocationGranted.asStateFlow()

    private val _isLocationPrecise = MutableStateFlow(permissionChecker.hasFineLocationPermission())
    val isLocationPrecise: StateFlow<Boolean> = _isLocationPrecise.asStateFlow()

    private val _isNotificationGranted = MutableStateFlow(permissionChecker.hasNotificationPermission())
    val isNotificationGranted: StateFlow<Boolean> = _isNotificationGranted.asStateFlow()

    private val _showLocationRationale = MutableStateFlow(false)
    val showLocationRationale: StateFlow<Boolean> = _showLocationRationale.asStateFlow()

    private val _showLocationSettingsRecovery = MutableStateFlow(false)
    val showLocationSettingsRecovery: StateFlow<Boolean> = _showLocationSettingsRecovery.asStateFlow()

    private var countdownJob: Job? = null

    init {
        refreshBatteryPercentage()
        refreshPermissionStates()
    }

    fun refreshPermissionStates() {
        _isLocationGranted.value = permissionChecker.hasLocationPermission()
        _isLocationPrecise.value = permissionChecker.hasFineLocationPermission()
        _isNotificationGranted.value = permissionChecker.hasNotificationPermission()
    }

    /**
     * Reads the current battery percentage through the SOS domain layer.
     */
    fun refreshBatteryPercentage() {
        viewModelScope.launch {
            runCatching {
                sosCoordinator.getBatteryPercentage()
            }.getOrNull()
                ?.takeIf { percentage -> percentage in 0..100 }
                ?.let { percentage ->
                    _batteryPercentage.value = percentage
                }
        }
    }

    /**
     * Called when the user requests to start SOS.
     * If location permission is not yet granted, requests rationale first.
     */
    fun requestStartSos(onRequirePermissionLaunch: () -> Unit) {
        if (_uiState.value is SosUiState.Countdown || _uiState.value is SosUiState.Active) {
            return
        }

        refreshPermissionStates()
        if (!_isLocationGranted.value) {
            _showLocationRationale.value = true
        } else {
            startCountdown()
        }
    }

    fun dismissLocationRationale() {
        _showLocationRationale.value = false
    }

    fun onLocationPermissionResult(hasFine: Boolean, hasCoarse: Boolean) {
        _isLocationGranted.value = hasFine || hasCoarse
        _isLocationPrecise.value = hasFine
        _showLocationRationale.value = false

        if (hasFine || hasCoarse) {
            _showLocationSettingsRecovery.value = false
            startCountdown()
        } else {
            _showLocationSettingsRecovery.value = true
        }
    }

    fun onNotificationPermissionResult(isGranted: Boolean) {
        _isNotificationGranted.value = isGranted
    }

    /**
     * Starts the countdown and dispatches SOS.
     */
    fun startCountdown() {
        if (_uiState.value is SosUiState.Countdown || _uiState.value is SosUiState.Active) {
            return
        }

        refreshBatteryPercentage()
        countdownJob?.cancel()

        countdownJob = viewModelScope.launch {
            _uiState.value = SosUiState.Countdown(5)

            val countdownResult =
                sosCoordinator.runCountdown(5) { secondsRemaining ->
                    _uiState.value = SosUiState.Countdown(secondsRemaining)
                }

            if (countdownResult.isSuccess) {
                dispatchEmergencySos()
            } else {
                val error = countdownResult.exceptionOrNull() as? SosError
                when {
                    error is SosError.Cancelled -> {
                        _uiState.value = SosUiState.Cancelled
                    }
                    error != null -> {
                        _uiState.value = SosUiState.Error(
                            error = error,
                            message = error.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Compatibility hook for existing callers.
     */
    fun startSos() {
        startCountdown()
    }

    /**
     * Cancels any active countdown or SOS alert.
     */
    fun cancelSos() {
        countdownJob?.cancel()
        countdownJob = null

        viewModelScope.launch {
            sosCoordinator.cancelSos()
            refreshBatteryPercentage()
            _uiState.value = SosUiState.Cancelled
        }
    }

    /**
     * Skips the countdown and dispatches the emergency SOS immediately.
     */
    fun triggerImmediately() {
        countdownJob?.cancel()
        countdownJob = null
        refreshBatteryPercentage()

        viewModelScope.launch {
            dispatchEmergencySos()
        }
    }

    /**
     * Returns the SOS screen to its idle state.
     */
    fun resetSos() {
        countdownJob?.cancel()
        countdownJob = null

        viewModelScope.launch {
            sosCoordinator.cancelSos()
            refreshBatteryPercentage()
            _uiState.value = SosUiState.Idle
        }
    }

    /**
     * Opens the phone dialer with the designated emergency number.
     */
    fun openEmergencyDialer(
        emergencyNumber: String = "911"
    ): Result<Unit> {
        val result = sosCoordinator.openEmergencyDialer(emergencyNumber)

        if (result.isFailure) {
            val error = result.exceptionOrNull() as? SosError
                ?: SosError.NoDialerApp("Failed to open emergency dialer.")

            _uiState.value = SosUiState.Error(
                error = error,
                message = error.message
            )
        }

        return result
    }

    /**
     * Compatibility hook for existing callers.
     */
    fun onSosPressed() {
        triggerImmediately()
    }

    private suspend fun dispatchEmergencySos() {
        _uiState.value = SosUiState.CollectingEmergencyData

        val userId = authRepository?.currentUser?.uid ?: "user_placeholder"
        val result = sosCoordinator.triggerSos(userId = userId)

        if (result.isSuccess) {
            val emergencyData = result.getOrThrow()
            _batteryPercentage.value = emergencyData.batteryPercentage
            _uiState.value = SosUiState.Active(emergencyData)
        } else {
            val error = result.exceptionOrNull() as? SosError
                ?: SosError.UnexpectedError(result.exceptionOrNull())

            _uiState.value = SosUiState.Error(
                error = error,
                message = error.message
            )
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}