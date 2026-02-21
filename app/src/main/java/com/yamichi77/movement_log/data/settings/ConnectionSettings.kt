package com.yamichi77.movement_log.data.settings

data class ConnectionSettings(
    val baseUrl: String,
    val uploadPath: String,
) {
    companion object {
        val Default = ConnectionSettings(
            baseUrl = "https://portal.yamichi.com",
            uploadPath = "/api/movelog",
        )
    }
}
