package net.krtl.maimaid.core.data.session

import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import net.krtl.maimaid.core.data.remote.BackendAuthPayloadDto
import net.krtl.maimaid.core.data.remote.BackendConfig
import net.krtl.maimaid.core.data.remote.BackendErrorDto
import net.krtl.maimaid.core.data.remote.BackendHttpClient
import net.krtl.maimaid.core.data.remote.BackendLogoutResponseDto
import net.krtl.maimaid.core.data.remote.BackendMeDto
import net.krtl.maimaid.core.data.remote.BackendRefreshRequestDto
import net.krtl.maimaid.core.data.remote.BackendSessionExchangeRequestDto
import net.krtl.maimaid.core.domain.AuthUser
import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.SessionState
import net.krtl.maimaid.core.domain.repository.AuthWebMode

class BackendSessionManager(
    private val httpClient: BackendHttpClient,
    private val secureSessionStore: SecureSessionStore,
    private val json: Json
) {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        val cached = secureSessionStore.loadCachedBundle()
        _sessionState.value = if (cached != null) {
            SessionState.LoggedIn(cached.toAuthUser())
        } else {
            SessionState.LoggedOut
        }
    }

    fun accessTokenForRequest(): String? = secureSessionStore.loadCachedBundle()?.accessToken

    fun isConfigured(): Boolean = BackendConfig.baseUrl != null

    fun isAuthenticated(): Boolean = sessionState.value is SessionState.LoggedIn

    suspend fun exchangeSessionCode(sessionCode: String): Result<SessionState, DomainError> {
        if (!isConfigured()) {
            return Result.Err(DomainError.Validation("Backend URL is not configured"))
        }
        val result = runCatching {
            val body = httpClient.encodeBody(
                BackendSessionExchangeRequestDto(sessionCode = sessionCode)
            )
            httpClient.execute(
                path = "v1/auth/session:exchange",
                method = "POST",
                bodyJson = body
            )
        }.getOrElse { throwable ->
            return Result.Err(DomainError.Network(throwable.message ?: "Network failure"))
        }

        if (result.statusCode !in 200..299) {
            return Result.Err(parseDomainError(result.statusCode, result.body))
        }
        val payload = runCatching { httpClient.decodeBody<BackendAuthPayloadDto>(result.body) }
            .getOrElse { return Result.Err(DomainError.Unknown("Invalid auth payload")) }
        applyAuthPayload(payload)
        return Result.Ok(sessionState.value)
    }

    suspend fun checkSession(): Result<SessionState, DomainError> {
        if (!isConfigured()) {
            return Result.Err(DomainError.Validation("Backend URL is not configured"))
        }
        val bundle = secureSessionStore.loadBundle() ?: run {
            _sessionState.value = SessionState.LoggedOut
            return Result.Ok(SessionState.LoggedOut)
        }

        val first = runCatching {
            httpClient.execute(
                path = "v1/auth/me",
                method = "GET",
                bearerToken = bundle.accessToken
            )
        }.getOrElse { throwable ->
            return Result.Err(DomainError.Network(throwable.message ?: "Network failure"))
        }

        if (first.statusCode == 401) {
            val refreshed = refreshSessionSilently()
            if (!refreshed) {
                secureSessionStore.clear()
                _sessionState.value = SessionState.LoggedOut
                return Result.Err(DomainError.Unauthorized())
            }
            return checkSession()
        }

        if (first.statusCode !in 200..299) {
            return Result.Err(parseDomainError(first.statusCode, first.body))
        }

        val me = runCatching { httpClient.decodeBody<BackendMeDto>(first.body) }
            .getOrElse { return Result.Err(DomainError.Unknown("Invalid profile payload")) }

        val latest = secureSessionStore.loadBundle()
        if (latest != null) {
            secureSessionStore.saveBundle(
                latest.copy(
                    userId = me.id,
                    email = me.email,
                    isAdmin = me.isAdmin
                )
            )
        }
        val state = SessionState.LoggedIn(
            AuthUser(
                id = me.id,
                email = me.email,
                isAdmin = me.isAdmin
            )
        )
        _sessionState.value = state
        return Result.Ok(state)
    }

    suspend fun refreshSessionSilently(): Boolean {
        if (!isConfigured()) return false
        val bundle = secureSessionStore.loadBundle() ?: return false
        val response = runCatching {
            httpClient.execute(
                path = "v1/auth/refresh",
                method = "POST",
                bodyJson = httpClient.encodeBody(BackendRefreshRequestDto(bundle.refreshToken))
            )
        }.getOrElse {
            return false
        }
        if (response.statusCode !in 200..299) {
            return false
        }
        val payload = runCatching { httpClient.decodeBody<BackendAuthPayloadDto>(response.body) }.getOrNull() ?: return false
        applyAuthPayload(payload)
        return true
    }

    suspend fun logout(): Result<Unit, DomainError> {
        val bundle = secureSessionStore.loadBundle()
        if (bundle != null && isConfigured()) {
            runCatching {
                httpClient.execute(
                    path = "v1/auth/logout",
                    method = "POST",
                    bodyJson = httpClient.encodeBody(BackendRefreshRequestDto(bundle.refreshToken))
                )
            }.onSuccess { result ->
                if (result.statusCode in 200..299) {
                    runCatching { httpClient.decodeBody<BackendLogoutResponseDto>(result.body) }
                }
            }
        }
        secureSessionStore.clear()
        _sessionState.value = SessionState.LoggedOut
        return Result.Ok(Unit)
    }

    suspend fun handleAuthRedirect(url: String): Result<SessionState, DomainError> {
        val sessionCode = valueFromUrl(url, "sessionCode")
        if (!sessionCode.isNullOrBlank() && sessionCode.length >= 20) {
            return exchangeSessionCode(sessionCode)
        }
        val resetToken = valueFromUrl(url, "token")
        val action = valueFromUrl(url, "authAction")
        if (action == "reset-password" && !resetToken.isNullOrBlank()) {
            val recovery = SessionState.Recovery(
                token = resetToken,
                email = valueFromUrl(url, "email")
            )
            _sessionState.value = recovery
            return Result.Ok(recovery)
        }
        return Result.Err(DomainError.Validation("Unsupported auth callback"))
    }

    fun buildWebAuthUrl(mode: AuthWebMode): String? {
        val base = BackendConfig.webAuthBaseUrl ?: return null
        val authMode = when (mode) {
            AuthWebMode.LOGIN -> "login"
            AuthWebMode.REGISTER -> "register"
            AuthWebMode.FORGOT_PASSWORD -> "forgot"
        }
        return base.newBuilder()
            .setQueryParameter("authMode", authMode)
            .setQueryParameter("redirect_uri", "maimaid://auth/callback")
            .setQueryParameter("client", "app")
            .build()
            .toString()
    }

    private suspend fun applyAuthPayload(payload: BackendAuthPayloadDto) {
        secureSessionStore.saveBundle(
            TokenBundle(
                userId = payload.user.id,
                email = payload.user.email,
                isAdmin = payload.user.isAdmin,
                accessToken = payload.accessToken,
                refreshToken = payload.refreshToken
            )
        )
        _sessionState.value = SessionState.LoggedIn(
            AuthUser(
                id = payload.user.id,
                email = payload.user.email,
                isAdmin = payload.user.isAdmin
            )
        )
    }

    private fun valueFromUrl(url: String, key: String): String? {
        val query = runCatching { url.toUri().getQueryParameter(key) }.getOrNull()
        if (!query.isNullOrBlank()) return query
        return null
    }

    private fun parseDomainError(statusCode: Int, body: String): DomainError {
        val fallback = "HTTP $statusCode"
        val payload = runCatching {
            json.decodeFromString(BackendErrorDto.serializer(), body)
        }.getOrNull()
        val message = payload?.message?.takeIf { it.isNotBlank() } ?: fallback
        return when (statusCode) {
            400 -> DomainError.Validation(message)
            401 -> DomainError.Unauthorized(message)
            409 -> DomainError.Conflict(message)
            in 500..599 -> DomainError.Server(statusCode, message)
            else -> DomainError.Unknown(message)
        }
    }
}
