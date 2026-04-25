package com.yamichi77.movement_log.service

import android.app.PendingIntent
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
