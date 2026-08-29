package com.oyesuno.core.permissions

sealed class PermissionState {
    object Granted : PermissionState()
    object Denied : PermissionState()
    object PermanentlyDenied : PermissionState()
    object NotRequested : PermissionState()
}
