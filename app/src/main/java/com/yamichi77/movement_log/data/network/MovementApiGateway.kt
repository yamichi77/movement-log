package com.yamichi77.movement_log.data.network

data class MovementLogUploadRequest(
    val seqTime: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val activity: String,
)

interface MovementApiGateway {
    suspend fun verifyToken(
        baseUrl: String,
        token: String,
    )

    suspend fun uploadMovementLog(
        baseUrl: String,
        uploadPath: String,
        token: String,
        request: MovementLogUploadRequest,
    )
}

class MovementApiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
