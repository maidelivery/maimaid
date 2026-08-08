package net.krtl.maimaid.core.domain

data class AuthUser(
    val id: String,
    val email: String,
    val isAdmin: Boolean
)

sealed interface SessionState {
    data object Unknown : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: AuthUser) : SessionState
    data class Recovery(val token: String, val email: String?) : SessionState
}

