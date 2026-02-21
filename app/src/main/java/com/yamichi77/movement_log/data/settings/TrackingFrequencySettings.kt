package com.yamichi77.movement_log.data.settings

data class TrackingFrequencySettings(
    val walkingSec: Int,
    val runningSec: Int,
    val bicycleSec: Int,
    val vehicleSec: Int,
    val stillSec: Int,
) {
    companion object {
        val Default = TrackingFrequencySettings(
            walkingSec = 30,
            runningSec = 25,
            bicycleSec = 20,
            vehicleSec = 15,
            stillSec = 15 * 60,
        )
    }
}
