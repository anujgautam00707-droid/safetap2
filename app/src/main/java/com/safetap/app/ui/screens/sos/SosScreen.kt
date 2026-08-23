package com.safetap.app.ui.screens.sos

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safetap.app.di.RakshaViewModelFactory
import com.safetap.app.ui.components.PermissionRationaleDialog
import com.safetap.app.ui.theme.EmergencyRed
import com.safetap.app.ui.theme.EmergencyRedContainer
import com.safetap.app.ui.theme.EmergencyRedDark
import com.safetap.app.ui.theme.EmergencyRedLight
import com.safetap.app.ui.theme.EmergencyWhite
import com.safetap.app.ui.theme.SafeGreen
import com.safetap.app.ui.theme.SafeGreenContainer
import com.safetap.app.ui.theme.WarningAmber
import com.safetap.app.ui.theme.WarningAmberContainer

@Composable
fun SosScreen(
    viewModel: SosViewModel = viewModel(factory = RakshaViewModelFactory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val batteryPercentage by viewModel.batteryPercentage.collectAsStateWithLifecycle()
    val isLocationGranted by viewModel.isLocationGranted.collectAsStateWithLifecycle()
    val isLocationPrecise by viewModel.isLocationPrecise.collectAsStateWithLifecycle()
    val isNotificationGranted by viewModel.isNotificationGranted.collectAsStateWithLifecycle()
    val showLocationRationale by viewModel.showLocationRationale.collectAsStateWithLifecycle()
    val showLocationSettingsRecovery by viewModel.showLocationSettingsRecovery.collectAsStateWithLifecycle()

    // Permission Activity Result Launchers
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(hasFine = fine, hasCoarse = coarse)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onNotificationPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionStates()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Permission Rationale Dialog for Location
    if (showLocationRationale) {
        PermissionRationaleDialog(
            title = "Location Access for SOS",
            description = "SafeTap requires location access to share your GPS position with emergency responders and trusted contacts during an SOS event.\n\nYou can choose either Precise or Approximate location.",
            icon = Icons.Filled.GpsFixed,
            iconTint = SafeGreen,
            primaryButtonText = "Grant Location",
            onConfirm = {
                viewModel.dismissLocationRationale()
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onDismiss = {
                viewModel.dismissLocationRationale()
                viewModel.startCountdown()
            },
            dismissButtonText = "Continue Without GPS",
            showSettingsOption = showLocationSettingsRecovery,
            onOpenSettings = {
                viewModel.dismissLocationRationale()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        )
    }

    val isCheckingPermissions =
        uiState == SosUiState.CheckingPermissions

    val isCountdown =
        uiState is SosUiState.Countdown

    val isEmergencyActive =
        uiState == SosUiState.CollectingEmergencyData ||
                uiState is SosUiState.ReadyToSend ||
                uiState is SosUiState.Active

    val usesCountdownVisuals =
        isCheckingPermissions || isCountdown

    val isSosInProgress =
        usesCountdownVisuals || isEmergencyActive

    val countdownSeconds =
        (uiState as? SosUiState.Countdown)?.secondsRemaining ?: 5

    val infiniteTransition =
        rememberInfiniteTransition(label = "SosEmergencyPulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "SosScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "SosAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = when {
                        isEmergencyActive -> listOf(
                            Color(0xFF3B0000),
                            Color(0xFF1E0505),
                            MaterialTheme.colorScheme.background
                        )

                        usesCountdownVisuals -> listOf(
                            Color(0xFF2E1005),
                            Color(0xFF1E0A05),
                            MaterialTheme.colorScheme.background
                        )

                        else -> listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = 0.3f
                            )
                        )
                    }
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 20.dp,
                vertical = 16.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Emergency SOS",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = when (val state = uiState) {
                        SosUiState.Idle ->
                            "Tap button to start 5-second countdown"

                        SosUiState.CheckingPermissions ->
                            "Checking emergency permissions"

                        is SosUiState.Countdown ->
                            "Dispatching SOS in ${state.secondsRemaining}s"

                        SosUiState.CollectingEmergencyData ->
                            "Preparing emergency broadcast"

                        is SosUiState.ReadyToSend ->
                            "Emergency data ready"

                        is SosUiState.Active ->
                            "EMERGENCY BROADCAST ACTIVE"

                        SosUiState.Cancelled ->
                            "SOS alert cancelled"

                        is SosUiState.Error ->
                            state.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (uiState) {
                        SosUiState.CheckingPermissions,
                        is SosUiState.Countdown -> WarningAmber

                        SosUiState.CollectingEmergencyData,
                        is SosUiState.ReadyToSend,
                        is SosUiState.Active,
                        is SosUiState.Error -> EmergencyRedLight

                        SosUiState.Idle,
                        SosUiState.Cancelled ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when (uiState) {
                            SosUiState.CheckingPermissions,
                            is SosUiState.Countdown -> WarningAmber

                            SosUiState.CollectingEmergencyData,
                            is SosUiState.ReadyToSend,
                            is SosUiState.Active,
                            is SosUiState.Error -> EmergencyRed

                            SosUiState.Idle,
                            SosUiState.Cancelled -> SafeGreen
                        }
                    )
                    .padding(
                        horizontal = 10.dp,
                        vertical = 5.dp
                    )
            ) {
                Text(
                    text = when (uiState) {
                        SosUiState.Idle -> "ARMED"
                        SosUiState.CheckingPermissions -> "CHECKING"
                        is SosUiState.Countdown -> "COUNTING"
                        SosUiState.CollectingEmergencyData -> "PREPARING"
                        is SosUiState.ReadyToSend -> "READY"
                        is SosUiState.Active -> "ALERTING"
                        SosUiState.Cancelled -> "CANCELLED"
                        is SosUiState.Error -> "ERROR"
                    },
                    color = EmergencyWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            if (isSosInProgress) {
                Box(
                    modifier = Modifier
                        .size(230.dp)
                        .scale(pulseScale)
                        .background(
                            color = (
                                    if (isEmergencyActive) {
                                        EmergencyRed
                                    } else {
                                        WarningAmber
                                    }
                                    ).copy(alpha = pulseAlpha),
                            shape = CircleShape
                        )
                )
            }

            if (usesCountdownVisuals) {
                val animatedProgress by animateFloatAsState(
                    targetValue = countdownSeconds / 5f,
                    animationSpec = tween(
                        durationMillis = 900,
                        easing = LinearEasing
                    ),
                    label = "CountdownArc"
                )

                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(220.dp),
                    color = WarningAmber,
                    strokeWidth = 8.dp,
                    trackColor = WarningAmber.copy(alpha = 0.2f),
                    strokeCap =
                        ProgressIndicatorDefaults.CircularDeterminateStrokeCap
                )
            } else if (isEmergencyActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(220.dp),
                    color = EmergencyRedLight,
                    strokeWidth = 8.dp,
                    trackColor = EmergencyRedDark
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(175.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = CircleShape,
                        ambientColor = EmergencyRedDark,
                        spotColor = EmergencyRed
                    )
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = when {
                                isEmergencyActive -> listOf(
                                    EmergencyRedLight,
                                    EmergencyRed,
                                    EmergencyRedDark
                                )

                                usesCountdownVisuals -> listOf(
                                    Color(0xFFFFB74D),
                                    WarningAmber,
                                    Color(0xFFE65100)
                                )

                                else -> listOf(
                                    EmergencyRedLight,
                                    EmergencyRed,
                                    EmergencyRedDark
                                )
                            }
                        )
                    )
                    .clickable(
                        interactionSource =
                            remember { MutableInteractionSource() },
                        indication = ripple(
                            bounded = true,
                            color = EmergencyWhite
                        ),
                        onClick = {
                            when (uiState) {
                                SosUiState.Idle ->
                                    viewModel.requestStartSos {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }

                                is SosUiState.Countdown ->
                                    viewModel.triggerImmediately()

                                else -> Unit
                            }
                        }
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        usesCountdownVisuals -> {
                            Text(
                                text = "${countdownSeconds}s",
                                color = EmergencyWhite,
                                style =
                                    MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black
                            )

                            Text(
                                text = "TAP TO TRIGGER NOW",
                                color =
                                    EmergencyWhite.copy(alpha = 0.9f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        isEmergencyActive -> {
                            Icon(
                                imageVector =
                                    Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = EmergencyWhite,
                                modifier = Modifier.size(44.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "ACTIVE",
                                color = EmergencyWhite,
                                style =
                                    MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp
                            )
                        }

                        else -> {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = EmergencyWhite,
                                modifier = Modifier.size(44.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "SOS",
                                color = EmergencyWhite,
                                style =
                                    MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )

                            Text(
                                text = "START 5S TIMER",
                                color =
                                    EmergencyWhite.copy(alpha = 0.85f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (uiState != SosUiState.Idle) {
            Button(
                onClick = {
                    when (uiState) {
                        SosUiState.Cancelled,
                        is SosUiState.Error ->
                            viewModel.resetSos()

                        else ->
                            viewModel.cancelSos()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        MaterialTheme.colorScheme.surface,
                    contentColor =
                        MaterialTheme.colorScheme.onSurface
                ),
                elevation =
                    ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = null,
                    tint = EmergencyRed,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = when (uiState) {
                        SosUiState.Cancelled -> "Reset SOS"
                        is SosUiState.Error -> "Dismiss Error"
                        else -> "Cancel Alert / I Am Safe"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        } else {
            OutlinedButton(
                onClick = {
                    viewModel.requestStartSos {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Test 5-Second SOS Countdown",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Emergency Notification Warning (if denied on API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = WarningAmberContainer.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(WarningAmber.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsOff,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SOS Notifications Disabled",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap to enable status alerts on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Dispatch Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isEmergencyActive) {
                        EmergencyRedContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                elevation =
                    CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEmergencyActive) {
                                    EmergencyRed
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isEmergencyActive) {
                                Icons.Filled.NotificationsActive
                            } else {
                                Icons.Outlined.Shield
                            },
                            contentDescription = null,
                            tint = if (isEmergencyActive) {
                                EmergencyWhite
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEmergencyActive) {
                                "Emergency Broadcast Active"
                            } else {
                                "Emergency Dispatch Standby"
                            },
                            style =
                                MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isEmergencyActive) {
                                EmergencyRed
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (isEmergencyActive) {
                                "Broadcasting emergency telemetry to contacts & cloud"
                            } else {
                                "Contacts will be alerted immediately on trigger"
                            },
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Dynamic Location Status Card
            val activeEmergency = (uiState as? SosUiState.Active)?.emergencyData
            val isLocationUnavailable = activeEmergency != null && activeEmergency.latitude == 0.0 && activeEmergency.longitude == 0.0
            val isLocationApprox = (activeEmergency != null && activeEmergency.isApproximateLocation) || (!isLocationPrecise && isLocationGranted)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isLocationGranted) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconBgColor = when {
                        !isLocationGranted || isLocationUnavailable -> EmergencyRedContainer
                        isLocationApprox -> WarningAmberContainer
                        else -> SafeGreenContainer
                    }
                    val iconTint = when {
                        !isLocationGranted || isLocationUnavailable -> EmergencyRed
                        isLocationApprox -> WarningAmber
                        else -> SafeGreen
                    }
                    val iconVector = when {
                        !isLocationGranted || isLocationUnavailable -> Icons.Filled.LocationOff
                        else -> Icons.Filled.GpsFixed
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(iconBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val titleText = when {
                                activeEmergency != null && isLocationUnavailable -> "Location Unavailable"
                                activeEmergency != null && isLocationApprox -> "Approximate Location Active"
                                activeEmergency != null -> "GPS Location Locked"
                                isLocationGranted && isLocationPrecise -> "GPS Location Ready"
                                isLocationGranted -> "Approximate Location Ready"
                                else -> "Location Disabled"
                            }

                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            val badgeText = when {
                                !isLocationGranted || isLocationUnavailable -> "NO GPS"
                                isLocationApprox -> "APPROXIMATE"
                                else -> "HIGH PRECISION"
                            }
                            val badgeBgColor = when {
                                !isLocationGranted || isLocationUnavailable -> EmergencyRedContainer
                                isLocationApprox -> WarningAmberContainer
                                else -> SafeGreenContainer
                            }
                            val badgeTextColor = when {
                                !isLocationGranted || isLocationUnavailable -> EmergencyRed
                                isLocationApprox -> WarningAmber
                                else -> SafeGreen
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badgeBgColor)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = badgeTextColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val subtitleText = when {
                            activeEmergency != null && isLocationUnavailable ->
                                "Emergency triggered without GPS coordinates"
                            activeEmergency != null && isLocationApprox ->
                                "Lat: %.4f, Long: %.4f • Approximate area".format(activeEmergency.latitude, activeEmergency.longitude)
                            activeEmergency != null ->
                                "Lat: %.4f, Long: %.4f • Accuracy ±%.1fm".format(activeEmergency.latitude, activeEmergency.longitude, activeEmergency.locationAccuracy)
                            isLocationGranted && isLocationPrecise ->
                                "High-accuracy GPS locked for instant SOS telemetry"
                            isLocationGranted ->
                                "Coarse location active • Tap to enable high precision"
                            else ->
                                "Tap here to grant location permission for SOS GPS"
                        }

                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Battery Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.surface
                ),
                elevation =
                    CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0F2FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.BatteryChargingFull,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = batteryPercentage?.let { percentage ->
                                "Battery Status: $percentage%"
                            } ?: "Battery Status: Reading...",
                            style =
                                MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color =
                                MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Live reading from this device",
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
