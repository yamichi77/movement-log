package com.yamichi77.movement_log.ui.permission

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.yamichi77.movement_log.permission.PermissionStatusItem
import com.yamichi77.movement_log.permission.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PermissionStateRepository {
    fun hasRequiredPermissions(context: Context): Boolean
    fun permissionItems(context: Context): List<PermissionStatusItem>
}

object DefaultPermissionStateRepository : PermissionStateRepository {
    override fun hasRequiredPermissions(context: Context): Boolean =
        PermissionUtils.hasRequiredPermissions(context)

    override fun permissionItems(context: Context): List<PermissionStatusItem> =
        PermissionUtils.permissionItems(context)
}

class PermissionViewModel(
    application: Application,
    private val permissionStateRepository: PermissionStateRepository =
        DefaultPermissionStateRepository,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        permissionStateRepository = DefaultPermissionStateRepository,
    )

    private val _uiState = MutableStateFlow(readPermissionState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    fun refreshPermissionState() {
        _uiState.value = readPermissionState()
    }

    private fun readPermissionState(): PermissionUiState {
        val appContext = getApplication<Application>()
        return PermissionUiState(
            hasRequiredPermissions = permissionStateRepository.hasRequiredPermissions(appContext),
            permissionItems = permissionStateRepository.permissionItems(appContext),
        )
    }
}
