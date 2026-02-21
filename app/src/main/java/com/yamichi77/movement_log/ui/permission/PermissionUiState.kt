package com.yamichi77.movement_log.ui.permission

import com.yamichi77.movement_log.permission.PermissionStatusItem

data class PermissionUiState(
    val hasRequiredPermissions: Boolean = false,
    val permissionItems: List<PermissionStatusItem> = emptyList(),
)
