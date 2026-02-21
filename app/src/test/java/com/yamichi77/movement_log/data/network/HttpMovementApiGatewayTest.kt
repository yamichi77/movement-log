package com.yamichi77.movement_log.data.network

import com.yamichi77.movement_log.data.auth.UnauthorizedApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HttpMovementApiGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: HttpMovementApiGateway

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = HttpMovementApiGateway()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test(expected = UnauthorizedApiException::class)
    fun verifyToken_throwsWhenUnauthorized() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        gateway.verifyToken(
            baseUrl = server.url("/").toString(),
            token = "expired-token",
        )
    }

    @Test
    fun uploadMovementLog_postsLegacyPayloadToConfiguredPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        gateway.uploadMovementLog(
            baseUrl = server.url("/").toString(),
            uploadPath = "/api/movelog",
            token = "valid-token",
            request = MovementLogUploadRequest(
                seqTime = "20260216235959",
                latitude = 35.0,
                longitude = 139.0,
                accuracy = 4.5,
                activity = "WALKING",
            ),
        )

        val request = server.takeRequest()
        assertEquals("/api/movelog", request.path)
        assertEquals("Bearer valid-token", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"SeqTime\":\"20260216235959\""))
        assertTrue(body.contains("\"Latitude\":35.0"))
        assertTrue(body.contains("\"Longitude\":139.0"))
        assertTrue(body.contains("\"Accuracy\":4.5"))
        assertTrue(body.contains("\"Activity\":\"WALKING\""))
    }

    @Test(expected = UnauthorizedApiException::class)
    fun uploadMovementLog_throwsWhenUnauthorized() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        gateway.uploadMovementLog(
            baseUrl = server.url("/").toString(),
            uploadPath = "/api/movelog",
            token = "invalid-token",
            request = MovementLogUploadRequest(
                seqTime = "20260216235959",
                latitude = 35.0,
                longitude = 139.0,
                accuracy = 4.5,
                activity = "WALKING",
            ),
        )
    }
}
