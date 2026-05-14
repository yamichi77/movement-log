package com.yamichi77.movement_log.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yamichi77.movement_log.BuildConfig
import com.yamichi77.movement_log.MainActivity
import com.yamichi77.movement_log.R
import com.yamichi77.movement_log.data.local.MoveLogDao
import com.yamichi77.movement_log.data.local.MoveLogEntity
import com.yamichi77.movement_log.data.local.MovementLogDatabase
import com.yamichi77.movement_log.data.repository.TrackingFrequencySettingsRepositoryProvider
import com.yamichi77.movement_log.data.settings.TrackingFrequencySettings
import com.yamichi77.movement_log.data.tracking.TrackingActivityStatus
import com.yamichi77.movement_log.data.tracking.TrackingStateStore
import com.yamichi77.movement_log.permission.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal fun resolveTransitionPendingIntentFlags(sdkInt: Int): Int =
    PendingIntent.FLAG_UPDATE_CURRENT or if (sdkInt >= Build.VERSION_CODES.S) {
        // Activity Recognition writes transition results into the PendingIntent on Android 12+.
        PendingIntent.FLAG_MUTABLE
    } else {
        0
    }

internal const val DebugIntervalSeconds = 5

internal fun resolveTrackedActivityStatus(activityType: Int): String? =
    when (activityType) {
        DetectedActivity.WALKING -> TrackingActivityStatus.WALKING
        DetectedActivity.RUNNING -> TrackingActivityStatus.RUNNING
        DetectedActivity.ON_BICYCLE -> TrackingActivityStatus.BICYCLE
        DetectedActivity.IN_VEHICLE -> TrackingActivityStatus.VEHICLE
        DetectedActivity.STILL -> TrackingActivityStatus.STILL
        else -> null
    }

internal fun resolveActivityUpdateStatus(activityType: Int): String? =
    when (activityType) {
        DetectedActivity.WALKING -> TrackingActivityStatus.WALKING
        DetectedActivity.RUNNING -> TrackingActivityStatus.RUNNING
        DetectedActivity.ON_BICYCLE -> TrackingActivityStatus.BICYCLE
        DetectedActivity.IN_VEHICLE -> TrackingActivityStatus.VEHICLE
        DetectedActivity.STILL -> TrackingActivityStatus.STILL
        DetectedActivity.ON_FOOT -> TrackingActivityStatus.WALKING
        DetectedActivity.UNKNOWN -> TrackingActivityStatus.UNKNOWN
        else -> null
    }

internal fun resolveNextActivityStatus(
    activityType: Int,
    transitionType: Int,
    currentActivityStatus: String,
): String? {
    val mappedStatus = resolveTrackedActivityStatus(activityType) ?: return null
    return when (transitionType) {
        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> mappedStatus
        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
            if (mappedStatus == TrackingActivityStatus.STILL || currentActivityStatus == mappedStatus) {
                TrackingActivityStatus.UNKNOWN
            } else {
                currentActivityStatus
            }
        }
        else -> currentActivityStatus
    }
}

internal data class LocationRequestParameters(
    val intervalSeconds: Int,
    val highAccuracy: Boolean,
)

internal fun resolveLocationRequestParameters(
    activityStatus: String,
    currentFrequencySettings: TrackingFrequencySettings,
    debugGpsMode: Boolean,
): LocationRequestParameters {
    if (debugGpsMode) {
        return LocationRequestParameters(
            intervalSeconds = DebugIntervalSeconds,
            highAccuracy = true,
        )
    }
    return when (activityStatus) {
        TrackingActivityStatus.WALKING -> LocationRequestParameters(
            intervalSeconds = currentFrequencySettings.walkingSec,
            highAccuracy = true,
        )
        TrackingActivityStatus.RUNNING -> LocationRequestParameters(
            intervalSeconds = currentFrequencySettings.runningSec,
            highAccuracy = true,
        )
        TrackingActivityStatus.BICYCLE -> LocationRequestParameters(
            intervalSeconds = currentFrequencySettings.bicycleSec,
            highAccuracy = true,
        )
        TrackingActivityStatus.VEHICLE -> LocationRequestParameters(
            intervalSeconds = currentFrequencySettings.vehicleSec,
            highAccuracy = true,
        )
        TrackingActivityStatus.UNKNOWN -> LocationRequestParameters(
            intervalSeconds = currentFrequencySettings.walkingSec,
            highAccuracy = true,
        )
        TrackingActivityStatus.STILL -> LocationRequestParameters(
            intervalSeconds = currentFrequencySettings.stillSec,
            highAccuracy = false,
        )
        else -> LocationRequestParameters(
            intervalSeconds = currentFrequencySettings.stillSec,
            highAccuracy = false,
        )
    }
}

internal fun resolveGpsLoggerStartCommandResult(startedForeground: Boolean): Int =
    if (startedForeground) {
        Service.START_STICKY
    } else {
        Service.START_NOT_STICKY
    }

class GpsLoggerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var moveLogDao: MoveLogDao
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private val debugGpsMode: Boolean = BuildConfig.LOCAL_DEBUG_GPS
    private var currentFrequencySettings: TrackingFrequencySettings = TrackingFrequencySettings.Default
    private var hasStartedTracking: Boolean = false
    private var currentActivityStatus: String = if (debugGpsMode) {
        TrackingActivityStatus.UNKNOWN
    } else {
        TrackingActivityStatus.STILL
    }
    private var currentLocationRequest: LocationRequest = createLocationRequest(
        intervalSeconds = if (debugGpsMode) DebugIntervalSeconds else currentFrequencySettings.stillSec,
        highAccuracy = debugGpsMode,
    )

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val locations = locationResult.locations
            if (locations.isNullOrEmpty()) return

            locations.forEach { location ->
                val latitude = location.latitude
                val longitude = location.longitude
                if (latitude == lastLatitude && longitude == lastLongitude) return@forEach

                lastLatitude = latitude
                lastLongitude = longitude

                TrackingStateStore.updateLocation(
                    latitude = latitude,
                    longitude = longitude,
                    updatedAtEpochMillis = location.time,
                    activityStatus = currentActivityStatus,
                )

                val entity = MoveLogEntity(
                    recordedAtEpochMillis = location.time,
                    latitude = latitude,
                    longitude = longitude,
                    activityStatus = currentActivityStatus,
                    accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                    isUploaded = false,
                )
                serviceScope.launch { moveLogDao.insert(entity) }
            }
        }
    }

    private val activityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) {
                Log.w(LogTag, "activityReceiver: intent was null")
                return
            }
            when {
                ActivityTransitionResult.hasResult(intent) -> {
                    val result = ActivityTransitionResult.extractResult(intent) ?: return
                    result.transitionEvents.forEach { event ->
                        handleActivityTransition(event)
                    }
                }
                ActivityRecognitionResult.hasResult(intent) -> {
                    val result = ActivityRecognitionResult.extractResult(intent) ?: return
                    handleActivityUpdate(result)
                }
                else -> {
                    Log.w(LogTag, "activityReceiver: no activity result action=${intent.action}")
                }
            }
        }
    }

    private val transitionAction: String by lazy {
        "${applicationContext.packageName}.TRANSITION_ACTION_RECEIVER"
    }

    private val activityUpdateAction: String by lazy {
        "${applicationContext.packageName}.ACTIVITY_UPDATE_RECEIVER"
    }

    private val transitionPendingIntent: PendingIntent by lazy {
        val flags = resolveTransitionPendingIntentFlags(Build.VERSION.SDK_INT)
        PendingIntent.getBroadcast(
            this,
            0,
            Intent(transitionAction).setPackage(packageName),
            flags,
        )
    }

    private val activityUpdatePendingIntent: PendingIntent by lazy {
        val flags = resolveTransitionPendingIntentFlags(Build.VERSION.SDK_INT)
        PendingIntent.getBroadcast(
            this,
            1,
            Intent(activityUpdateAction).setPackage(packageName),
            flags,
        )
    }

    override fun onCreate() {
        super.onCreate()
        moveLogDao = MovementLogDatabase.getInstance(this).moveLogDao()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        registerReceiver(
            activityReceiver,
            IntentFilter().apply {
                addAction(transitionAction)
                addAction(activityUpdateAction)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )

        serviceScope.launch {
            TrackingFrequencySettingsRepositoryProvider.get(applicationContext).settings.collect { settings ->
                currentFrequencySettings = settings
                if (hasStartedTracking && !debugGpsMode) {
                    applyLocationRequestForActivity(currentActivityStatus)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!PermissionUtils.hasRequiredPermissions(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!startForegroundInternal()) {
            TrackingStateStore.setCollecting(false)
            stopSelf(startId)
            return resolveGpsLoggerStartCommandResult(startedForeground = false)
        }

        hasStartedTracking = true
        TrackingStateStore.setCollecting(true)
        TrackingStateStore.updateActivity(currentActivityStatus)
        val initialRequestParameters = resolveLocationRequestParameters(currentActivityStatus)
        currentLocationRequest = createLocationRequest(
            intervalSeconds = initialRequestParameters.intervalSeconds,
            highAccuracy = initialRequestParameters.highAccuracy,
        )
        startLocationUpdates()
        startActivityTransitionMonitoring()
        startActivityUpdateMonitoring()
        return resolveGpsLoggerStartCommandResult(startedForeground = true)
    }

    override fun onDestroy() {
        hasStartedTracking = false
        stopLocationUpdates()
        stopActivityTransitionMonitoring()
        stopActivityUpdateMonitoring()
        runCatching { unregisterReceiver(activityReceiver) }
        TrackingStateStore.setCollecting(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundInternal(): Boolean {
        createNotificationChannel()
        val openAppIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            true
        } catch (exception: ForegroundServiceStartNotAllowedException) {
            Log.w(LogTag, "startForegroundInternal: foreground service start not allowed", exception)
            false
        } catch (exception: SecurityException) {
            Log.w(LogTag, "startForegroundInternal: foreground service permission denied", exception)
            false
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun startActivityTransitionMonitoring() {
        if (debugGpsMode) return
        if (!PermissionUtils.hasActivityRecognitionPermission(this)) {
            Log.w(LogTag, "startActivityTransitionMonitoring: permission missing")
            return
        }

        val request = ActivityTransitionRequest(
            listOf(
                createEnterTransition(DetectedActivity.WALKING),
                createExitTransition(DetectedActivity.WALKING),
                createEnterTransition(DetectedActivity.RUNNING),
                createExitTransition(DetectedActivity.RUNNING),
                createEnterTransition(DetectedActivity.ON_BICYCLE),
                createExitTransition(DetectedActivity.ON_BICYCLE),
                createEnterTransition(DetectedActivity.IN_VEHICLE),
                createExitTransition(DetectedActivity.IN_VEHICLE),
                createEnterTransition(DetectedActivity.STILL),
                createExitTransition(DetectedActivity.STILL),
            ),
        )
        ActivityRecognition.getClient(this)
            .requestActivityTransitionUpdates(
                request,
                transitionPendingIntent,
            )
            .addOnSuccessListener {
                Log.d(LogTag, "startActivityTransitionMonitoring: registered")
            }
            .addOnFailureListener { error ->
                Log.e(LogTag, "startActivityTransitionMonitoring: failed", error)
            }
    }

    @SuppressLint("MissingPermission")
    private fun stopActivityTransitionMonitoring() {
        if (debugGpsMode) return
        if (!PermissionUtils.hasActivityRecognitionPermission(this)) return
        ActivityRecognition.getClient(this)
            .removeActivityTransitionUpdates(transitionPendingIntent)
            .addOnFailureListener { error ->
                Log.w(LogTag, "stopActivityTransitionMonitoring: failed", error)
            }
    }

    @SuppressLint("MissingPermission")
    private fun startActivityUpdateMonitoring() {
        if (debugGpsMode) return
        if (!PermissionUtils.hasActivityRecognitionPermission(this)) {
            Log.w(LogTag, "startActivityUpdateMonitoring: permission missing")
            return
        }

        ActivityRecognition.getClient(this)
            .requestActivityUpdates(
                ACTIVITY_UPDATE_INTERVAL_MILLIS,
                activityUpdatePendingIntent,
            )
            .addOnSuccessListener {
                Log.d(LogTag, "startActivityUpdateMonitoring: registered")
            }
            .addOnFailureListener { error ->
                Log.e(LogTag, "startActivityUpdateMonitoring: failed", error)
            }
    }

    @SuppressLint("MissingPermission")
    private fun stopActivityUpdateMonitoring() {
        if (debugGpsMode) return
        if (!PermissionUtils.hasActivityRecognitionPermission(this)) return
        ActivityRecognition.getClient(this)
            .removeActivityUpdates(activityUpdatePendingIntent)
            .addOnFailureListener { error ->
                Log.w(LogTag, "stopActivityUpdateMonitoring: failed", error)
            }
    }

    private fun createEnterTransition(activityType: Int): ActivityTransition =
        createTransition(activityType, ActivityTransition.ACTIVITY_TRANSITION_ENTER)

    private fun createExitTransition(activityType: Int): ActivityTransition =
        createTransition(activityType, ActivityTransition.ACTIVITY_TRANSITION_EXIT)

    private fun createTransition(activityType: Int, transitionType: Int): ActivityTransition =
        ActivityTransition.Builder()
            .setActivityType(activityType)
            .setActivityTransition(transitionType)
            .build()

    private fun handleActivityTransition(event: ActivityTransitionEvent) {
        if (debugGpsMode) return
        val nextStatus = resolveNextActivityStatus(
            activityType = event.activityType,
            transitionType = event.transitionType,
            currentActivityStatus = currentActivityStatus,
        ) ?: return
        Log.d(
            LogTag,
            "handleActivityTransition: type=${event.activityType} transition=${event.transitionType} nextStatus=$nextStatus",
        )
        updateActivityStatus(nextStatus)
    }

    private fun handleActivityUpdate(result: ActivityRecognitionResult) {
        if (debugGpsMode) return
        val activity = result.mostProbableActivity ?: return
        val nextStatus = resolveActivityUpdateStatus(activity.type) ?: return
        Log.d(
            LogTag,
            "handleActivityUpdate: type=${activity.type} confidence=${activity.confidence} nextStatus=$nextStatus",
        )
        updateActivityStatus(nextStatus)
    }

    private fun updateActivityStatus(nextStatus: String) {
        if (currentActivityStatus == nextStatus) return
        currentActivityStatus = nextStatus
        TrackingStateStore.updateActivity(nextStatus)
        applyLocationRequestForActivity(nextStatus)
    }

    private fun changeLocationRequest(intervalSeconds: Int, highAccuracy: Boolean) {
        stopLocationUpdates()
        currentLocationRequest = createLocationRequest(intervalSeconds, highAccuracy)
        startLocationUpdates()
    }

    private fun applyLocationRequestForActivity(activityStatus: String) {
        val requestParameters = resolveLocationRequestParameters(activityStatus)
        changeLocationRequest(
            intervalSeconds = requestParameters.intervalSeconds,
            highAccuracy = requestParameters.highAccuracy,
        )
    }

    private fun resolveLocationRequestParameters(activityStatus: String): LocationRequestParameters {
        return com.yamichi77.movement_log.service.resolveLocationRequestParameters(
            activityStatus = activityStatus,
            currentFrequencySettings = currentFrequencySettings,
            debugGpsMode = debugGpsMode,
        )
    }

    private fun createLocationRequest(intervalSeconds: Int, highAccuracy: Boolean): LocationRequest {
        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        return LocationRequest.Builder(priority, intervalSeconds * 1000L)
            .setMinUpdateIntervalMillis(5_000L)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!PermissionUtils.hasLocationPermissions(this)) return
        runCatching {
            fusedLocationClient.requestLocationUpdates(
                currentLocationRequest,
                locationCallback,
                mainLooper,
            )
        }.onFailure { error ->
            Log.e(LogTag, "startLocationUpdates: failed", error)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private companion object {
        private const val LogTag = "GpsLoggerService"
        private const val NOTIFICATION_CHANNEL_ID = "gps_logger_service_notification_channel"
        private const val NOTIFICATION_ID = 200
        private const val ACTIVITY_UPDATE_INTERVAL_MILLIS = 60_000L

    }
}
