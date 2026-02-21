package com.yamichi77.movement_log.data.network

import android.content.Context
import com.yamichi77.movement_log.data.auth.AuthHttpClientProvider

object MovementApiGatewayProvider {
    @Volatile
    private var instance: MovementApiGateway? = null

    fun get(context: Context): MovementApiGateway =
        instance ?: synchronized(this) {
            instance ?: HttpMovementApiGateway(
                client = AuthHttpClientProvider.get(context.applicationContext),
            ).also { instance = it }
        }

    fun setForTesting(gateway: MovementApiGateway?) {
        instance = gateway
    }
}
