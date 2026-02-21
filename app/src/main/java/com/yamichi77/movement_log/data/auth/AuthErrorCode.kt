package com.yamichi77.movement_log.data.auth

enum class AuthErrorCode {
    SESSION_INVALID,
    SESSION_STEP_UP_REQUIRED,
    SESSION_COMPROMISED_REAUTH_REQUIRED,
    SESSION_EXPIRED,
    REFRESH_TEMPORARY_FAILURE,
    UNKNOWN,
}
