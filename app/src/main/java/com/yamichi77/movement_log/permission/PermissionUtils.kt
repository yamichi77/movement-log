package com.yamichi77.movement_log.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.yamichi77.movement_log.BuildConfig
import com.yamichi77.movement_log.R

data class PermissionStatusItem(
    val labelResId: Int,
    val permission: String,
    val granted: Boolean,
)

object PermissionUtils {
    fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        if (!BuildConfig.LOCAL_DEBUG_GPS) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        return permissions.toTypedArray()
    }

    fun hasRequiredPermissions(context: Context): Boolean =
        requiredPermissions().all { hasPermission(context, it) }

    fun hasLocationPermissions(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) &&
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasActivityRecognitionPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)

    fun permissionItems(context: Context): List<PermissionStatusItem> {
        val items = mutableListOf(
            PermissionStatusItem(
                labelResId = R.string.permission_fine_location,
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                granted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION),
            ),
            PermissionStatusItem(
                labelResId = R.string.permission_coarse_location,
                permission = Manifest.permission.ACCESS_COARSE_LOCATION,
                granted = hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            ),
            PermissionStatusItem(
                labelResId = R.string.permission_notifications,
                permission = Manifest.permission.POST_NOTIFICATIONS,
                granted = hasPermission(context, Manifest.permission.POST_NOTIFICATIONS),
            ),
        )
        if (!BuildConfig.LOCAL_DEBUG_GPS) {
            items.add(
                PermissionStatusItem(
                    labelResId = R.string.permission_activity_recognition,
                    permission = Manifest.permission.ACTIVITY_RECOGNITION,
                    granted = hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION),
                ),
            )
        }
        return items
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
