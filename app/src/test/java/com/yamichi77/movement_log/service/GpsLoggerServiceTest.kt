package com.yamichi77.movement_log.service

import android.app.PendingIntent
import android.app.Service
import android.os.Build
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import com.yamichi77.movement_log.data.settings.TrackingFrequencySettings
import com.yamichi77.movement_log.data.tracking.TrackingActivityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsLoggerServiceTest {
    @Test
    fun resolveTransitionPendingIntentFlags_onAndroid12AndAbove_includesMutableFlag() {
        val flags = resolveTransitionPendingIntentFlags(Build.VERSION_CODES.S)

        assertTrue(flags and PendingIntent.FLAG_UPDATE_CURRENT != 0)
        assertTrue(flags and PendingIntent.FLAG_MUTABLE != 0)
    }

    @Test
    fun resolveTransitionPendingIntentFlags_beforeAndroid12_excludesMutableFlag() {
        val flags = resolveTransitionPendingIntentFlags(Build.VERSION_CODES.R)

        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, flags)
    }

    @Test
    fun resolveNextActivityStatus_stillExit_returnsUnknown() {
        val nextStatus = resolveNextActivityStatus(
            activityType = DetectedActivity.STILL,
            transitionType = ActivityTransition.ACTIVITY_TRANSITION_EXIT,
            currentActivityStatus = TrackingActivityStatus.STILL,
        )

        assertEquals(TrackingActivityStatus.UNKNOWN, nextStatus)
    }

    @Test
    fun resolveNextActivityStatus_walkEnter_returnsWalking() {
        val nextStatus = resolveNextActivityStatus(
            activityType = DetectedActivity.WALKING,
            transitionType = ActivityTransition.ACTIVITY_TRANSITION_ENTER,
            currentActivityStatus = TrackingActivityStatus.STILL,
        )

        assertEquals(TrackingActivityStatus.WALKING, nextStatus)
    }

    @Test
    fun resolveActivityUpdateStatus_onFoot_returnsWalking() {
        val status = resolveActivityUpdateStatus(DetectedActivity.ON_FOOT)

        assertEquals(TrackingActivityStatus.WALKING, status)
    }

    @Test
    fun resolveActivityUpdateStatus_unknown_returnsUnknown() {
        val status = resolveActivityUpdateStatus(DetectedActivity.UNKNOWN)

        assertEquals(TrackingActivityStatus.UNKNOWN, status)
    }

    @Test
    fun resolveActivityUpdateStatus_tilting_returnsNull() {
        val status = resolveActivityUpdateStatus(DetectedActivity.TILTING)

        assertEquals(null, status)
    }

    @Test
    fun resolveGpsLoggerStartCommandResult_foregroundStarted_returnsSticky() {
        val result = resolveGpsLoggerStartCommandResult(startedForeground = true)

        assertEquals(Service.START_STICKY, result)
    }

    @Test
    fun resolveGpsLoggerStartCommandResult_foregroundRejected_returnsNotSticky() {
        val result = resolveGpsLoggerStartCommandResult(startedForeground = false)

        assertEquals(Service.START_NOT_STICKY, result)
    }

    @Test
    fun resolveLocationRequestParameters_unknownUsesWalkingCadence() {
        val parameters = resolveLocationRequestParameters(
            activityStatus = TrackingActivityStatus.UNKNOWN,
            currentFrequencySettings = TrackingFrequencySettings.Default,
            debugGpsMode = false,
        )

        assertEquals(TrackingFrequencySettings.Default.walkingSec, parameters.intervalSeconds)
        assertTrue(parameters.highAccuracy)
    }
}
