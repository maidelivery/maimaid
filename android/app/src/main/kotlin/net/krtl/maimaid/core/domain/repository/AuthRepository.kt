package net.krtl.maimaid.core.domain.repository

import kotlinx.coroutines.flow.StateFlow
import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.SessionState

enum class AuthWebMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}

interface AuthRepository {
    val sessionState: StateFlow<SessionState>
    suspend fun checkSession(): Result<SessionState, DomainError>
    suspend fun exchangeSessionCode(sessionCode: String): Result<SessionState, DomainError>
    suspend fun logout(): Result<Unit, DomainError>
    fun buildWebAuthUrl(mode: AuthWebMode): String?
    suspend fun handleRedirect(url: String): Result<SessionState, DomainError>
}

