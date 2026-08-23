package com.safetap.app.domain.sos.services

interface PermissionChecker {
    fun hasLocationPermission(): Boolean
    fun hasFineLocationPermission(): Boolean
    fun hasCoarseLocationPermission(): Boolean
    fun hasNotificationPermission(): Boolean
    fun hasSmsPermission(): Boolean
    fun hasCallPhonePermission(): Boolean
    fun hasRequiredPermissions(): Boolean
}
