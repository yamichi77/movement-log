package com.yamichi77.movement_log.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthNavigationEvent {
    data class RequireLogin(
        val reason: AuthErrorCode,
        val baseUrl: String? = null,
    ) : AuthNavigationEvent
}

object AuthNavigationEventBus {
    private val _event = MutableStateFlow<AuthNavigationEvent?>(null)
    val event: StateFlow<AuthNavigationEvent?> = _event.asStateFlow()

    fun requireLogin(reason: AuthErrorCode, baseUrl: String? = null) {
        // Force re-emission even when the same reason/baseUrl is requested repeatedly.
        _event.value = null
        _event.value = AuthNavigationEvent.RequireLogin(reason, baseUrl)
    }

    fun clear() {
        _event.value = null
    }
}
