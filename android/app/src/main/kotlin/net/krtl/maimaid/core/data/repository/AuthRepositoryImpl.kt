package net.krtl.maimaid.core.data.repository

import kotlinx.coroutines.flow.StateFlow
import net.krtl.maimaid.core.data.session.BackendSessionManager
import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.SessionState
import net.krtl.maimaid.core.domain.repository.AuthRepository
import net.krtl.maimaid.core.domain.repository.AuthWebMode

class AuthRepositoryImpl(
    private val sessionManager: BackendSessionManager
) : AuthRepository {
    override val sessionState: StateFlow<SessionState> = sessionManager.sessionState

    override suspend fun checkSession(): Result<SessionState, DomainError> = sessionManager.checkSession()

    override suspend fun exchangeSessionCode(sessionCode: String): Result<SessionState, DomainError> =
        sessionManager.exchangeSessionCode(sessionCode)

    override suspend fun logout(): Result<Unit, DomainError> = sessionManager.logout()

    override fun buildWebAuthUrl(mode: AuthWebMode): String? = sessionManager.buildWebAuthUrl(mode)

    override suspend fun handleRedirect(url: String): Result<SessionState, DomainError> =
        sessionManager.handleAuthRedirect(url)
}

