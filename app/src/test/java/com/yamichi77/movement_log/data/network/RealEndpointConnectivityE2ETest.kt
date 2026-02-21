package com.yamichi77.movement_log.data.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealEndpointConnectivityE2ETest {
    @Test
    fun connectivity_realEndpoint_canVerifyToken() = runTest {
        val baseUrl = System.getenv("SERVER_URL").orEmpty()
        val accessToken = System.getenv("SERVER_ACCESS_TOKEN").orEmpty()
        assumeTrue(baseUrl.isNotBlank() && accessToken.isNotBlank())

        val gateway = HttpMovementApiGateway()
        gateway.verifyToken(
            baseUrl = baseUrl,
            token = accessToken,
        )
    }
}
